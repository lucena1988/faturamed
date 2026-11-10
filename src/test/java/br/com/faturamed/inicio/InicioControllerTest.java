package br.com.faturamed.inicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.and;
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
        var r = new InicioController(jdbc, revisoes).resumo(null, null, null, null);
        assertEquals(0L, r.get("visitasAtencao"));
        assertEquals(3L, r.get("chamadosAbertos"));
        assertEquals(3L, r.get("lotesPendentes"));
        assertEquals(3L, r.get("solicitacoesPendentes"));
        verify(jdbc).queryForList(and(contains("interval '1 day'"), and(contains("as rascunho"), and(contains("as conferido"), contains("as enviado")))), eq(15));
        verifyNoInteractions(revisoes);
    }
    @Test void cicloPorPeriodoSemRelatoriosZeraContagens() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var revisoes = mock(RevisaoVisitasService.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(5L * 1440))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        var r = new InicioController(jdbc, revisoes).resumo("10d", "5d", null, null);
        assertEquals(0L, r.get("visitasPagasPeriodo"));
        assertEquals(0, r.get("relatoriosPeriodo"));
        verify(jdbc).queryForList(contains("generate_series"), eq(10));
    }
    @Test void producaoEmHorasAgrupaPeloMomentoDoRegistro() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        new InicioController(jdbc, mock(RevisaoVisitasService.class)).resumo("3h", null, "30m", "2h");
        verify(jdbc).queryForList(and(contains("interval '1 hour'"), contains("l.criado_em")), eq(3));
        verify(jdbc).query(and(contains("producao_lote"), contains("criado_em >=")), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq(30L));
        verify(jdbc).query(and(contains("chamado_suporte"), and(contains("group by status"), contains("criado_em >="))), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq(120L));
        verify(jdbc).query(and(contains("prazo_sla_em"), and(contains("<> 'CANCELADO'"), contains("criado_em >="))), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq(120L));
    }
    @Test void slaSemChamadosVoltaZerado() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        var r = new InicioController(jdbc, mock(RevisaoVisitasService.class)).resumo(null, null, null, null);
        assertEquals(java.util.Map.of("total", 0L, "noPrazo", 0L), r.get("chamadosSla"));
    }
    @Test void periodoAceitaMinutosHorasEDiasComLimites() {
        var padrao = new InicioController.Periodo(15, 'd');
        assertEquals(new InicioController.Periodo(1, 'm'), InicioController.Periodo.ler("1m", padrao));
        assertEquals(new InicioController.Periodo(2, 'h'), InicioController.Periodo.ler("2H", padrao));
        assertEquals(new InicioController.Periodo(120, 'm'), InicioController.Periodo.ler("999m", padrao));
        assertEquals(new InicioController.Periodo(1, 'd'), InicioController.Periodo.ler("0d", padrao));
        assertEquals(padrao, InicioController.Periodo.ler("abc", padrao));
        assertNull(InicioController.Periodo.ler("", null));
        assertEquals(180, new InicioController.Periodo(3, 'h').minutos());
    }
    @Test void equipeInternaVaiParaTelaDeInicio() {
        for (var perfil : List.of("ADMIN", "OPERADOR", "DEV")) assertEquals("/inicio.html", SegurancaConfig.destinoInterno(perfil));
        assertNull(SegurancaConfig.destinoInterno("MEDICO"));
    }
}
