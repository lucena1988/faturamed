package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AssistenteBuscaTest {
    @Test
    void entendeMesDaPergunta() {
        YearMonth atual = YearMonth.now();
        assertEquals(atual, AssistenteBusca.mes("quanto faturou"));
        assertEquals(atual.minusMonths(1), AssistenteBusca.mes("quanto o morumbi faturou mes passado"));
        assertEquals(YearMonth.of(2025, 3), AssistenteBusca.mes("total de marco 2025"));
        YearMonth outubro = AssistenteBusca.mes("quanto o morumbi faturou em outubro");
        assertEquals(10, outubro.getMonthValue());
        assertFalse(outubro.isAfter(atual), "mes sem ano nunca fica no futuro");
    }

    @Test
    void normalizaAcentosEPontuacao() {
        assertEquals("quanto o sao luiz faturou em marco", AssistenteBusca.normalizar("Quanto o São Luiz faturou em março?"));
    }

    @Test
    void textoGigantePassaRapidoPelasExpressoes() {
        String longo = "lote ".repeat(50_000) + "x";
        long inicio = System.nanoTime();
        assertNotNull(AssistenteBusca.mes(longo));
        assertTrue((System.nanoTime() - inicio) / 1_000_000 < 2000, "sem retrocesso excessivo");
    }
}
