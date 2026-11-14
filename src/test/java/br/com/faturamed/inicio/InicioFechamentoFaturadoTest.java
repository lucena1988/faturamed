package br.com.faturamed.inicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import br.com.faturamed.conciliacao.RevisaoVisitasService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Tela de Inicio: contagem de visitas do ultimo relatorio e card Fechamento Faturado por periodo e filtros (sem banco). */
class InicioFechamentoFaturadoTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RevisaoVisitasService revisoes = mock(RevisaoVisitasService.class, RETURNS_DEEP_STUBS);
    private final InicioController controller = new InicioController(jdbc, revisoes);


    private static ConciliadorVisitas.Visita visita(ConciliadorVisitas.Status s) {
        var v = mock(ConciliadorVisitas.Visita.class);
        when(v.status()).thenReturn(s);
        return v;
    }


    @Test
    void resumoContaVisitasDoUltimoRelatorioEAplicaPeriodos() throws Exception {
        when(jdbc.queryForList(contains("max(id) from conciliacao_visitas"), eq(Long.class))).thenReturn(List.of(6L));
        var relatorio = mock(ConciliadorVisitas.Relatorio.class);
        when(relatorio.hospital()).thenReturn("Itaim");
        var visitas = List.of(visita(ConciliadorVisitas.Status.PAGA), visita(ConciliadorVisitas.Status.DIVERGENTE), visita(ConciliadorVisitas.Status.PENDENTE));
        when(relatorio.visitas()).thenReturn(visitas);
        when(revisoes.carregar(6L).atual()).thenReturn(relatorio);
        when(jdbc.queryForList(contains("not exists (select 1 from conciliacao_visitas n"), eq(Long.class), eq(2880L))).thenReturn(List.of(6L));
        when(jdbc.queryForObject(contains("from chamado_suporte where status in"), eq(Long.class))).thenReturn(3L);
        var r = controller.resumo("2h", "2d", "1d", "30m");
        assertThat(r).containsEntry("visitasAtencao", 2L).containsEntry("visitasDivergentes", 1L).containsEntry("visitasPagas", 1L)
                .containsEntry("visitasPendentes", 1L).containsEntry("relatorioHospital", "Itaim").containsEntry("chamadosAbertos", 3L)
                .containsEntry("visitasPagasPeriodo", 1L).containsEntry("visitasDivergentesPeriodo", 1L).containsEntry("visitasPendentesPeriodo", 1L)
                .containsEntry("relatoriosPeriodo", 1);
        verify(jdbc).queryForList(contains("date_trunc('hour'"), eq(2));       // producao por hora
        verify(jdbc).query(contains("from producao_lote where criado_em >="), any(RowCallbackHandler.class), eq(1440L));
        verify(jdbc).query(contains("and criado_em >= now() - ? * interval '1 minute'"), any(RowCallbackHandler.class), eq(30L));
    }

    @Test
    void fechamentoFaturadoTodoPeriodoSemDadosDevolveSerieVazia() {
        var limites = new java.util.HashMap<String, Object>(); limites.put("primeira", null); limites.put("ultima", null);
        when(jdbc.queryForMap(contains("min(v.data_visita)"), any(Object[].class))).thenReturn(limites);
        var r = controller.fechamentoFaturado("tudo", null, null);
        assertThat(r).containsEntry("agrupamento", "mes").containsEntry("serie", List.of());
    }

    @Test
    void fechamentoFaturadoTodoPeriodoAgrupaPorMesComFiltroDeHospitalETipo() {
        when(jdbc.queryForMap(contains("min(v.data_visita)"), any(Object[].class)))
                .thenReturn(Map.of("primeira", java.sql.Date.valueOf("2026-01-02"), "ultima", java.sql.Date.valueOf("2026-02-27")));
        var r = controller.fechamentoFaturado("tudo", 2L, "tipo:exame");
        assertThat(r).containsEntry("agrupamento", "mes").containsKeys("serie", "porTipo", "composicao");
        verify(jdbc).queryForList(contains("to_char(d, 'MM/YYYY')"), eq(2L), eq("EXAME"), any(), any());
    }

    @Test
    void fechamentoFaturadoPorDiasEPorHorasComProcedimentoEspecifico() {
        when(jdbc.queryForMap(contains("min(v.data_visita)"), any(Object[].class))).thenReturn(Map.of());
        controller.fechamentoFaturado("15d", null, "cod:40101010");
        verify(jdbc).queryForList(contains("to_char(d, 'DD/MM')"), eq("40101010"), eq(15));
        controller.fechamentoFaturado("3h", 1L, null);
        verify(jdbc).queryForList(contains("to_char(d, 'HH24:MI')"), eq(1L), eq(3));
        verify(jdbc).query(contains("a.criado_em >= now() - ? * interval '1 minute'"), any(RowCallbackHandler.class), eq(1L), eq(180L));
    }
}
