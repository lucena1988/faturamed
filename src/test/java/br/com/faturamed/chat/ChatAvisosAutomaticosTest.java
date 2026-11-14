package br.com.faturamed.chat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.FechamentoFaturadoEventos;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Avisos automaticos do Robo FaturaMed para a equipe interna. */
class ChatAvisosAutomaticosTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatService chat = mock(ChatService.class);
    private final ChatAvisosAutomaticos avisos = new ChatAvisosAutomaticos(jdbc, chat);

    private void equipe(Long... ids) {
        when(jdbc.queryForList(contains("from usuario_acesso"), eq(Long.class), eq(ChatService.EMAIL_ROBO), anyLong())).thenReturn(List.of(ids));
    }

    @Test
    void planilhaImportadaAvisaAEquipeMenosQuemImportou() {
        equipe(2L, 3L);
        avisos.planilhaImportada(new FechamentoFaturadoEventos.PlanilhaHospitalImportada(1, "Hospital Itaim", "2026-11", 42, 1));
        verify(jdbc).queryForList(contains("from usuario_acesso"), eq(Long.class), eq(ChatService.EMAIL_ROBO), eq(1L));
        verify(chat).avisarSistema(eq(2L), contains("Planilha do Hospital Itaim importada (competência 2026-11): 42"));
        verify(chat).avisarSistema(eq(3L), anyString());
    }

    @Test
    void conciliacaoSoAvisaQuandoTemDivergencia() {
        avisos.conciliacaoGerada(new FechamentoFaturadoEventos.ConciliacaoGerada(6, "Itaim", 10, 0, 1));
        verifyNoInteractions(chat);
        equipe(2L);
        avisos.conciliacaoGerada(new FechamentoFaturadoEventos.ConciliacaoGerada(6, "Itaim", 10, 3, 1));
        verify(chat).avisarSistema(eq(2L), contains("3 divergência(s) em 10 procedimento(s): #relatorio 6"));
    }

    @Test
    void falhaAoAvisarUmaPessoaNaoImpedeAsOutras() {
        equipe(2L, 3L);
        doThrow(new IllegalStateException("sem conversa")).when(chat).avisarSistema(eq(2L), anyString());
        avisos.planilhaImportada(new FechamentoFaturadoEventos.PlanilhaHospitalImportada(1, "H", "2026-11", 1, 9));
        verify(chat).avisarSistema(eq(3L), anyString());
    }

    @Test
    void lotesParadosListaOsMaisAntigosComoAtalho() {
        when(jdbc.queryForList(contains("limit 5"), eq(Long.class))).thenReturn(List.of(10L, 11L));
        when(jdbc.queryForObject(contains("count(*) from producao_lote"), eq(Long.class))).thenReturn(7L);
        equipe(2L);
        avisos.lotesParados();
        verify(chat).avisarSistema(eq(2L), eq("⏳ 7 lote(s) de produção parado(s) em rascunho há mais de 3 dias. Os mais antigos: #lote 10 #lote 11"));
    }

    @Test
    void semLoteParadoNaoAvisa() {
        when(jdbc.queryForList(contains("limit 5"), eq(Long.class))).thenReturn(List.of());
        avisos.lotesParados();
        verifyNoInteractions(chat);
    }
}
