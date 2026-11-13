package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.seguranca.UsuarioService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/** Recursos novos do FaturaChat: mencao por papel, status, salvas, lembretes, respostas rapidas e referencias. */
@SpringBootTest(properties = "ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named = "BASE_HOSPITALAR_TEST_DB", matches = "true")
@Transactional
class ChatRecursosNovosIntegracaoTest {
    @Autowired ChatService chat;
    @Autowired ChatStatusService status;
    @Autowired ChatOrganizacaoService organizacao;
    @Autowired ChatReferencias referencias;
    @Autowired UsuarioService usuarios;
    @Autowired JdbcTemplate jdbc;
    UsuarioAutenticado ana, bruno, carla;

    UsuarioAutenticado usuario(String perfil) {
        var conta = usuarios.criar(new UsuarioService.Novo(UUID.randomUUID() + "@teste.local", "Teste recursos " + perfil, "SenhaTesteSegura12", perfil, null));
        return new UsuarioAutenticado(conta.id(), conta.email(), conta.nome(), "hash", perfil, null, true, false, 0);
    }

    @BeforeEach void preparar() { ana = usuario("OPERADOR"); bruno = usuario("ADMIN"); carla = usuario("DEV"); }

    @Test void mencaoPorPapelAvisaQuemTemOPerfilNoGrupo() {
        var g = chat.criarGrupo(new ChatService.NovoGrupo("Fechamento", List.of(bruno.id(), carla.id()), false), ana);
        var m = chat.enviar(g.id(), "@admin pode aprovar o repasse?", ana);
        assertEquals(List.of(bruno.id()), jdbc.queryForList("select usuario_id from chat_mencao where mensagem_id=?", Long.class, m.id()));
    }

    @Test void statusEPendenciasComigo() {
        var c = chat.abrirDireta(bruno.id(), ana);
        var m = chat.enviar(c.id(), "@Teste preciso que confira o lote", ana);
        var s = status.definir(m.id(), "EM_ANALISE", true, bruno);
        assertEquals("EM_ANALISE", s.status());
        assertEquals(bruno.id(), s.responsavelId());
        assertEquals(1, status.comigo(bruno).size());
        assertEquals(1, status.daConversa(c.id(), ana).size());
        status.definir(m.id(), "RESOLVIDO", false, ana);
        assertTrue(status.comigo(bruno).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> status.definir(m.id(), "QUALQUER", false, ana));
        assertThrows(AccessDeniedException.class, () -> status.definir(m.id(), "PENDENTE", false, carla));
    }

    @Test void salvasLembretesERespostasRapidas() {
        var c = chat.abrirDireta(bruno.id(), ana);
        var m = chat.enviar(c.id(), "Regra de repasse do Itaim: 70%", ana);
        organizacao.salvar(m.id(), true, bruno);
        assertEquals(m.id(), organizacao.salvas(bruno).getFirst().mensagemId());
        organizacao.salvar(m.id(), false, bruno);
        assertTrue(organizacao.salvas(bruno).isEmpty());

        organizacao.lembrar(m.id(), Instant.now().plus(1, ChronoUnit.HOURS), bruno);
        assertEquals(1, organizacao.lembretes(bruno).size());
        jdbc.update("update chat_lembrete set quando = now() - interval '1 minute' where usuario_id = ?", bruno.id());
        organizacao.entregarLembretes();
        assertTrue(organizacao.lembretes(bruno).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> organizacao.lembrar(m.id(), Instant.now().minus(1, ChronoUnit.HOURS), bruno));

        var r = organizacao.salvarResposta("/Recebido", "Planilha recebida ✅", bruno);
        assertEquals("recebido", r.atalho());
        organizacao.salvarResposta("recebido", "Texto novo", bruno);
        assertEquals("Texto novo", organizacao.respostas(bruno).getFirst().texto());
        assertTrue(organizacao.respostas(ana).isEmpty());
    }

    @Test void referenciasIgnoramChavesInvalidasEInexistentes() {
        assertTrue(referencias.resolver(List.of("lote:999999999", "xpto:1", "relatorio:abc")).isEmpty());
    }
}
