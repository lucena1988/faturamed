package br.com.faturamed.seguranca;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemonstrativoMedicoController.class)
@Import(SegurancaConfig.class)
class DemonstrativoMedicoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DemonstrativoMedicoService administracao;
    @MockitoBean ExtratoMedicoService portal;
    @MockitoBean ExportadorDemonstrativoPdf pdf;
    @MockitoBean UsuarioService users;
    private final UsuarioAutenticado medico=new UsuarioAutenticado(7,"m@test.local","Ana","hash","MEDICO",10L,true,false,0);
    private final UsuarioAutenticado admin=new UsuarioAutenticado(8,"a@test.local","Admin","hash","ADMIN",null,true,false,0);
    @BeforeEach void configurar(){when(users.sessaoValida(any())).thenReturn(true);}
    @Test void exigeSessaoESeparaPapeis() throws Exception {
        mvc.perform(get("/api/medico/relatorios/1/demonstrativo.pdf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conciliacoes/visitas/1/demonstrativo.pdf?medicoId=10").with(user(medico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/medico/relatorios/1/demonstrativo.pdf").with(user(admin))).andExpect(status().isForbidden());verifyNoInteractions(portal,administracao,pdf);
    }
    @Test void medicoIgnoraCadastroInformadoPorParametroEUsaFiltrosDoExtrato() throws Exception {
        var filtros=new ExtratoMedicoService.Filtros(LocalDate.of(2026,1,1),null,null,null);var exemplo=DemonstrativoMedicoTest.exemplo(1,false);
        when(portal.buscar(1,filtros,medico)).thenReturn(exemplo);when(pdf.exportar(exemplo)).thenReturn("%PDF-teste".getBytes());
        mvc.perform(get("/api/medico/relatorios/1/demonstrativo.pdf?medicoId=99&visitaDe=2026-01-01").with(user(medico))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition","attachment; filename=demonstrativo-medico-1.pdf"));
        verify(portal).buscar(1,filtros,medico);verifyNoInteractions(administracao);
    }
    @Test void administradorEscolheCadastroExplicitamente() throws Exception {
        var filtros=new ExtratoMedicoService.Filtros(null,null,null,LocalDate.of(2026,2,28));var exemplo=DemonstrativoMedicoTest.exemplo(1,false);
        when(administracao.buscar(1,10,filtros)).thenReturn(exemplo);when(pdf.exportar(exemplo)).thenReturn("%PDF-teste".getBytes());
        mvc.perform(get("/api/conciliacoes/visitas/1/demonstrativo.pdf?medicoId=10&recebimentoAte=2026-02-28").with(user(admin))).andExpect(status().isOk());verify(administracao).buscar(1,10,filtros);verifyNoInteractions(portal);
    }
    @Test void cadastroOuDataInvalidosRetornam400() throws Exception {
        mvc.perform(get("/api/conciliacoes/visitas/1/demonstrativo.pdf").with(user(admin))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/medico/relatorios/1/demonstrativo.pdf?visitaDe=15/01/2026").with(user(medico))).andExpect(status().isBadRequest());verifyNoInteractions(portal,administracao,pdf);
    }
}
