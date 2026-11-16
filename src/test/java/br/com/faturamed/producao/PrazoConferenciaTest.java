package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PrazoConferenciaTest {
    @Test
    void somaDiasUteisPulandoFimDeSemana() {
        LocalDate sexta = LocalDate.of(2026, 11, 6);
        assertEquals(LocalDate.of(2026, 11, 13), PrazoConferencia.somarDiasUteis(sexta, 5));
        assertEquals(LocalDate.of(2026, 11, 9), PrazoConferencia.somarDiasUteis(sexta, 1));
    }

    @Test
    void contaDiasUteisAteOPrazo() {
        LocalDate quinta = LocalDate.of(2026, 11, 12);
        assertEquals(1, PrazoConferencia.diasUteisAte(quinta, LocalDate.of(2026, 11, 13)));
        assertEquals(0, PrazoConferencia.diasUteisAte(quinta, quinta));
        assertEquals(1, PrazoConferencia.diasUteisAte(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9)), "sexta -> segunda = 1 dia util");
        assertEquals(-2, PrazoConferencia.diasUteisAte(quinta, LocalDate.of(2026, 11, 10)));
    }
}
