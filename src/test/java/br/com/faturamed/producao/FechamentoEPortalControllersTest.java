package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** Controllers do fechamento (faturamento) e do portal do hospital, sem banco. */
class FechamentoEPortalControllersTest {
    private final FechamentoProducaoService service = mock(FechamentoProducaoService.class);
    private final ExportadorFechamentoProducao exportador = mock(ExportadorFechamentoProducao.class);
    private final RegistroAcesso acessos = mock(RegistroAcesso.class);
    private final PainelProducaoService painel = mock(PainelProducaoService.class);
    private final Principal ana = () -> "ana@x";

    private FechamentoProducaoController fc() {
        var c = new FechamentoProducaoController(service, exportador);
        ReflectionTestUtils.setField(c, "acessos", acessos);
        ReflectionTestUtils.setField(c, "painel", painel);
        return c;
    }

    private static FechamentoProducaoService.Detalhe detalhe(String hospital) {
        var f = mock(FechamentoProducaoService.Fechamento.class);
        when(f.hospital()).thenReturn(hospital);
        when(f.dataInicio()).thenReturn(LocalDate.of(2026, 1, 1));
        when(f.dataFim()).thenReturn(LocalDate.of(2026, 1, 31));
        return new FechamentoProducaoService.Detalhe(f, List.of(), List.of(), List.of());
    }

    @Test
    void endpointsDoFechamentoDelegamAoServico() {
        var c = fc();
        c.painel(1L); verify(painel).painel(1L);
        c.regras(2L); verify(service).regras(2L);
        c.criarRegra(null); verify(service).salvarRegra(null, null);
        c.atualizarRegra(3, null); verify(service).salvarRegra(3L, null);
        c.excluirRegra(3); verify(service).excluirRegra(3);
        c.valoresLote(4); verify(service).valoresLote(4);
        var d1 = LocalDate.of(2026, 1, 1); var d2 = LocalDate.of(2026, 1, 31);
        c.previa(1, d1, d2); verify(service).previa(1, d1, d2);
        c.fechamentos(null); verify(service).fechamentos(null);
        c.fechar(new FechamentoProducaoController.NovoFechamento(1L, d1, d2, "obs"), ana);
        verify(service).fechar(1, d1, d2, "obs", "ana@x");
        c.fecharLotes(new FechamentoProducaoController.EnvioLotes(List.of(5L), "o"), ana);
        verify(service).fecharLotes(eq(List.of(5L)), eq("o"), any());
        c.detalhe(9); verify(service).detalhe(9); verify(acessos).registrar(eq("ABRIU_FECHAMENTO"), eq("FECHAMENTO"), eq(9L), any());
        c.aprovar(9, new FechamentoProducaoController.Aprovacao("Joao", d2), ana); verify(service).aprovar(9, "Joao", d2, "ana@x");
        c.nf(9, new FechamentoProducaoController.NotaFiscal("123", d2), ana); verify(service).registrarNf(9, "123", d2, "ana@x");
        c.cancelar(9, ana); verify(service).cancelar(9, "ana@x");
        c.contestacoes(); verify(service).contestacoes();
        c.resolver(7, new FechamentoProducaoController.Decisao("ACEITA", "ok"), ana); verify(service).resolverContestacao(7, "ACEITA", "ok", "ana@x");
    }

    @Test
    void fecharExigeHospitalEPeriodo() {
        var c = fc();
        var d = LocalDate.now();
        assertThatThrownBy(() -> c.fechar(new FechamentoProducaoController.NovoFechamento(null, d, d, null), ana)).hasMessageContaining("Informe hospital");
        assertThatThrownBy(() -> c.fechar(new FechamentoProducaoController.NovoFechamento(1L, null, d, null), ana)).hasMessageContaining("Informe hospital");
        assertThatThrownBy(() -> c.fechar(new FechamentoProducaoController.NovoFechamento(1L, d, null, null), ana)).hasMessageContaining("Informe hospital");
    }

    @Test
    void anexarNfValidaTipoELimpaNome() throws Exception {
        var c = fc();
        assertThatThrownBy(() -> c.anexarNf(1, new MockMultipartFile("arquivo", "a.exe", "application/octet-stream", new byte[]{1}), ana)).hasMessageContaining("PDF");
        assertThatThrownBy(() -> c.anexarNf(1, new MockMultipartFile("arquivo", "a.pdf", null, new byte[]{1}), ana)).hasMessageContaining("PDF");
        assertThatThrownBy(() -> c.anexarNf(1, new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[0]), ana)).hasMessageContaining("PDF");
        c.anexarNf(1, new MockMultipartFile("arquivo", "nota fiscal#1.PDF", "APPLICATION/PDF", new byte[]{1}), ana);
        verify(service).salvarNfArquivo(eq(1L), any(), eq("nota_fiscal_1.PDF"), eq("application/pdf"), eq("ana@x"));
        c.anexarNf(2, new MockMultipartFile("arquivo", "x".repeat(250) + ".xml", "text/xml", new byte[]{1}), ana);
        verify(service).salvarNfArquivo(eq(2L), any(), eq("x".repeat(200)), eq("text/xml"), eq("ana@x"));
    }

    @Test
    void baixarNfEPlanilhaRegistramAcesso() {
        var c = fc();
        when(service.nfArquivo(3)).thenReturn(Map.of("nf_arquivo_nome", "nf.pdf", "nf_arquivo_tipo", "application/pdf", "nf_arquivo", new byte[]{1}));
        var r = c.baixarNf(3);
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("nf.pdf");
        verify(acessos).registrar("BAIXOU_NF", "FECHAMENTO", 3L, "");
        var d = detalhe("Hospital Sao Luiz");
        when(service.detalhe(4)).thenReturn(d);
        when(exportador.exportar(d)).thenReturn(new byte[]{9});
        var p = c.planilha(4);
        assertThat(p.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("producao-hospital-sao-luiz-2026-01-01-a-2026-01-31.xlsx");
        assertThat(p.getBody()).containsExactly(9);
        verify(acessos).registrar(eq("BAIXOU_PLANILHA"), eq("FECHAMENTO"), eq(4L), anyString());
    }

    // ------------------------------------------------------------------ portal do hospital
    private final HospitalPortalService portal = mock(HospitalPortalService.class);
    private final UsuarioAutenticado u = new UsuarioAutenticado(1, "h@x", "Hosp", "h", "HOSPITAL", null, true, false, 0);

    private HospitalPortalController pc() {
        var c = new HospitalPortalController(portal, exportador);
        ReflectionTestUtils.setField(c, "acessos", acessos);
        when(portal.hospitalDoUsuario(1)).thenReturn(7L);
        return c;
    }

    @Test
    void portalUsaOHospitalDoUsuario() {
        var c = pc();
        when(portal.nomeHospital(7)).thenReturn("H7");
        assertThat(c.eu(u)).containsEntry("hospitalId", 7L).containsEntry("hospital", "H7").containsEntry("nome", "Hosp");
        c.fechamentos(u); verify(portal).listar(7);
        c.abrir(10, u); verify(portal).abrir(10, 7, "Hosp"); verify(acessos).registrar(eq("ABRIU_FECHAMENTO"), eq("FECHAMENTO"), eq(10L), anyString());
        c.conferirLote(10, 5, new HospitalPortalController.Marcar(true), u); verify(portal).conferirLote(10, 5, true, 7, "Hosp");
        when(portal.conferirTodos(10, 7, "Hosp")).thenReturn(3);
        assertThat(c.conferirTodos(10, u)).containsEntry("marcados", 3);
        c.contestar(10, 2, new HospitalPortalController.Contestar(null), u); verify(portal).contestar(10, 2, "", 7, "Hosp");
        c.contestar(10, 2, new HospitalPortalController.Contestar("m"), u); verify(portal).contestar(10, 2, "m", 7, "Hosp");
        c.retirarContestacao(10, 2, u); verify(portal).contestar(10, 2, null, 7, "Hosp");
        c.aprovar(10, u); verify(portal).aprovar(10, 7, "Hosp");
        c.revelar(10, u); verify(portal).detalheParaPlanilha(10, 7); verify(acessos).registrar(eq("REVELOU_DADOS"), eq("FECHAMENTO"), eq(10L), anyString());
    }

    @Test
    void portalBaixaNfEPlanilha() {
        var c = pc();
        when(portal.nfArquivo(10, 7)).thenReturn(Map.of("nf_arquivo_nome", "nf.png", "nf_arquivo_tipo", "image/png", "nf_arquivo", new byte[]{1}));
        assertThat(c.nf(10, u).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("nf.png");
        var d = detalhe("H");
        when(portal.detalheParaPlanilha(11, 7)).thenReturn(d);
        when(exportador.exportar(d)).thenReturn(new byte[]{2});
        assertThat(c.planilha(11, u).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("fechamento-11.xlsx");
        verify(acessos).registrar(eq("BAIXOU_PLANILHA"), eq("FECHAMENTO"), eq(11L), anyString());
    }
}
