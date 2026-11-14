package br.com.faturamed.chat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

/** Status de acompanhamento nas mensagens (validacoes e permissoes, sem banco). */
@SuppressWarnings("unchecked")
class ChatStatusServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatStatusService service = new ChatStatusService(jdbc);
    private final UsuarioAutenticado ana = new UsuarioAutenticado(7, "ana@x", "Ana", "h", "OPERADOR", null, true, false, 0);

    private void participa(boolean sim) {
        when(jdbc.queryForList(contains("from chat_mensagem m join chat_participante"), eq(Long.class), eq(7L), eq(50L)))
                .thenReturn(sim ? List.of(3L) : List.of());
    }

    @Test
    void quemNaoParticipaNaoMexeNoStatus() {
        participa(false);
        assertThatThrownBy(() -> service.definir(50, "PENDENTE", false, ana)).isInstanceOf(AccessDeniedException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void statusInvalidoERecusado() {
        participa(true);
        assertThatThrownBy(() -> service.definir(50, "ARQUIVADO", false, ana)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusVazioTiraOStatus() {
        participa(true);
        assertThat(service.definir(50, " ", false, ana)).isNull();
        verify(jdbc).update("delete from chat_mensagem_status where mensagem_id = ?", 50L);
    }

    @Test
    void definirGravaEmMaiusculoEAssumeQuandoPedido() {
        participa(true);
        var esperado = new ChatStatusService.StatusMensagem(50, "EM_ANALISE", 7L, "Ana", "Ana", Instant.now());
        when(jdbc.query(contains("where s.mensagem_id = any(?)"), any(RowMapper.class), any(Object.class))).thenReturn(List.of(esperado));
        assertThat(service.definir(50, "em_analise", true, ana)).isEqualTo(esperado);
        verify(jdbc).update(contains("insert into chat_mensagem_status"), eq(50L), eq("EM_ANALISE"), eq(7L), eq(7L), eq(true));
    }

    @Test
    void naoAssumirMantemOResponsavelAnterior() {
        participa(true);
        when(jdbc.query(contains("where s.mensagem_id = any(?)"), any(RowMapper.class), any(Object.class)))
                .thenReturn(List.of(new ChatStatusService.StatusMensagem(50, "RESOLVIDO", null, null, "Ana", Instant.now())));
        service.definir(50, "RESOLVIDO", false, ana);
        verify(jdbc).update(contains("insert into chat_mensagem_status"), eq(50L), eq("RESOLVIDO"), isNull(), eq(7L), eq(false));
    }

    @Test
    void statusDaConversaExigeParticipacao() {
        when(jdbc.queryForObject(contains("from chat_participante"), eq(Integer.class), eq(3L), eq(7L))).thenReturn(0);
        assertThatThrownBy(() -> service.daConversa(3, ana)).isInstanceOf(AccessDeniedException.class);
        when(jdbc.queryForObject(contains("from chat_participante"), eq(Integer.class), eq(4L), eq(7L))).thenReturn(1);
        when(jdbc.query(contains("where m.conversa_id = ?"), any(RowMapper.class), eq(4L))).thenReturn(List.of());
        assertThat(service.daConversa(4, ana)).isEmpty();
    }

    @Test
    void pendenciasComigoUsaOUsuarioEmTodosOsFiltros() {
        when(jdbc.query(contains("s.status <> 'RESOLVIDO'"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThat(service.comigo(ana)).isEmpty();
        verify(jdbc).query(contains("s.status <> 'RESOLVIDO'"), any(RowMapper.class), eq(7L), eq(7L), eq(7L), eq(7L), eq(7L), eq(7L));
    }
}
