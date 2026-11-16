package br.com.faturamed.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Controllers dos recursos novos do chat apenas repassam ao servico com o usuario logado. */
class ChatControllersNovosTest {
    private final UsuarioAutenticado eu = new UsuarioAutenticado(1, "a@x", "A", "h", "ADMIN", null, true, false, 0);

    @Test
    void referencias() {
        var refs = mock(ChatReferencias.class);
        when(refs.resolver(List.of("lote:1"))).thenReturn(List.of());
        assertThat(new ChatReferenciasController(refs).resolver(List.of("lote:1"))).isEmpty();
    }

    @Test
    void status() {
        var s = mock(ChatStatusService.class);
        var c = new ChatStatusController(s);
        c.definir(5, new ChatStatusController.NovoStatus("PENDENTE", null), eu);
        verify(s).definir(5, "PENDENTE", false, eu);
        c.definir(5, new ChatStatusController.NovoStatus("EM_ANALISE", true), eu);
        verify(s).definir(5, "EM_ANALISE", true, eu);
        c.daConversa(3, eu); verify(s).daConversa(3, eu);
        c.comigo(eu); verify(s).comigo(eu);
    }

    @Test
    void organizacao() {
        var o = mock(ChatOrganizacaoService.class);
        var c = new ChatOrganizacaoController(o);
        var quando = Instant.now();
        c.lembrar(5, new ChatOrganizacaoController.NovoLembrete(quando), eu); verify(o).lembrar(5, quando, eu);
        c.lembretes(eu); verify(o).lembretes(eu);
        c.cancelar(9, eu); verify(o).cancelarLembrete(9, eu);
        when(o.salvar(5, true, eu)).thenReturn(true);
        when(o.salvar(5, false, eu)).thenReturn(false);
        assertThat(c.salvar(5, Map.of(), eu)).containsEntry("salva", true);
        assertThat(c.salvar(5, Map.of("salva", false), eu)).containsEntry("salva", false);
        c.salvas(eu); verify(o).salvas(eu);
        c.respostas(eu); verify(o).respostas(eu);
        c.novaResposta(new ChatOrganizacaoController.NovaResposta("ok", "Ok!"), eu); verify(o).salvarResposta("ok", "Ok!", eu);
        c.apagarResposta(2, eu); verify(o).apagarResposta(2, eu);
    }
}
