package br.com.faturamed.conferencia;

import br.com.faturamed.domain.TipoDivergencia;
import java.math.BigDecimal;

public record ResultadoConferencia(
        TipoDivergencia tipo,
        RegistroConferencia producao,
        RegistroConferencia faturamento,
        BigDecimal valorEmRisco,
        String descricao
) {
}
