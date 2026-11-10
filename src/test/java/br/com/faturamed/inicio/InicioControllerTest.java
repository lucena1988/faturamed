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
        var r = new InicioController(jdbc, revisoes).resumo(null, 15);
        assertEquals(0L, r.get("visitasAtencao"));
        assertEquals(3L, r.get("chamadosAbertos"));
        assertEquals(3L, r.get("lotesPendentes"));
        assertEquals(3L, r.get("solicitacoesPendentes"));
        verifyNoInteractions(revisoes);
    }
    @Test void cicloPorPeriodoSemRelatoriosZeraContagens() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var revisoes = mock(RevisaoVisitasService.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(5))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        var r = new InicioController(jdbc, revisoes).resumo(5, 10);
        assertEquals(0L, r.get("visitasPagasPeriodo"));
        assertEquals(0, r.get("relatoriosPeriodo"));
        verify(jdbc).queryForList(contains("generate_series"), eq(10));
    }
    @Test void equipeInternaVaiParaTelaDeInicio() {
        for (var perfil : List.of("ADMIN", "OPERADOR", "DEV")) assertEquals("/inicio.html", SegurancaConfig.destinoInterno(perfil));
        assertNull(SegurancaConfig.destinoInterno("MEDICO"));
    }
}
