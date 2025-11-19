package br.com.meuGasto.finControl.service;

import br.com.meuGasto.finControl.dto.*;
import br.com.meuGasto.finControl.entity.Gasto;
import br.com.meuGasto.finControl.repository.GastoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class MovimentacaoService {

    private final GastoRepository gastoRepository;
    private final ExportacaoService exportacaoService;

    /**
     * Listar movimentações com paginação
     */
    @Transactional(readOnly = true)
    public MovimentacaoResponseDTO listarMovimentacoes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dataGasto").descending());
        Page<Gasto> gastos = gastoRepository.findAll(pageable);

        List<MovimentacaoDTO> content = gastos.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return MovimentacaoResponseDTO.builder()
                .content(content)
                .pageNumber(gastos.getNumber())
                .pageSize(gastos.getSize())
                .totalElements(gastos.getTotalElements())
                .totalPages(gastos.getTotalPages())
                .last(gastos.isLast())
                .build();
    }

    /**
     * Buscar uma movimentação por ID
     */
    @Transactional(readOnly = true)
    public MovimentacaoDTO buscarMovimentacao(Long id) {
        return gastoRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Movimentação não encontrada com ID: " + id));
    }

    /**
     * Criar uma nova movimentação
     */
    public MovimentacaoDTO criarMovimentacao(MovimentacaoDTO dto) {
        Gasto gasto = new Gasto();
        gasto.setDescricao(dto.getDescricao());
        gasto.setValor(dto.getValor());
        gasto.setCategoria(dto.getCategoria());
        gasto.setTipo(dto.getTipo());
        // dto.data is LocalDate (date-only). Convert to LocalDateTime at start of day.
        if (dto.getData() != null) {
            gasto.setDataGasto(dto.getData().atStartOfDay());
        } else {
            gasto.setDataGasto(LocalDateTime.now());
        }
        gasto.setObservacoes(dto.getObservacoes());
        gasto.setDataCriacao(LocalDateTime.now());
        gasto.setDataAtualizacao(LocalDateTime.now());

        Gasto saved = gastoRepository.save(gasto);
        return convertToDTO(saved);
    }

    /**
     * Atualizar uma movimentação existente
     */
    public MovimentacaoDTO atualizarMovimentacao(Long id, MovimentacaoDTO dto) {
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movimentação não encontrada com ID: " + id));

        gasto.setDescricao(dto.getDescricao());
        gasto.setValor(dto.getValor());
        gasto.setCategoria(dto.getCategoria());
        gasto.setTipo(dto.getTipo());
        // Only update dataGasto if dto.data is provided (convert LocalDate to LocalDateTime)
        if (dto.getData() != null) {
            gasto.setDataGasto(dto.getData().atStartOfDay());
        }
        gasto.setObservacoes(dto.getObservacoes());
        gasto.setDataAtualizacao(LocalDateTime.now());

        Gasto updated = gastoRepository.save(gasto);
        return convertToDTO(updated);
    }

    /**
     * Excluir uma movimentação
     */
    public void excluirMovimentacao(Long id) {
        if (!gastoRepository.existsById(id)) {
            throw new RuntimeException("Movimentação não encontrada com ID: " + id);
        }
        gastoRepository.deleteById(id);
    }

    /**
     * Obter resumo financeiro (últimos N dias)
     */
    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO obterResumoFinanceiro(int dias) {
        LocalDateTime inicio = LocalDateTime.now().minusDays(dias);
        LocalDateTime fim = LocalDateTime.now();

        List<Gasto> gastos = gastoRepository.findByDataGastoBetween(inicio, fim);

        BigDecimal totalDespesas = gastos.stream()
                .filter(gasto -> gasto.getTipo().equalsIgnoreCase("DESPESA"))
                .map(Gasto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Por enquanto, assumindo que todas as movimentações são despesas
        // Você pode ajustar isso se tiver um campo de tipo (receita/despesa)
        //BigDecimal totalReceitas = BigDecimal.ZERO;
        BigDecimal totalReceitas = gastos.stream()
                .filter(gasto -> gasto.getTipo().equalsIgnoreCase("RECEITA"))
                .map(Gasto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoAtual = totalReceitas.subtract(totalDespesas);

        return ResumoFinanceiroDTO.builder()
                .totalReceitas(totalReceitas)
                .totalDespesas(totalDespesas)
                .saldoAtual(saldoAtual)
                .build();
    }

    /**
     * Obter evolução financeira (últimos N dias)
     */
    @Transactional(readOnly = true)
    public List<EvolucaoFinanceiraDTO> obterEvolucaoFinanceira(int dias) {
        LocalDateTime inicio = LocalDateTime.now().minusDays(dias);
        LocalDateTime fim = LocalDateTime.now();

        List<Gasto> gastos = gastoRepository.findByDataGastoBetween(inicio, fim);

        // Agrupar por data
        Map<LocalDate, List<Gasto>> gastosPorData = gastos.stream()
                .collect(Collectors.groupingBy(g -> g.getDataGasto().toLocalDate()));

        List<EvolucaoFinanceiraDTO> evolucao = new ArrayList<>();
        BigDecimal saldoAcumulado = BigDecimal.ZERO;

        // Preencher todas as datas do intervalo
        for (int i = dias; i >= 0; i--) {
            LocalDate data = LocalDate.now().minusDays(i);
            List<Gasto> gastosDoDia = gastosPorData.getOrDefault(data, Collections.emptyList());

            BigDecimal despesasDia = gastosDoDia.stream()
                    .map(Gasto::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal receitasDia = BigDecimal.ZERO; // Ajustar se tiver receitas

            saldoAcumulado = saldoAcumulado.add(receitasDia).subtract(despesasDia);

            evolucao.add(EvolucaoFinanceiraDTO.builder()
                    .data(data.toString())
                    .receitas(receitasDia)
                    .despesas(despesasDia)
                    .saldo(saldoAcumulado)
                    .build());
        }

        return evolucao;
    }

    /**
     * Obter despesas por categoria (últimos N dias)
     */
    @Transactional(readOnly = true)
    public List<DespesaCategoriaDTO> obterDespesasPorCategoria(int dias) {
        LocalDateTime inicio = LocalDateTime.now().minusDays(dias);
        LocalDateTime fim = LocalDateTime.now();

        List<Gasto> gastos = gastoRepository.findByDataGastoBetween(inicio, fim);

        Map<String, BigDecimal> totalPorCategoria = gastos.stream()
                .collect(Collectors.groupingBy(
                        Gasto::getCategoria,
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getValor, BigDecimal::add)
                ));

        BigDecimal totalGeral = totalPorCategoria.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalTotal = totalGeral.compareTo(BigDecimal.ZERO) > 0 ? totalGeral : BigDecimal.ONE;

        return totalPorCategoria.entrySet().stream()
                .map(entry -> {
                    BigDecimal percentual = entry.getValue()
                            .divide(finalTotal, 2, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));

                    return DespesaCategoriaDTO.builder()
                            .categoria(entry.getKey())
                            .total(entry.getValue())
                            .percentual(percentual)
                            .build();
                })
                .sorted(Comparator.comparing(DespesaCategoriaDTO::getTotal).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Obter previsão financeira
     */
    @Transactional(readOnly = true)
    public PrevisaoFinanceiraDTO obterPrevisaoFinanceira() {
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioDoMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimDoMes = mesAtual.atEndOfMonth().atTime(23, 59, 59);

        // Buscar gastos e receitas
        List<Gasto> gastosDoMes = gastoRepository.findByDataGastoBetween(inicioDoMes, fimDoMes);
        List<Gasto> receitasDoMes = gastoRepository.findByDataGastoBetween(inicioDoMes, fimDoMes);

        BigDecimal despesaRealizada = gastosDoMes.stream()
                .map(Gasto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receitaRealizada = receitasDoMes.stream()
                .map(Gasto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoRealizado = receitaRealizada.subtract(despesaRealizada);

        int diaAtual = LocalDate.now().getDayOfMonth();
        int diasNoMes = mesAtual.atEndOfMonth().getDayOfMonth();

        BigDecimal despesaPrevista = despesaRealizada.multiply(new BigDecimal(diasNoMes))
                .divide(new BigDecimal(diaAtual), 2, RoundingMode.HALF_UP);

        BigDecimal receitaPrevista = receitaRealizada.multiply(new BigDecimal(diasNoMes))
                .divide(new BigDecimal(diaAtual), 2, RoundingMode.HALF_UP);

        BigDecimal saldoPrevisto = receitaPrevista.subtract(despesaPrevista);

        // Construir evolução diária
        List<EvolucaoDiariaDTO> evolucaoDiaria = IntStream.rangeClosed(1, diaAtual)
                .mapToObj(dia -> EvolucaoDiariaDTO.builder()
                        .dia(dia)
                        .receita(receitasDoMes.stream()
                                .filter(r -> r.getDataGasto().getDayOfMonth() == dia)
                                .map(Gasto::getValor)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .despesa(gastosDoMes.stream()
                                .filter(g -> g.getDataGasto().getDayOfMonth() == dia)
                                .map(Gasto::getValor)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .collect(Collectors.toList());

        return PrevisaoFinanceiraDTO.builder()
                .receitaRealizada(receitaRealizada)
                .despesaRealizada(despesaRealizada)
                .saldoRealizado(saldoRealizado)
                .receitaPrevista(receitaPrevista)
                .despesaPrevista(despesaPrevista)
                .saldoPrevisto(saldoPrevisto)
                .evolucaoDiaria(evolucaoDiaria)
                .periodo(mesAtual.toString())
                .build();
    }



    /**
     * Exportar movimentações em CSV
     */
    @Transactional(readOnly = true)
    public byte[] exportarMovimentacoes() {
        LocalDateTime inicio = LocalDateTime.now().minusMonths(12);
        LocalDateTime fim = LocalDateTime.now();

        String csv = exportacaoService.exportarGastosCSV(inicio, fim);
        return csv.getBytes();
    }

    /**
     * Converter Gasto para MovimentacaoDTO
     */
    private MovimentacaoDTO convertToDTO(Gasto gasto) {
        return MovimentacaoDTO.builder()
                .id(gasto.getId())
                .descricao(gasto.getDescricao())
                .valor(gasto.getValor())
                .categoria(gasto.getCategoria())
                .tipo(gasto.getTipo())
                // Convert stored LocalDateTime to LocalDate for the DTO (may be null)
                .data(gasto.getDataGasto() != null ? gasto.getDataGasto().toLocalDate() : null)
                .observacoes(gasto.getObservacoes())
                .dataCriacao(gasto.getDataCriacao())
                .dataAtualizacao(gasto.getDataAtualizacao())
                .build();
    }

}
