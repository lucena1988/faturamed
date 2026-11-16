package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

/** Portal do hospital: so o hospital dono acessa, confere lotes, contesta e aprova (sem banco). */
@SuppressWarnings("unchecked")
class HospitalPortalServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FechamentoProducaoService fechamentos = mock(FechamentoProducaoService.class);
    private final HistoricoProducao historico = mock(HistoricoProducao.class);
    private final HospitalPortalService portal = new HospitalPortalService(jdbc, fechamentos, historico);

    private void dono(long fechamento, Long hospital) {
        doReturn(hospital).when(jdbc).query(contains("select hospital_id from producao_fechamento"), any(ResultSetExtractor.class), eq(fechamento));
    }
    private void status(long fechamento, String s) {
        when(jdbc.queryForObject(contains("select status from producao_fechamento"), eq(String.class), eq(fechamento))).thenReturn(s);
    }

    @Test
    void usuarioSemHospitalNaoEntraNoPortal() {
        doReturn(null).when(jdbc).query(contains("perfil='HOSPITAL'"), any(ResultSetExtractor.class), eq(1L));
        assertThatThrownBy(() -> portal.hospitalDoUsuario(1)).isInstanceOf(AccessDeniedException.class);
        doReturn(7L).when(jdbc).query(contains("perfil='HOSPITAL'"), any(ResultSetExtractor.class), eq(2L));
        assertThat(portal.hospitalDoUsuario(2)).isEqualTo(7L);
        when(jdbc.queryForObject(contains("from hospital_cadastro"), eq(String.class), eq(7L))).thenReturn("Hospital A");
        assertThat(portal.nomeHospital(7)).isEqualTo("Hospital A");
        portal.listar(7); verify(fechamentos).fechamentos(7L);
    }

    @Test
    void outroHospitalNaoVeOFechamento() {
        dono(10, 7L);
        assertThatThrownBy(() -> portal.abrir(10, 8, "x")).isInstanceOf(RecursoNaoEncontradoException.class);
        dono(11, null);
        assertThatThrownBy(() -> portal.nfArquivo(11, 7)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> portal.detalheParaPlanilha(11, 7)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(fechamentos);
    }

    @Test
    void abrirRegistraPrimeiraVisitaEMontaAConferencia() {
        dono(10, 7L);
        when(jdbc.update(contains("visto_hospital_em=now()"), eq(10L))).thenReturn(1, 0);
        when(jdbc.query(contains("from producao_fechamento_lote fl"), any(RowMapper.class), eq(10L))).thenReturn(List.of());
        when(jdbc.queryForObject(contains("coalesce(valor_enviado,total)"), eq(BigDecimal.class), eq(10L))).thenReturn(new BigDecimal("144.00"));
        var c = portal.abrir(10, 7, "Faturamento");
        assertThat(c.valorEnviado()).isEqualByComparingTo("144.00");
        portal.abrir(10, 7, "Faturamento");
        verify(historico, times(1)).fechamento(10L, "VISTO", "Fechamento aberto pelo hospital para conferência", "Faturamento");
        verify(fechamentos, times(2)).detalhe(10L);
    }

    @Test
    void naoAlteraFechamentoJaAprovado() {
        dono(10, 7L);
        status(10, "APROVADO");
        assertThatThrownBy(() -> portal.conferirLote(10, 1, true, 7, "x")).isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(() -> portal.conferirTodos(10, 7, "x")).isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(() -> portal.contestar(10, 1, "m", 7, "x")).isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(() -> portal.aprovar(10, 7, "x")).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test
    void conferirEDesfazerConferenciaDeUmLote() {
        dono(10, 7L); status(10, "AGUARDANDO");
        when(jdbc.update(contains("set conferido_em=?, conferido_por=?"), any(), any(), eq(10L), eq(99L))).thenReturn(0);
        assertThatThrownBy(() -> portal.conferirLote(10, 99, true, 7, "x")).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.update(contains("set conferido_em=?, conferido_por=?"), any(), any(), eq(10L), eq(5L))).thenReturn(1);
        portal.conferirLote(10, 5, true, 7, "Ana");
        verify(historico).lote(eq(5L), eq("CONFERIDO_HOSPITAL"), contains("nº 10"), eq("Ana"));
        portal.conferirLote(10, 5, false, 7, "Ana");
        verify(jdbc).update(contains("set conferido_em=?, conferido_por=?"), isNull(), isNull(), eq(10L), eq(5L));
        verify(historico).lote(5L, "REABERTO_HOSPITAL", "Hospital desfez a conferência do lote", "Ana");
    }

    @Test
    void conferirTodosMarcaSoOsQueFaltam() {
        dono(10, 7L); status(10, "AGUARDANDO");
        when(jdbc.queryForList(contains("conferido_em is null"), eq(Long.class), eq(10L))).thenReturn(List.of(1L, 2L), List.of());
        assertThat(portal.conferirTodos(10, 7, "Ana")).isEqualTo(2);
        verify(historico).fechamento(10L, "LOTES_CONFERIDOS", "2 lote(s) marcados como conferidos de uma vez", "Ana");
        assertThat(portal.conferirTodos(10, 7, "Ana")).isZero();
        verify(historico, times(1)).fechamento(eq(10L), eq("LOTES_CONFERIDOS"), anyString(), anyString());
    }

    @Test
    void contestarExigeMotivoERetirarDesfaz() {
        dono(10, 7L); status(10, "AGUARDANDO");
        assertThatThrownBy(() -> portal.contestar(10, 3, " ", 7, "x")).hasMessageContaining("motivo");
        when(jdbc.update(contains("contestacao='PENDENTE', contestacao_motivo=?"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> portal.contestar(10, 3, "nao realizado", 7, "x")).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.update(contains("contestacao='PENDENTE', contestacao_motivo=?"), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(contains("select lote_id from producao_item"), eq(Long.class), eq(3L))).thenReturn(40L);
        when(jdbc.queryForObject(contains("select nr_atendimento"), eq(String.class), eq(3L))).thenReturn("12345678");
        portal.contestar(10, 3, "x".repeat(600), 7, "Ana");
        verify(jdbc).update(contains("contestacao='PENDENTE', contestacao_motivo=?"), eq("x".repeat(500)), eq("Ana"), eq(3L), eq(10L));
        verify(historico).lote(eq(40L), eq("CONTESTADA"), contains("12345678"), eq("Ana"));
        when(jdbc.update(contains("set contestacao=null"), any(Object[].class))).thenReturn(1);
        portal.contestar(10, 3, null, 7, "Ana");
        verify(historico).lote(40L, "CONTESTACAO_RETIRADA", "Hospital retirou a contestação do atendimento 12345678", "Ana");
    }

    @Test
    void aprovarExigeTodosOsLotesConferidos() {
        dono(10, 7L); status(10, "AGUARDANDO");
        when(jdbc.queryForObject(contains("count(*) from producao_fechamento_lote"), eq(Integer.class), eq(10L))).thenReturn(2, 0);
        assertThatThrownBy(() -> portal.aprovar(10, 7, "Ana")).hasMessageContaining("faltam 2");
        portal.aprovar(10, 7, "Ana");
        verify(fechamentos).aplicarContestacoes(10L, "Ana");
        verify(jdbc).update(contains("set status='APROVADO'"), eq("Ana (portal do hospital)"), any(), eq(10L));
        verify(historico).fechamento(10L, "APROVADO", "Aprovado e liberado para NF pelo hospital", "Ana");
    }

    @Test
    void nfEPlanilhaDoProprioHospital() {
        dono(10, 7L);
        when(fechamentos.nfArquivo(10L)).thenReturn(Map.of("nf_arquivo_nome", "nf.pdf"));
        assertThat(portal.nfArquivo(10, 7)).containsEntry("nf_arquivo_nome", "nf.pdf");
        portal.detalheParaPlanilha(10, 7);
        verify(fechamentos).detalhe(10L);
    }
}
