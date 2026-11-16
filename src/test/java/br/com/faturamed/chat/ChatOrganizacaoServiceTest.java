package br.com.faturamed.chat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

/** Lembretes, mensagens salvas e respostas rapidas (validacoes e entrega, sem banco). */
@SuppressWarnings("unchecked")
class ChatOrganizacaoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatService chat = mock(ChatService.class);
    private final ChatOrganizacaoService service = new ChatOrganizacaoService(jdbc, chat);
    private final UsuarioAutenticado bruno = new UsuarioAutenticado(8, "b@x", "Bruno", "h", "ADMIN", null, true, false, 0);

    private void participa(boolean sim) {
        when(jdbc.queryForObject(contains("join chat_participante p on p.conversa_id = m.conversa_id"), eq(Integer.class), eq(8L), eq(40L)))
                .thenReturn(sim ? 1 : 0);
    }

    @Test
    void lembreteExigeParticipacaoEHorarioValido() {
        participa(false);
        assertThatThrownBy(() -> service.lembrar(40, Instant.now().plus(1, ChronoUnit.HOURS), bruno)).isInstanceOf(AccessDeniedException.class);
        participa(true);
        assertThatThrownBy(() -> service.lembrar(40, null, bruno)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lembrar(40, Instant.now().minus(2, ChronoUnit.HOURS), bruno)).hasMessageContaining("futuro");
        assertThatThrownBy(() -> service.lembrar(40, Instant.now().plus(400, ChronoUnit.DAYS), bruno)).hasMessageContaining("um ano");
    }

    @Test
    void lembreteValidoEGravadoERetornado() {
        participa(true);
        when(jdbc.queryForObject(contains("insert into chat_lembrete"), eq(Long.class), eq(8L), eq(40L), any())).thenReturn(5L);
        var l = new ChatOrganizacaoService.Lembrete(5, 40, 3, "Ana", "Ana", "texto", Instant.now());
        when(jdbc.query(contains("from chat_lembrete l"), any(RowMapper.class), eq(8L), eq(8L))).thenReturn(List.of(l));
        assertThat(service.lembrar(40, Instant.now().plus(1, ChronoUnit.HOURS), bruno)).isEqualTo(l);
    }

    @Test
    void cancelarSoApagaOProprioLembretePendente() {
        service.cancelarLembrete(5, bruno);
        verify(jdbc).update(contains("delete from chat_lembrete where id = ? and usuario_id = ? and entregue_em is null"), eq(5L), eq(8L));
    }

    @Test
    void entregaLembretesVencidosPeloRoboEMarcaComoEntregue() {
        String longo = "x".repeat(400);
        when(jdbc.queryForList(contains("for update of l skip locked"))).thenReturn(List.of(
                Map.of("id", 1L, "usuario_id", 8L, "texto", "Conferir lote", "autor", "Ana", "conversa", "Equipe"),
                Map.of("id", 2L, "usuario_id", 9L, "texto", longo, "autor", "Carlos", "conversa", "Carlos")));
        service.entregarLembretes();
        verify(chat).avisarSistema(8L, "⏰ Lembrete — Ana em Equipe: \"Conferir lote\"");
        verify(chat).avisarSistema(eq(9L), argThat(t -> t.endsWith("…\"") && t.length() < 360));
        verify(jdbc).update("update chat_lembrete set entregue_em = now() where id = ?", 1L);
        verify(jdbc).update("update chat_lembrete set entregue_em = now() where id = ?", 2L);
    }

    @Test
    void lembreteQueFalhaAoAvisarTambemEMarcado() {
        when(jdbc.queryForList(contains("for update of l skip locked"))).thenReturn(List.of(
                Map.of("id", 3L, "usuario_id", 8L, "texto", "t", "autor", "A", "conversa", "C")));
        doThrow(new IllegalStateException("x")).when(chat).avisarSistema(anyLong(), anyString());
        service.entregarLembretes();
        verify(jdbc).update("update chat_lembrete set entregue_em = now() where id = ?", 3L);
    }

    @Test
    void salvarERemoverDosSalvos() {
        participa(true);
        assertThat(service.salvar(40, true, bruno)).isTrue();
        verify(jdbc).update(contains("insert into chat_mensagem_salva"), eq(8L), eq(40L));
        assertThat(service.salvar(40, false, bruno)).isFalse();
        verify(jdbc).update(contains("delete from chat_mensagem_salva"), eq(8L), eq(40L));
        participa(false);
        assertThatThrownBy(() -> service.salvar(40, true, bruno)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void respostaRapidaNormalizaAtalhoEValidaTamanhos() {
        when(jdbc.queryForObject(contains("insert into chat_resposta_rapida"), eq(Long.class), eq(8L), eq("recebido-ok"), eq("Recebido ✅"))).thenReturn(1L);
        var r = service.salvarResposta(" /Recebido-OK! ", "  Recebido ✅  ", bruno);
        assertThat(r.atalho()).isEqualTo("recebido-ok");
        assertThat(r.texto()).isEqualTo("Recebido ✅");
        assertThatThrownBy(() -> service.salvarResposta("/", "texto", bruno)).hasMessageContaining("Atalho");
        assertThatThrownBy(() -> service.salvarResposta("a".repeat(31), "texto", bruno)).hasMessageContaining("Atalho");
        assertThatThrownBy(() -> service.salvarResposta("ok", " ", bruno)).hasMessageContaining("Texto");
        assertThatThrownBy(() -> service.salvarResposta("ok", "x".repeat(1001), bruno)).hasMessageContaining("Texto");
        assertThatThrownBy(() -> service.salvarResposta(null, null, bruno)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apagarRespostaSoDaPropriaPessoa() {
        service.apagarResposta(4, bruno);
        verify(jdbc).update("delete from chat_resposta_rapida where id = ? and usuario_id = ?", 4L, 8L);
    }
}
