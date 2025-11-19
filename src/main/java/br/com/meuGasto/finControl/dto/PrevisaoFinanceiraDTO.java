package br.com.meuGasto.finControl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrevisaoFinanceiraDTO {
    // Valores realizados até o dia atual
    private BigDecimal receitaRealizada;
    private BigDecimal despesaRealizada;
    private BigDecimal saldoRealizado;

    // Valores previstos até o fim do mês
    private BigDecimal receitaPrevista;
    private BigDecimal despesaPrevista;
    private BigDecimal saldoPrevisto;

    // Evolução diária (lista de pontos)
    private List<EvolucaoDiariaDTO> evolucaoDiaria;

    private String periodo;
}
