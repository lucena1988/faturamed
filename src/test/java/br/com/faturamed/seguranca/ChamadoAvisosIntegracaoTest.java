package br.com.faturamed.seguranca;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@SpringBootTest(properties="ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named="BASE_HOSPITALAR_TEST_DB", matches="true")
@Transactional
class ChamadoAvisosIntegracaoTest {
    @Autowired ChamadoService chamados;
    @Autowired UsuarioService usuarios;
    @Autowired JdbcTemplate jdbc;
    UsuarioAutenticado funcionario, tecnico, outroTecnico;

    UsuarioAutenticado usuario(String perfil) {
        var conta = usuarios.criar(new UsuarioService.Novo(UUID.randomUUID()+"@teste.local",
                "Teste avisos", "SenhaTesteSegura12", perfil, null));
        return new UsuarioAutenticado(conta.id(), conta.email(), conta.nome(), "hash", perfil, null, true, false, 0);
    }

    @BeforeEach void preparar() {
        funcionario = usuario("OPERADOR");
        tecnico = usuario("ADMIN");
        outroTecnico = usuario("DEV");
    }

    long abrir() throws Exception {
        return chamados.criar("ERRO_BLOQUEANTE", "Teste de avisos", "Cenario ficticio",
                "/teste", List.of(), funcionario).chamado().id();
    }

    void aguardar(long id) {
        chamados.atualizarStatus(id, new ChamadoService.AlteracaoStatus(
                "AGUARDANDO_USUARIO", null, null, null, true, "Favor responder"), tecnico);
    }

    @Test void retornoERespostaNotificamDestinatariosCorretos() throws Exception {
        long id = abrir();
        aguardar(id);
        aguardar(id);
        var caixa = chamados.avisos(funcionario);
        assertEquals(1, caixa.naoLidas());
        assertEquals("RETORNO_SOLICITADO", caixa.itens().getFirst().tipo());
        assertEquals(0, chamados.avisos(tecnico).naoLidas());
        chamados.adicionarMensagem(id, "Segue o retorno", List.of(), funcionario);
        assertEquals("EM_ATENDIMENTO", chamados.detalhar(id, funcionario).chamado().status());
        assertEquals("RESPOSTA_SOLICITANTE", chamados.avisos(tecnico).itens().getFirst().tipo());
        assertEquals(0, chamados.avisos(outroTecnico).naoLidas());
        chamados.adicionarMensagem(id, "Retorno recebido", List.of(), tecnico);
        assertEquals("MENSAGEM_SUPORTE", chamados.avisos(funcionario).itens().getFirst().tipo());
        assertEquals(2, chamados.avisos(funcionario).naoLidas());
    }

    @Test void semResponsavelNotificaEquipeTecnicaAtiva() throws Exception {
        long id = abrir();
        var inativo = usuario("ADMIN");
        jdbc.update("update usuario_acesso set ativo=false where id=?", inativo.id());
        chamados.adicionarMensagem(id, "Mais detalhes", List.of(), funcionario);
        assertEquals(1, chamados.avisos(tecnico).naoLidas());
        assertEquals(1, chamados.avisos(outroTecnico).naoLidas());
        assertEquals(0, chamados.avisos(inativo).naoLidas());
        assertEquals(0, chamados.avisos(funcionario).naoLidas());
    }

    @Test void leituraPorChamadoIncluiAvisosForaDaPaginaEPreservaNovos() throws Exception {
        long primeiro = abrir(), segundo = abrir();
        for (int i = 0; i < 55; i++) {
            chamados.adicionarMensagem(primeiro, "Mensagem " + i, List.of(), tecnico);
        }
        aguardar(segundo);
        var caixa = chamados.avisos(funcionario);
        assertEquals(56, caixa.naoLidas());
        assertEquals(50, caixa.itens().size());
        long limite = caixa.itens().getFirst().id();
        chamados.adicionarMensagem(primeiro, "Mensagem posterior", List.of(), tecnico);
        chamados.lerAvisosChamado(primeiro, limite, funcionario);
        var restantes = chamados.avisos(funcionario);
        assertEquals(2, restantes.naoLidas());
        assertTrue(restantes.itens().stream().anyMatch(a -> a.chamadoId() == segundo));
        assertTrue(restantes.itens().stream().anyMatch(a -> a.id() > limite));
    }

    @Test void leituraIndividualPreservaOutroChamadoEIsolaUsuarios() throws Exception {
        long primeiro = abrir(), segundo = abrir();
        aguardar(primeiro);
        aguardar(segundo);
        var aviso = chamados.avisos(funcionario).itens().stream()
                .filter(a -> a.chamadoId() == primeiro).findFirst().orElseThrow();
        assertThrows(RecursoNaoEncontradoException.class, () -> chamados.lerAviso(aviso.id(), tecnico));
        assertEquals(2, chamados.avisos(funcionario).naoLidas());
        chamados.lerAviso(aviso.id(), funcionario);
        chamados.lerAviso(aviso.id(), funcionario);
        var restantes = chamados.avisos(funcionario);
        assertEquals(1, restantes.naoLidas());
        assertEquals(segundo, restantes.itens().getFirst().chamadoId());
    }
}
