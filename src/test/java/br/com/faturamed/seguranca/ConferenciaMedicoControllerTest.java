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

@WebMvcTest(ConferenciaMedicoController.class)
@Import(SegurancaConfig.class)
class ConferenciaMedicoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConferenciaMedicoService service;
    @MockitoBean UsuarioService users;
    private final UsuarioAutenticado medico=new UsuarioAutenticado(7,"medico@test.local","Ana","hash","MEDICO",10L,true,false,0);
    private final UsuarioAutenticado admin=new UsuarioAutenticado(8,"admin@test.local","Admin","hash","ADMIN",null,true,false,0);
    @BeforeEach void configurar(){when(users.sessaoValida(any())).thenReturn(true);}
    @Test void pedidoUsaUsuarioAutenticadoENaoMedicoInformadoNoJson() throws Exception {
        mvc.perform(post("/api/medico/relatorios/1/solicitacoes").with(user(medico)).with(csrf()).contentType("application/json").content("{\"publicacao\":9,\"linha\":2,\"motivo\":\"Conferir visita\",\"medicoId\":99}")) .andExpect(status().isOk());
        verify(service).solicitar(1,new ConferenciaMedicoService.Pedido(9,2,"Conferir visita"),medico);
    }
    @Test void cienciaERespostaExigemCsrf() throws Exception {
        mvc.perform(post("/api/medico/relatorios/1/ciencia").with(user(medico)).contentType("application/json").content("{\"publicacao\":9}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitacoes-medicos/1/resposta").with(user(admin)).contentType("application/json").content("{\"resposta\":\"Conferido\"}")) .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void linkDiretoDePedidoUsaCadastroDaSessao() throws Exception {
        mvc.perform(get("/api/medico/relatorios/1/solicitacoes/3?medicoId=99").with(user(medico))).andExpect(status().isOk());
        verify(service).pedidoDoMedico(1,10,3);
        mvc.perform(get("/api/solicitacoes-medicos/3").with(user(medico))).andExpect(status().isForbidden());
    }
    @Test void validaPublicacaoLinhaEMotivo() throws Exception {
        for(String dados:new String[]{"{}","{\"publicacao\":0,\"linha\":2,\"motivo\":\"Conferir\"}","{\"publicacao\":9,\"linha\":0,\"motivo\":\"Conferir\"}","{\"publicacao\":9,\"linha\":2,\"motivo\":\"abc\"}"})
            mvc.perform(post("/api/medico/relatorios/1/solicitacoes").with(user(medico)).with(csrf()).contentType("application/json").content(dados)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void adminRespondeComAtorDaSessaoMasNaoOperaComoMedico() throws Exception {
        mvc.perform(post("/api/solicitacoes-medicos/3/resposta").with(user(admin)).with(csrf()).contentType("application/json").content("{\"resposta\":\"Conferido no hospital\"}")) .andExpect(status().isOk());
        verify(service).responder(3,new ConferenciaMedicoService.Resposta("Conferido no hospital"),admin);
        mvc.perform(post("/api/medico/relatorios/1/ciencia").with(user(admin)).with(csrf()).contentType("application/json").content("{\"publicacao\":9}")) .andExpect(status().isForbidden());
    }
}
