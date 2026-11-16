package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Painel da producao, controller dos lotes e registro de acesso a dados sensiveis (sem banco). */
@SuppressWarnings("unchecked")
class ProducaoPainelEControllerTest {
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    // ------------------------------------------------------------------ painel
    @Test
    void painelContaPrazosVencidosEDoDiaEZeraValoresNulos() {
        var jdbc = mock(JdbcTemplate.class);
        var prazo = mock(PrazoConferencia.class);
        when(prazo.dias()).thenReturn(5);
        var hoje = LocalDate.now();
        when(jdbc.query(contains("f.status='AGUARDANDO' and f.prazo_conferencia"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                new PainelProducaoService.Atencao(1, "H", BigDecimal.TEN, hoje.minusDays(3), -2, true),
                new PainelProducaoService.Atencao(2, "H", BigDecimal.TEN, hoje, 0, false),
                new PainelProducaoService.Atencao(3, "H", BigDecimal.TEN, hoje.plusDays(5), 3, false)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForObject(contains("from producao_lote l where l.status='RASCUNHO'"), eq(Integer.class), any(Object[].class))).thenReturn(4);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class))).thenReturn(null);
        var p = new PainelProducaoService(jdbc, prazo).painel(null);
        assertThat(p.tarefas().atrasados()).isEqualTo(1);
        assertThat(p.tarefas().venceHoje()).isEqualTo(1);
        assertThat(p.tarefas().lotesRascunho()).isEqualTo(4);
        assertThat(p.tarefas().contestacoes()).isZero();
        assertThat(p.mes().fechadoMes()).isEqualByComparingTo("0");
        assertThat(p.prazoDias()).isEqualTo(5);
    }

    @Test
    void painelFiltradoPorHospitalPassaOHospitalNasConsultas() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        new PainelProducaoService(jdbc, mock(PrazoConferencia.class)).painel(7L);
        verify(jdbc).queryForObject(contains("l.status='RASCUNHO' and l.hospital_id=?"), eq(Integer.class), eq(7L));
        verify(jdbc).query(contains("and h.id=?"), any(RowMapper.class), any(), any(), eq(7L));
    }

    // ------------------------------------------------------------------ controller
    private final ProducaoService service = mock(ProducaoService.class);
    private final ExtratorEtiquetasOcr ocr = mock(ExtratorEtiquetasOcr.class);
    private final HistoricoProducao historico = mock(HistoricoProducao.class);
    private final RegistroAcesso acessos = mock(RegistroAcesso.class);
    private ProducaoController controller() {
        var c = new ProducaoController(service, ocr, historico);
        ReflectionTestUtils.setField(c, "acessos", acessos);
        return c;
    }
    private final Principal ana = () -> "ana@x";

    @Test
    void abrirLoteEFotoFicamRegistradosComoAcessoADadosSensiveis() {
        var c = controller();
        var lote = new ProducaoService.Lote(5, 1, "H", List.of(), List.of(), LocalDate.now(), "MANHA", "", "", "", "RASCUNHO", true, 0, List.of());
        when(service.buscar(5)).thenReturn(lote);
        assertThat(c.buscar(5)).isSameAs(lote);
        verify(acessos).registrar("ABRIU_LOTE", "LOTE", 5L, "0 etiqueta(s)");
        when(service.foto(5)).thenReturn(Map.of("foto", new byte[]{1}, "foto_tipo", "image/png"));
        var resp = c.foto(5);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        verify(acessos).registrar("VIU_FOTO", "LOTE", 5L, "Foto da folha");
    }

    @Test
    void revelarAcessosEHistoricoSoParaLoteOuFechamento() {
        var c = controller();
        assertThatThrownBy(() -> c.revelar(new ProducaoController.Revelar("paciente", 1L))).hasMessageContaining("Entidade invalida");
        assertThatThrownBy(() -> c.revelar(new ProducaoController.Revelar(null, 1L))).hasMessageContaining("Entidade invalida");
        c.revelar(new ProducaoController.Revelar("lote", 1L));
        verify(acessos).registrar(eq("REVELOU_DADOS"), eq("LOTE"), eq(1L), anyString());
        assertThatThrownBy(() -> c.acessos("medico", 1)).hasMessageContaining("Entidade invalida");
        c.acessos("fechamento", 2); verify(acessos).listar("FECHAMENTO", 2L);
        assertThatThrownBy(() -> c.historico("x", 1)).hasMessageContaining("Historico invalido");
        c.historico("lote", 3); verify(historico).listar("LOTE", 3L);
    }

    @Test
    void crudDeLotesUsaOUsuarioLogado() throws Exception {
        var c = controller();
        var dados = new ProducaoService.Salvar(null, List.of());
        c.criar(dados, ana); verify(service).salvar(null, dados, "ana@x");
        c.atualizar(4, dados, null); verify(service).salvar(4L, dados, null);
        c.status(4, Map.of("status", "CONFERIDO"), ana); verify(service).alterarStatus(4, "CONFERIDO", "ana@x");
        c.excluir(4); verify(service).excluir(4);
        c.listar(null, null, 1L, null, null, "RASCUNHO"); verify(service).listar(null, null, 1L, null, null, "RASCUNHO");
        c.duplicidades(Map.of("atendimentos", List.of("1"), "loteId", 9)); verify(service).duplicidades(List.of("1"), 9L);
        c.duplicidades(Map.of()); verify(service).duplicidades(List.of(), null);
        c.exames(); verify(service).exames();
        c.criarExame(Map.of("nome", "ECG")); verify(service).criarExame("ECG");
    }

    @Test
    void fotoEOcrSoAceitamImagem() throws Exception {
        var c = controller();
        var pdf = new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> c.enviarFoto(1, pdf, ana)).hasMessageContaining("JPG, PNG ou WEBP");
        assertThatThrownBy(() -> c.extrair(new MockMultipartFile("arquivo", "a.jpg", "image/jpeg", new byte[0]))).hasMessageContaining("JPG");
        assertThatThrownBy(() -> c.extrair(new MockMultipartFile("arquivo", "a.jpg", null, new byte[]{1}))).hasMessageContaining("JPG");
        c.enviarFoto(1, new MockMultipartFile("arquivo", "a.jpg", "IMAGE/JPEG", new byte[]{1}), ana);
        verify(service).salvarFoto(eq(1L), any(), eq("image/jpeg"), eq("ana@x"));
        c.extrair(new MockMultipartFile("arquivo", "a.png", "image/png", new byte[]{2}));
        verify(ocr).extrair(any(), eq("image/png"));
        when(ocr.disponivel()).thenReturn(true); when(ocr.motor()).thenReturn("tesseract");
        assertThat(c.ocrStatus()).containsEntry("disponivel", true).containsEntry("motor", "tesseract");
    }

    // ------------------------------------------------------------------ registro de acesso (LGPD)
    @Test
    void registroDeAcessoGuardaUsuarioPerfilIpEDetalheLimitado() {
        var jdbc = mock(JdbcTemplate.class);
        var u = new UsuarioAutenticado(1, "ana@x", "Ana", "h", "OPERADOR", null, true, false, 0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
        var req = new MockHttpServletRequest(); req.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        new RegistroAcesso(jdbc).registrar("ABRIU_LOTE", "LOTE", 5L, "x".repeat(400));
        verify(jdbc).update(contains("insert into acesso_dados"), eq("ana@x"), eq("OPERADOR"), eq("ABRIU_LOTE"), eq("LOTE"), eq(5L), eq("x".repeat(300)), eq("10.0.0.1"));
    }

    @Test
    void registroSemUsuarioNemRequisicaoUsaNulos() {
        var jdbc = mock(JdbcTemplate.class);
        new RegistroAcesso(jdbc).registrar("ROTINA", "LOTE", 1L, null);
        verify(jdbc).update(contains("insert into acesso_dados"), isNull(), isNull(), eq("ROTINA"), eq("LOTE"), eq(1L), eq(""), isNull());
        var req = new MockHttpServletRequest(); req.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        new RegistroAcesso(jdbc).registrar("ROTINA", "LOTE", 2L, "ok");
        verify(jdbc).update(contains("insert into acesso_dados"), isNull(), isNull(), eq("ROTINA"), eq("LOTE"), eq(2L), eq("ok"), eq("127.0.0.1"));
        when(jdbc.query(contains("from acesso_dados"), any(RowMapper.class), eq("LOTE"), eq(1L))).thenReturn(List.of());
        assertThat(new RegistroAcesso(jdbc).listar("LOTE", 1)).isEmpty();
    }
}
