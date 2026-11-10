package br.com.faturamed.inicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.RevisaoVisitasService;
import br.com.faturamed.seguranca.SegurancaConfig;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InicioControllerTest {
    @Test void resumoSemRelatorioTrazContadoresDoBanco() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var revisoes = mock(RevisaoVisitasService.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        var r = new InicioController(jdbc, revisoes).resumo();
        assertEquals(0L, r.get("visitasAtencao"));
        assertEquals(3L, r.get("chamadosAbertos"));
        assertEquals(3L, r.get("lotesPendentes"));
        assertEquals(3L, r.get("solicitacoesPendentes"));
        verifyNoInteractions(revisoes);
    }
    @Test void equipeInternaVaiParaTelaDeInicio() {
        for (var perfil : List.of("ADMIN", "OPERADOR", "DEV")) assertEquals("/inicio.html", SegurancaConfig.destinoInterno(perfil));
        assertNull(SegurancaConfig.destinoInterno("MEDICO"));
    }
}
