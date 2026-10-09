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

@WebMvcTest(AcessoController.class)
@Import(SegurancaConfig.class)
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
}
