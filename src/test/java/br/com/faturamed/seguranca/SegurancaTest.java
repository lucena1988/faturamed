package br.com.faturamed.seguranca;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AcessoController.class, SegurancaTest.OperacaoProbe.class})
@Import({SegurancaConfig.class, SegurancaTest.OperacaoProbe.class})
class SegurancaTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UsuarioService users;
    private UsuarioAutenticado admin, medico;
    @BeforeEach void configurar() {
        admin=new UsuarioAutenticado(1,"admin@test.local","Admin",encoder.encode("SenhaTesteSegura12"),"ADMIN",null,true,false,0);
        medico=new UsuarioAutenticado(2,"medico@test.local","Medico","hash","MEDICO",10L,true,false,0);
        when(users.sessaoValida(any())).thenReturn(true);
        when(users.loadUserByUsername("admin@test.local")).thenReturn(admin);
    }
    @Test void apiSemLoginRetorna401SemRedirecionar() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/json"));
    }
    @Test void paginaSemLoginRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html"));
    }
    @Test void medicoNaoAcessaAdministracao() throws Exception {
        mvc.perform(get("/api/base-hospitalar/acompanhamento?hospitalId=1").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/base-hospitalar/visitas?hospitalId=1").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/base-hospitalar/arquivos/1/original").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/base-hospitalar/hospital").with(user(medico)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/solicitacoes-medicos").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ciencias-medicos").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitacoes-medicos/1/resposta").with(user(medico)).with(csrf()).contentType("application/json").content("{\"resposta\":\"Tentativa indevida\"}")) .andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/liberacao").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/liberacao").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/recebimentos/pagamentos/55/comprovantes").with(user(medico)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/recebimentos/pagamentos/55/comprovantes").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/recebimentos/pagamentos/55/comprovantes/1").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/recebimentos/pagamentos/55/comprovantes/1/retirada").with(user(medico)).with(csrf()).contentType("application/json").content("{\"justificativa\":\"teste\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/recebimentos/pagamentos").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/recebimentos/pagamentos/55").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/recebimentos/pagamentos").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/recebimentos").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/recebimentos/visitas/2").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/cobranca/lote").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/cobranca").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/cobranca.xlsx").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/conciliacoes/visitas/10/cobranca/acompanhamento").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/conciliacoes/visitas/10/cobranca/visitas/2").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(user(medico)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verify(users,never()).criar(any());
    }
    @Test void adminPodeListarUsuariosSemExporHash() throws Exception {
        when(users.listar()).thenReturn(java.util.List.of(new UsuarioService.Usuario(1,"admin@test.local","Admin","ADMIN",null,true,0)));
        mvc.perform(get("/api/usuarios").with(user(admin))).andExpect(status().isOk()).andExpect(jsonPath("$[0].senhaHash").doesNotExist());
    }
    @Test void escritaSemCsrfBloqueadaInclusiveLogin() throws Exception {
        mvc.perform(post("/api/auth/login").param("username","admin@test.local").param("password","SenhaTesteSegura12")).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/2/situacao").with(user(admin)).contentType("application/json").content("{\"ativo\":false,\"versao\":0}"))
                .andExpect(status().isForbidden());
        verify(users,never()).situacao(anyLong(),any(),anyLong());
    }
    @Test void escritaComCsrfPermitidaParaAdmin() throws Exception {
        mvc.perform(put("/api/usuarios/2/situacao").with(user(admin)).with(csrf()).contentType("application/json").content("{\"ativo\":false,\"versao\":0}"))
                .andExpect(status().isOk());
        verify(users).situacao(eq(2L),any(),eq(1L));
    }
    @Test void sessaoRevogadaNaoAcessaApi() throws Exception {
        when(users.sessaoValida(admin)).thenReturn(false);
        mvc.perform(get("/api/auth/me").with(user(admin))).andExpect(status().isUnauthorized());
    }
    @Test void identidadeNaoIncluiSenha() throws Exception {
        mvc.perform(get("/api/auth/me").with(user(medico))).andExpect(status().isOk()).andExpect(jsonPath("$.medicoId").value(10))
                .andExpect(jsonPath("$.senhaHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
    }
    @Test void csrfDisponivelAntesDoLogin() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
    @Test void adminNaoEntraEmLoopAoAbrirPortalExclusivoDoMedico() throws Exception {
        mvc.perform(get("/portal.html").with(user(admin))).andExpect(status().isForbidden());
    }

    @org.springframework.web.bind.annotation.RestController
    static class OperacaoProbe {
        @org.springframework.web.bind.annotation.GetMapping({"/api/hospitais", "/api/medicos", "/api/conciliacoes/teste", "/api/solicitacoes-medicos/teste"})
        public String consultar() { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping({"/api/hospitais", "/api/medicos", "/api/conciliacoes/teste"})
        public String operar() { return "ok"; }
    }
    @Test void funcionarioOperaEGerenciaSomenteAcessosMedicos() throws Exception {
        var funcionario=new UsuarioAutenticado(3,"funcionario@test.local","Funcionario","hash","OPERADOR",null,true,false,0);
        for (var url: java.util.List.of("/api/hospitais","/api/medicos","/api/conciliacoes/teste")) {
            mvc.perform(get(url).with(user(funcionario))).andExpect(status().isOk());
            mvc.perform(post(url).with(user(funcionario)).with(csrf())).andExpect(status().isOk());
        }
        var contaMedico=new UsuarioService.Usuario(2,"medico@test.local","Medico","MEDICO",10L,true,0);
        var contaAdmin=new UsuarioService.Usuario(1,"admin@test.local","Admin","ADMIN",null,true,0);
        when(users.listar()).thenReturn(java.util.List.of(contaMedico,contaAdmin));
        when(users.buscar(2)).thenReturn(contaMedico);
        when(users.buscar(1)).thenReturn(contaAdmin);
        mvc.perform(get("/api/usuarios").with(user(funcionario))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].perfil").value("MEDICO"));
        mvc.perform(post("/api/usuarios").with(user(funcionario)).with(csrf()).contentType("application/json")
            .content("{\"nome\":\"Medico\",\"email\":\"novo@test.local\",\"senha\":\"SenhaTesteSegura12\",\"perfil\":\"MEDICO\",\"medicoId\":10}"))
            .andExpect(status().isCreated());
        mvc.perform(put("/api/usuarios/2/situacao").with(user(funcionario)).with(csrf()).contentType("application/json")
            .content("{\"ativo\":false,\"versao\":0}")).andExpect(status().isOk());
        mvc.perform(post("/api/usuarios/2/senha").with(user(funcionario)).with(csrf()).contentType("application/json")
            .content("{\"senha\":\"SenhaTesteSegura12\",\"versao\":0}")).andExpect(status().isNoContent());
        for(var perfil:java.util.List.of("ADMIN","DEV","OPERADOR"))
            mvc.perform(post("/api/usuarios").with(user(funcionario)).with(csrf()).contentType("application/json")
                .content("{\"nome\":\"Tecnico\",\"email\":\"novo@test.local\",\"senha\":\"SenhaTesteSegura12\",\"perfil\":\""+perfil+"\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/1/situacao").with(user(funcionario)).with(csrf()).contentType("application/json")
            .content("{\"ativo\":false,\"versao\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios/1/senha").with(user(funcionario)).with(csrf()).contentType("application/json")
            .content("{\"senha\":\"SenhaTesteSegura12\",\"versao\":0}")).andExpect(status().isForbidden());
        verify(users,never()).criar(argThat(n->!n.perfil().equals("MEDICO")),any());
        verify(users,never()).situacao(eq(1L),any(),anyLong());
        verify(users,never()).redefinir(eq(1L),any(),any());
    }
    @Test void medicoNaoAcessaHelpDeskNemAnexosOuScripts() throws Exception {
        for(var url:java.util.List.of("/api/chamados","/api/chamados/1","/api/chamados/tipos","/api/chamados/atendentes","/api/chamados/1/anexos/1"))
            mvc.perform(get(url).with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(post("/api/chamados").with(user(medico)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/chamados/1/mensagens").with(user(medico)).with(csrf())).andExpect(status().isForbidden());
        for(var url:java.util.List.of("/chamados.html","/chamados.js","/chamados-widget.js","/chamados-widget.css"))
            mvc.perform(get(url).with(user(medico))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/portal.html"));
    }
    @Test void tecnicoLegadoMantemAcessoCompleto() throws Exception {
        var dev=new UsuarioAutenticado(4,"dev@test.local","Tecnico","hash","DEV",null,true,false,0);
        mvc.perform(get("/api/usuarios").with(user(dev))).andExpect(status().isOk());
        mvc.perform(post("/api/hospitais").with(user(dev)).with(csrf())).andExpect(status().isOk());
    }
}
