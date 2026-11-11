package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.seguranca.UsuarioService;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named = "BASE_HOSPITALAR_TEST_DB", matches = "true")
@Transactional
class ChatIntegracaoTest {
    @Autowired ChatService chat;
    @Autowired UsuarioService usuarios;
    UsuarioAutenticado ana, bruno, carla;

    UsuarioAutenticado usuario(String perfil) {
        var conta = usuarios.criar(new UsuarioService.Novo(UUID.randomUUID() + "@teste.local",
                "Teste chat " + perfil, "SenhaTesteSegura12", perfil, null));
        return new UsuarioAutenticado(conta.id(), conta.email(), conta.nome(), "hash", perfil, null, true, false, 0);
    }

    @BeforeEach void preparar() {
        ana = usuario("OPERADOR");
        bruno = usuario("ADMIN");
        carla = usuario("DEV");
    }

    @Test void conversaDiretaEUnicaPorParEContaNaoLidas() {
        var c1 = chat.abrirDireta(bruno.id(), ana);
        var c2 = chat.abrirDireta(ana.id(), bruno);
        assertEquals(c1.id(), c2.id());
        chat.enviar(c1.id(), "  Oi, o lote 112 voltou?  ", ana);
        var m2 = chat.enviar(c1.id(), "Mais uma", ana);
        assertEquals(2, chat.naoLidas(bruno));
        assertEquals(0, chat.naoLidas(ana));
        var pagina = chat.mensagens(c1.id(), null, bruno);
        assertEquals("Oi, o lote 112 voltou?", pagina.mensagens().getFirst().texto());
        chat.marcarLida(c1.id(), m2.id(), bruno);
        assertEquals(0, chat.naoLidas(bruno));
        assertEquals(m2.id(), chat.mensagens(c1.id(), null, ana).leituraContato());
    }

    @Test void quemNaoParticipaNaoLeNemEscreve() {
        var c = chat.abrirDireta(bruno.id(), ana);
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.mensagens(c.id(), null, carla));
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.enviar(c.id(), "intruso", carla));
    }

    @Test void recusaMensagemVaziaEConversaConsigoMesmo() {
        var c = chat.abrirDireta(bruno.id(), ana);
        assertThrows(IllegalArgumentException.class, () -> chat.enviar(c.id(), "   ", ana));
        assertThrows(IllegalArgumentException.class, () -> chat.abrirDireta(ana.id(), ana));
    }

    @Test void grupoContaNaoLidasEControlaParticipantes() {
        var g = chat.criarGrupo(new ChatService.NovoGrupo("  Faturamento ", List.of(bruno.id())), ana);
        assertEquals("GRUPO", g.tipo());
        assertEquals("Faturamento", g.nome());
        assertEquals(2, g.participantes());
        assertTrue(g.souCriador());
        chat.enviar(g.id(), "Bom dia, grupo", ana);
        assertEquals(1, chat.naoLidas(bruno));
        // quem entra depois nao herda o historico como nao lido
        chat.adicionarParticipante(g.id(), carla.id(), bruno);
        assertEquals(0, chat.naoLidas(carla));
        assertEquals(3, chat.participantesDoGrupo(g.id(), carla).size());
        // so quem criou remove outras pessoas; cada um pode sair
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> chat.removerParticipante(g.id(), ana.id(), bruno));
        chat.removerParticipante(g.id(), carla.id(), carla);
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.mensagens(g.id(), null, carla));
        chat.removerParticipante(g.id(), bruno.id(), ana);
        assertEquals(1, chat.participantesDoGrupo(g.id(), ana).size());
    }

    @Test void grupoExigeNomeEParticipantes() {
        assertThrows(IllegalArgumentException.class, () -> chat.criarGrupo(new ChatService.NovoGrupo(" ", List.of(bruno.id())), ana));
        assertThrows(IllegalArgumentException.class, () -> chat.criarGrupo(new ChatService.NovoGrupo("Só eu", List.of(ana.id())), ana));
        var direta = chat.abrirDireta(bruno.id(), ana);
        assertThrows(IllegalArgumentException.class, () -> chat.adicionarParticipante(direta.id(), carla.id(), ana));
    }

    @Test void chamadaExigeVideoConfiguradoEParticipacao() {
        var c = chat.abrirDireta(bruno.id(), ana);
        assertThrows(IllegalArgumentException.class, () -> chat.iniciarChamada(c.id(), "", ana));
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.iniciarChamada(c.id(), "video.teste.local", carla));
        var chamada = chat.iniciarChamada(c.id(), "video.teste.local", ana);
        assertTrue(chamada.sala().matches("faturamed-[0-9a-f]{24}"));
        assertEquals(1, chat.naoLidas(bruno));
    }

    @Test void silenciarTiraConversaDoAvisoSonoroMasMantemNaoLidas() {
        var c = chat.abrirDireta(bruno.id(), ana);
        chat.silenciar(c.id(), true, bruno);
        chat.enviar(c.id(), "Mensagem sem som", ana);
        var resumo = chat.resumoNaoLidas(bruno);
        assertEquals(1, resumo.total());
        assertEquals(0, resumo.comSom());
        assertTrue(chat.conversas(bruno).getFirst().silenciado());
        assertFalse(chat.conversas(ana).getFirst().silenciado());
        chat.silenciar(c.id(), false, bruno);
        assertEquals(1, chat.resumoNaoLidas(bruno).comSom());
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.silenciar(c.id(), true, carla));
    }

    @Test void anexoSoComArquivoESoParticipanteBaixa() {
        var c = chat.abrirDireta(bruno.id(), ana);
        var png = ChatAnexos.validar("print.png", ChatAnexosTest.PNG);
        var msg = chat.enviar(c.id(), "", List.of(png), ana);
        assertEquals(1, msg.anexos().size());
        assertEquals("print.png", msg.anexos().getFirst().nome());
        assertEquals("📎 print.png", chat.conversas(bruno).getFirst().ultimaMensagem());
        long anexoId = msg.anexos().getFirst().id();
        assertArrayEquals(ChatAnexosTest.PNG, chat.baixarAnexo(anexoId, bruno).conteudo());
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.baixarAnexo(anexoId, carla));
        assertEquals(1, chat.mensagens(c.id(), null, bruno).mensagens().getFirst().anexos().size());
    }

    @Test void responderReagirEditarApagarEFixar() {
        var c = chat.abrirDireta(bruno.id(), ana);
        var original = chat.enviar(c.id(), "Lote 112 voltou?", ana);
        var resposta = chat.enviar(c.id(), "Voltou sim", List.of(), original.id(), bruno);
        assertEquals("Lote 112 voltou?", resposta.resposta().texto());
        var reagida = chat.reagir(original.id(), "👍", bruno);
        assertEquals(List.of(bruno.id()), reagida.reacoes().getFirst().usuarios());
        assertTrue(chat.reagir(original.id(), "👍", bruno).reacoes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> chat.reagir(original.id(), "💩", bruno));
        assertTrue(chat.editar(original.id(), "Lote 113 voltou?", ana).editada());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> chat.editar(original.id(), "x", bruno));
        assertEquals(original.id(), chat.fixar(c.id(), original.id(), bruno).fixada().id());
        var apagada = chat.apagar(original.id(), ana);
        assertTrue(apagada.apagada());
        assertEquals("", apagada.texto());
        assertNull(chat.conversas(ana).getFirst().fixada());
    }

    @Test void buscaSoNasConversasDaPessoa() {
        var c = chat.abrirDireta(bruno.id(), ana);
        chat.enviar(c.id(), "Conferir a guia do Hospital Central", ana);
        assertEquals(1, chat.buscar("hospital central", bruno).size());
        assertTrue(chat.buscar("hospital central", carla).isEmpty());
        assertTrue(chat.buscar("h", bruno).isEmpty());
    }

    @Test void grupoOficialSoAdminGerencia() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> chat.criarGrupo(new ChatService.NovoGrupo("Diretoria", List.of(bruno.id()), true), ana));
        var g = chat.criarGrupo(new ChatService.NovoGrupo("Diretoria", List.of(ana.id()), true), bruno);
        assertTrue(g.oficial());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> chat.adicionarParticipante(g.id(), carla.id(), ana));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> chat.removerParticipante(g.id(), ana.id(), ana));
        chat.adicionarParticipante(g.id(), carla.id(), bruno);
        assertEquals(3, chat.participantesDoGrupo(g.id(), bruno).size());
    }

    @Test void statusERetencao() {
        assertEquals("OCUPADO", chat.definirStatus(new ChatService.MeuStatus("OCUPADO", "Fechando lotes"), ana).status());
        assertEquals("Fechando lotes", chat.meuStatus(ana).recado());
        assertThrows(IllegalArgumentException.class, () -> chat.definirStatus(new ChatService.MeuStatus("SUMIDO", null), ana));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> chat.definirConfiguracao(new ChatService.Configuracao(6), ana));
        assertEquals(6, chat.definirConfiguracao(new ChatService.Configuracao(6), bruno).retencaoMeses());
        chat.definirConfiguracao(new ChatService.Configuracao(null), bruno);
    }

    @Test void roboAvisaNaConversaDoSistemaSoLeitura() {
        chat.avisarSistema(ana.id(), "🔔 Teste do robô #chamado 1");
        var conversa = chat.conversas(ana).stream().filter(ChatService.Conversa::sistema).findFirst().orElseThrow();
        assertEquals(1, conversa.naoLidas());
        assertThrows(IllegalArgumentException.class, () -> chat.enviar(conversa.id(), "oi robô", ana));
        assertTrue(chat.mensagens(conversa.id(), null, ana).mensagens().getFirst().sistema());
    }

    @Test void exportaPdfDaConversa() throws Exception {
        var c = chat.abrirDireta(bruno.id(), ana);
        chat.enviar(c.id(), "Mensagem com acentuação e emoji 🎉", ana);
        byte[] pdf = ChatExportadorPdf.gerar("Teste", chat.historicoCompleto(c.id(), ana), "Ana");
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test void naoAbreConversaComPerfilExterno() {
        assertThrows(RecursoNaoEncontradoException.class, () -> chat.abrirDireta(Long.MAX_VALUE, ana));
    }
}
