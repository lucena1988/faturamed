package br.com.faturamed.conferencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.faturamed.domain.OrigemRegistro;
import br.com.faturamed.domain.TipoDivergencia;
import br.com.faturamed.importacao.RegistroConferencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotorConferenciaTest {

    private final MotorConferencia motor = new MotorConferencia();

    @Test
    void deveMarcarRegistroConformeQuandoProducaoEFaturamentoBatem() {
        RegistroConferencia producao = registro(1L, OrigemRegistro.PRODUCAO, "2", "150.00");
        RegistroConferencia faturamento = registro(2L, OrigemRegistro.FATURAMENTO, "2", "150.00");

        List<ResultadoConferencia> resultados = motor.conferir(List.of(producao), List.of(faturamento));

        assertThat(resultados)
                .extracting(ResultadoConferencia::tipo)
                .containsExactly(TipoDivergencia.CONFORME);
    }

    @Test
    void deveApontarFaturadoSemProducao() {
        RegistroConferencia faturamento = registro(2L, OrigemRegistro.FATURAMENTO, "1", "80.00");

        List<ResultadoConferencia> resultados = motor.conferir(List.of(), List.of(faturamento));

        assertThat(resultados)
                .extracting(ResultadoConferencia::tipo)
                .containsExactly(TipoDivergencia.FATURADO_SEM_PRODUCAO);
        assertThat(resultados.getFirst().valorEmRisco()).isEqualByComparingTo("80.00");
    }

    @Test
    void deveApontarQuantidadeDivergente() {
        RegistroConferencia producao = registro(1L, OrigemRegistro.PRODUCAO, "2", "150.00");
        RegistroConferencia faturamento = registro(2L, OrigemRegistro.FATURAMENTO, "3", "150.00");

        List<ResultadoConferencia> resultados = motor.conferir(List.of(producao), List.of(faturamento));

        assertThat(resultados)
                .extracting(ResultadoConferencia::tipo)
                .containsExactly(TipoDivergencia.QUANTIDADE_DIVERGENTE);
    }

    @Test
    void deveApontarValorDivergente() {
        RegistroConferencia producao = registro(1L, OrigemRegistro.PRODUCAO, "2", "150.00");
        RegistroConferencia faturamento = registro(2L, OrigemRegistro.FATURAMENTO, "2", "180.00");

        List<ResultadoConferencia> resultados = motor.conferir(List.of(producao), List.of(faturamento));

        assertThat(resultados)
                .extracting(ResultadoConferencia::tipo)
                .containsExactly(TipoDivergencia.VALOR_DIVERGENTE);
        assertThat(resultados.getFirst().valorEmRisco()).isEqualByComparingTo("30.00");
    }

    private static RegistroConferencia registro(
            Long id,
            OrigemRegistro origem,
            String quantidade,
            String valorTotal
    ) {
        return new RegistroConferencia(
                id,
                origem,
                "12345678900",
                "Paciente Teste",
                "ATD-1",
                LocalDate.of(2026, 9, 1),
                "Dra. Teste",
                "PROC-1",
                "Procedimento Teste",
                new BigDecimal(quantidade),
                new BigDecimal(valorTotal)
        );
    }
}
