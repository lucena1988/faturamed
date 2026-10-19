package br.com.faturamed.seguranca;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacaoController.class)
@Import(SegurancaConfig.class)
class NotificacaoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificacaoService service;
    @MockitoBean UsuarioService users;
    private final UsuarioAutenticado medico=new UsuarioAutenticado(7,"m@test.local","Ana","hash","MEDICO",10L,true,false,0);
    private final UsuarioAutenticado admin=new UsuarioAutenticado(8,"a@test.local","Admin","hash","ADMIN",null,true,false,0);
    @BeforeEach void configurar(){when(users.sessaoValida(any())).thenReturn(true);}
    @Test void exigeLogin() throws Exception {mvc.perform(get("/api/auth/notificacoes")).andExpect(status().isUnauthorized());}
    @Test void consultaUsaSessaoMesmoComParametroDeOutroUsuario() throws Exception {
        mvc.perform(get("/api/auth/notificacoes?usuario=99&medicoId=88&naoLidas=true&pagina=2").with(user(medico))).andExpect(status().isOk());
        verify(service).listar(medico,true,2);
    }
    @Test void leituraExigeCsrf() throws Exception {
        mvc.perform(post("/api/auth/notificacoes/LIBERACAO/9/leitura").with(user(medico))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void leituraDoMedicoEAdministradorUsaAtorAutenticado() throws Exception {
        mvc.perform(post("/api/auth/notificacoes/LIBERACAO/9/leitura").with(user(medico)).with(csrf())).andExpect(status().isNoContent());verify(service).ler(medico,"LIBERACAO",9);
        mvc.perform(post("/api/auth/notificacoes/PEDIDO/3/leitura").with(user(admin)).with(csrf())).andExpect(status().isNoContent());verify(service).ler(admin,"PEDIDO",3);
    }
    @Test void rejeitaPaginaNaoNumerica() throws Exception {
        mvc.perform(get("/api/auth/notificacoes?pagina=abc").with(user(medico))).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
}
