package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.chat.ChatService;
import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

/** Service Desk: etapas do Kanban por perfil, atribuicao, cancelamento, avaliacao, mensagens, anexos e SLA (sem banco). */
@SuppressWarnings("unchecked")
class ChamadoServiceFluxosTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatService chat = mock(ChatService.class);
    private final ChamadoService service = spy(new ChamadoService(jdbc));
    private final UsuarioAutenticado admin = new UsuarioAutenticado(1, "a@x", "Admin", "h", "ADMIN", null, true, false, 0);
    private final UsuarioAutenticado dev = new UsuarioAutenticado(2, "d@x", "Dev", "h", "DEV", null, true, false, 0);
    private final UsuarioAutenticado operador = new UsuarioAutenticado(3, "o@x", "Operador", "h", "OPERADOR", null, true, false, 0);
    private final UsuarioAutenticado medico = new UsuarioAutenticado(4, "m@x", "Medico", "h", "MEDICO", 9L, true, false, 0);

    ChamadoServiceFluxosTest() {
        service.setChat(chat);
        when(jdbc.queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), any(Object[].class))).thenReturn(1L);
    }

    private ChamadoService.ChamadoItem chamado(long id, long solicitante, String perfilSolicitante, String status, String fila, String prioridade, Long responsavel, boolean bloqueado) {
        var c = new ChamadoService.ChamadoItem(id, "CH-2026-000" + id, solicitante, "Solicitante", "s@x", perfilSolicitante, "DUVIDA", "Duvida",
                fila, prioridade, status, "Erro na planilha", "desc", null, responsavel, responsavel == null ? null : "Resp", 24,
                null, null, null, null, null, null, null, null, null, null, null, null, 0, "NO_PRAZO", 0, 0, 0, bloqueado);
        when(jdbc.query(contains("where c.id = ?"), any(RowMapper.class), eq(id))).thenReturn(List.of(c));
        doReturn(null).when(service).detalhar(eq(id), any());
        return c;
    }
    private String eventoRegistrado() {
        var conteudo = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, atLeastOnce()).queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), any(), any(), any(), any(), any(), conteudo.capture());
        return String.valueOf(conteudo.getValue());
    }

    // ------------------------------------------------------------------ visibilidade
    @Test
    void chamadoInexistenteEVisibilidadePorPerfil() {
        when(jdbc.query(contains("where c.id = ?"), any(RowMapper.class), eq(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.baixarAnexo(99, 1, admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        chamado(10, 7, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, false);
        assertThatThrownBy(() -> service.baixarAnexo(10, 1, medico)).isInstanceOf(AccessDeniedException.class);
        chamado(11, 7, "ADMIN", "ABERTO", "DEV", "MEDIA", null, false);
        assertThatThrownBy(() -> service.baixarAnexo(11, 1, operador)).isInstanceOf(AccessDeniedException.class);
        when(jdbc.queryForList(contains("from chamado_anexo"), eq(1L), eq(10L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.baixarAnexo(10, 1, operador)).isInstanceOf(RecursoNaoEncontradoException.class); // operador ve chamado de operador
        when(jdbc.queryForList(contains("from chamado_anexo"), eq(2L), eq(10L))).thenReturn(List.of(Map.of("nome_arquivo", "a.pdf", "content_type", "application/pdf", "conteudo", new byte[]{1})));
        assertThat(service.baixarAnexo(10, 2, admin).nome()).isEqualTo("a.pdf");
    }

    // ------------------------------------------------------------------ status (Kanban)
    @Test
    void canceladoNaoMudaEStatusOuFilaOuPrioridadeInvalidos() {
        chamado(20, 7, "OPERADOR", "CANCELADO", "ADMIN", "MEDIA", null, false);
        assertThatThrownBy(() -> service.atualizarStatus(20, new ChamadoService.AlteracaoStatus("ABERTO", null, null, null, null, null), admin)).hasMessageContaining("cancelados");
        chamado(21, 7, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, false);
        assertThatThrownBy(() -> service.atualizarStatus(21, new ChamadoService.AlteracaoStatus("ARQUIVADO", null, null, null, null, null), admin)).hasMessageContaining("Status invalido");
        assertThatThrownBy(() -> service.atualizarStatus(21, new ChamadoService.AlteracaoStatus("CANCELADO", null, null, null, null, null), admin)).hasMessageContaining("fluxo de cancelamento");
        assertThatThrownBy(() -> service.atualizarStatus(21, new ChamadoService.AlteracaoStatus(null, "FINANCEIRO", null, null, null, null), admin)).hasMessageContaining("Fila");
        assertThatThrownBy(() -> service.atualizarStatus(21, new ChamadoService.AlteracaoStatus(" ", null, "URGENTE", null, null, null), admin)).hasMessageContaining("Prioridade");
    }

    @Test
    void solicitanteSoConfirmaOuReabreOProprioChamadoResolvido() {
        chamado(22, 3, "OPERADOR", "RESOLVIDO", "ADMIN", "MEDIA", 1L, false);
        assertThatThrownBy(() -> service.atualizarStatus(22, new ChamadoService.AlteracaoStatus("ABERTO", null, null, null, null, null), operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.atualizarStatus(22, new ChamadoService.AlteracaoStatus("FECHADO", "DEV", null, null, null, null), operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.atualizarStatus(22, new ChamadoService.AlteracaoStatus("FECHADO", null, null, null, true, null), operador)).isInstanceOf(AccessDeniedException.class);
        service.atualizarStatus(22, new ChamadoService.AlteracaoStatus("fechado", null, null, null, null, null), operador);
        assertThat(eventoRegistrado()).contains("Status alterado de Resolvido para Fechado");
    }

    @Test
    void atendenteAssumeRedirecionaPriorizaEBloqueia() {
        chamado(23, 3, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, false);
        service.atualizarStatus(23, new ChamadoService.AlteracaoStatus("EM_ATENDIMENTO", "dev", "alta", null, true, "Analisando", true), dev);
        String evento = eventoRegistrado();
        assertThat(evento).contains("Status alterado de Aberto (Triagem) para Em Atendimento", "marcado como Bloqueado",
                "Redirecionado da fila ADMIN para a fila DEV", "Prioridade alterada para ALTA", "Atribuido para Dev", "Nota: Analisando");
    }

    @Test
    void emAtendimentoSemResponsavelAtribuiQuemMoveuEResolvidoTiraBloqueio() {
        chamado(24, 3, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, true);
        service.atualizarStatus(24, new ChamadoService.AlteracaoStatus("EM_ATENDIMENTO", null, null, null, null, null), admin);
        assertThat(eventoRegistrado()).contains("Atribuido para Admin");
        chamado(25, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, true);
        service.atualizarStatus(25, new ChamadoService.AlteracaoStatus("RESOLVIDO", null, null, null, null, null), admin);
        assertThat(eventoRegistrado()).contains("Bloqueio do chamado removido");
    }

    @Test
    void escolherOuRemoverResponsavel() {
        chamado(26, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        doReturn(List.of(new ChamadoService.Atendente(2, "Dev", "d@x", "DEV"))).when(service).listarAtendentes(any());
        assertThatThrownBy(() -> service.atualizarStatus(26, new ChamadoService.AlteracaoStatus(null, null, null, 77L, null, null), admin)).hasMessageContaining("Responsavel");
        service.atualizarStatus(26, new ChamadoService.AlteracaoStatus(null, null, null, 2L, null, null), admin);
        assertThat(eventoRegistrado()).contains("Atribuido para Dev");
        chamado(27, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        service.atualizarStatus(27, new ChamadoService.AlteracaoStatus(null, null, null, 0L, null, null), admin);
        assertThat(eventoRegistrado()).contains("Responsavel removido");
    }

    @Test
    void pedirRetornoAvisaOSolicitantePeloChat() {
        chamado(28, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        when(jdbc.queryForList(contains("insert into chamado_aviso"), eq(Long.class), any(Object[].class))).thenReturn(List.of(3L));
        service.atualizarStatus(28, new ChamadoService.AlteracaoStatus("AGUARDANDO_USUARIO", null, null, null, null, null), admin);
        verify(chat).avisarSistema(eq(3L), contains("pediu seu retorno no chamado CH-2026-00028"));
    }

    @Test
    void semMudancaNaoRegistraEvento() {
        chamado(29, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        service.atualizarStatus(29, new ChamadoService.AlteracaoStatus(null, null, null, null, null, " "), admin);
        verify(jdbc, never()).queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), any(Object[].class));
    }

    // ------------------------------------------------------------------ cancelamento
    @Test
    void cancelamentoPeloSolicitanteAvisaOSuporteEPeloSuporteAvisaOSolicitante() {
        chamado(30, 3, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", 1L, false);
        when(jdbc.queryForList(contains("insert into chamado_aviso"), eq(Long.class), any(Object[].class))).thenReturn(List.of(1L));
        assertThatThrownBy(() -> service.cancelar(30, new ChamadoService.CancelarChamado("OUTRO", "x".repeat(2001)), operador)).hasMessageContaining("2000");
        service.cancelar(30, new ChamadoService.CancelarChamado("duplicado", "Ja existe o 29"), operador);
        assertThat(eventoRegistrado()).contains("cancelado por Operador (Funcionario FaturaMed)", "Chamado duplicado", "Ja existe o 29");
        verify(chat).avisarSistema(eq(1L), contains("foi cancelado"));
        chamado(31, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        service.cancelar(31, new ChamadoService.CancelarChamado("NAO_REPRODUZIVEL", "Sem retorno"), dev);
        assertThat(eventoRegistrado()).contains("Administrador tecnico", "Nao reproduzivel");
    }

    // ------------------------------------------------------------------ avaliacao
    @Test
    void soOSolicitanteAvaliaChamadoResolvido() {
        chamado(40, 3, "OPERADOR", "RESOLVIDO", "ADMIN", "MEDIA", 1L, false);
        assertThatThrownBy(() -> service.avaliar(40, new ChamadoService.Avaliacao(5, null), admin)).isInstanceOf(AccessDeniedException.class);
        chamado(41, 3, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        assertThatThrownBy(() -> service.avaliar(41, new ChamadoService.Avaliacao(5, null), operador)).hasMessageContaining("Resolvido ou Fechado");
        service.avaliar(40, new ChamadoService.Avaliacao(4, " Rapido "), operador);
        assertThat(eventoRegistrado()).contains("★★★★☆ (4/5)", "\"Rapido\"");
        verify(jdbc).update(contains("set nota_avaliacao = ?"), eq(4), eq("Rapido"), eq(40L));
        chamado(42, 3, "OPERADOR", "FECHADO", "ADMIN", "MEDIA", 1L, false);
        service.avaliar(42, new ChamadoService.Avaliacao(5, null), operador);
        verify(jdbc).update(contains("set nota_avaliacao = ?"), eq(5), isNull(), eq(42L));
    }

    // ------------------------------------------------------------------ mensagens
    @Test
    void mensagemEmChamadoEncerradoOuVaziaERecusada() {
        chamado(50, 3, "OPERADOR", "FECHADO", "ADMIN", "MEDIA", 1L, false);
        assertThatThrownBy(() -> service.adicionarMensagem(50, "oi", null, operador)).isInstanceOf(ConflitoRevisaoException.class);
        chamado(51, 3, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, false);
        assertThatThrownBy(() -> service.adicionarMensagem(51, " ", List.of(), operador)).hasMessageContaining("Digite uma mensagem");
        assertThatThrownBy(() -> service.adicionarMensagem(51, "x".repeat(4001), null, operador)).hasMessageContaining("4000");
    }

    @Test
    void primeiraRespostaDoSuporteColocaEmAtendimento() throws Exception {
        chamado(52, 3, "OPERADOR", "ABERTO", "ADMIN", "MEDIA", null, false);
        when(jdbc.queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), any(Object[].class))).thenReturn(500L);
        service.adicionarMensagem(52, "Vou verificar", null, admin);
        verify(jdbc).update(contains("responsavel_id = coalesce(responsavel_id, ?)"), eq(1L), eq("Admin"), eq(52L));
    }

    @Test
    void respostaDoSolicitanteAguardandoVoltaParaAtendimentoComAnexo() throws Exception {
        chamado(53, 3, "OPERADOR", "AGUARDANDO_USUARIO", "ADMIN", "MEDIA", 1L, false);
        when(jdbc.queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), any(Object[].class))).thenReturn(501L);
        var pdf = new MockMultipartFile("arquivos", "print.pdf", "application/pdf", "%PDF-1.4 conteudo".getBytes(StandardCharsets.US_ASCII));
        service.adicionarMensagem(53, null, List.of(pdf), operador);
        verify(jdbc).queryForObject(contains("insert into chamado_mensagem"), eq(Long.class), eq(53L), eq(3L), eq("Operador"), eq("OPERADOR"), eq("MENSAGEM"), eq("Anexo enviado (1 arquivo(s))."));
        verify(jdbc).update(contains("insert into chamado_anexo"), eq(53L), eq(501L), eq(3L), eq("Operador"), eq("print.pdf"), eq("application/pdf"), anyInt(), any());
        verify(jdbc).update(contains("set status = 'EM_ATENDIMENTO'"), eq(53L));
    }

    @Test
    void mensagemComumSoAtualizaOChamado() throws Exception {
        chamado(54, 7, "OPERADOR", "EM_ATENDIMENTO", "ADMIN", "MEDIA", 1L, false);
        service.adicionarMensagem(54, "Atualizacao", null, operador);
        verify(jdbc).update("update chamado_suporte set atualizado_em = now(), versao = versao + 1 where id = ?", 54L);
    }

    // ------------------------------------------------------------------ anexos
    @Test
    void anexosValidadosPeloConteudoNaoSoPelaExtensao() throws Exception {
        assertThat(ChamadoService.validarArquivos(null)).isEmpty();
        byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1};
        byte[] jpg = {(byte) 255, (byte) 216, (byte) 255, 1};
        byte[] webp = "RIFF0000WEBPxx".getBytes(StandardCharsets.US_ASCII);
        var ok = ChamadoService.validarArquivos(java.util.Arrays.asList(
                new MockMultipartFile("a", "C:\\fotos\\tela.png", "image/png", png), new MockMultipartFile("a", "f.jpeg", "image/jpeg", jpg),
                new MockMultipartFile("a", "f.webp", "image/webp", webp), new MockMultipartFile("a", "vazio.png", "image/png", new byte[0]), null));
        assertThat(ok).extracting(ChamadoService.ArquivoAnexo::contentType).containsExactly("image/png", "image/jpeg", "image/webp");
        assertThat(ok.get(0).nome()).isEqualTo("tela.png");
        assertThatThrownBy(() -> ChamadoService.validarArquivos(List.of(new MockMultipartFile("a", "virus.png", "image/png", "MZ executavel".getBytes()))))
                .hasMessageContaining("Formato invalido");
        assertThatThrownBy(() -> ChamadoService.validarArquivos(List.of(new MockMultipartFile("a", "x".repeat(181) + ".pdf", "application/pdf", "%PDF-".getBytes()))))
                .hasMessageContaining("Nome do arquivo");
        assertThatThrownBy(() -> ChamadoService.validarArquivos(List.of(new MockMultipartFile("a", "grande.pdf", "application/pdf", new byte[ChamadoService.LIMITE_ARQUIVO + 1]))))
                .hasMessageContaining("5 MB");
        List<MultipartFile> seis = new ArrayList<>();
        for (int i = 0; i < 6; i++) seis.add(new MockMultipartFile("a", i + ".png", "image/png", png));
        assertThatThrownBy(() -> ChamadoService.validarArquivos(seis)).hasMessageContaining("ate 5 anexos");
    }

    // ------------------------------------------------------------------ criacao e SLA
    @Test
    void criarValidaTituloDescricaoECategoria() {
        assertThatThrownBy(() -> service.criar("DUVIDA", " ", "d", null, null, operador)).hasMessageContaining("titulo");
        assertThatThrownBy(() -> service.criar("DUVIDA", "t", "x".repeat(4001), null, null, operador)).hasMessageContaining("Descreva");
        doReturn(List.of()).when(service).listarTipos();
        assertThatThrownBy(() -> service.criar("NADA", "t", "d", null, null, operador)).hasMessageContaining("categoria");
    }

    @Test
    void criarGeraProtocoloEDistribuiNaFilaDoTipo() throws Exception {
        doReturn(List.of(new ChamadoService.TipoSla("BUG", "Erro", "", "DEV", "ALTA", 8, true, 1))).when(service).listarTipos();
        when(jdbc.queryForObject(contains("insert into chamado_suporte"), eq(Long.class), any(Object[].class))).thenReturn(12L);
        doReturn(null).when(service).detalhar(eq(12L), any());
        service.criar(" bug ", "Tela quebrada", "Ao abrir o lote", "x".repeat(300), null, medico);
        verify(jdbc).update(eq("update chamado_suporte set protocolo = ? where id = ?"), matches("CH-\\d{4}-0012"), eq(12L));
        assertThat(eventoRegistrado()).contains("Desenvolvimento (DEV)", "SLA: 8h", "(Medico)");
    }

    @Test
    void slaSoPelaEquipeEComValoresValidos() {
        assertThatThrownBy(() -> service.atualizarSla("BUG", new ChamadoService.AtualizarSla(8, "DEV", "ALTA"), operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.atualizarSla("BUG", new ChamadoService.AtualizarSla(8, "RH", "ALTA"), admin)).hasMessageContaining("Fila padrao");
        assertThatThrownBy(() -> service.atualizarSla("BUG", new ChamadoService.AtualizarSla(8, "DEV", "MAXIMA"), admin)).hasMessageContaining("Prioridade padrao");
        assertThatThrownBy(() -> service.atualizarSla("BUG", new ChamadoService.AtualizarSla(0, "DEV", "ALTA"), admin)).hasMessageContaining("entre 1 e 720");
        when(jdbc.update(contains("update chamado_tipo_sla"), eq(8), eq("DEV"), eq("ALTA"), eq("X"))).thenReturn(0);
        assertThatThrownBy(() -> service.atualizarSla("X", new ChamadoService.AtualizarSla(8, "dev", "alta"), admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.update(contains("update chamado_tipo_sla"), eq(8), eq("DEV"), eq("ALTA"), eq("BUG"))).thenReturn(1);
        var tipo = new ChamadoService.TipoSla("BUG", "Erro", "", "DEV", "ALTA", 8, true, 1);
        doReturn(List.of(tipo)).when(service).listarTipos();
        assertThat(service.atualizarSla("BUG", new ChamadoService.AtualizarSla(8, "DEV", "ALTA"), admin)).isEqualTo(tipo);
    }

    @Test
    void rotulosDosMotivos() {
        assertThat(ChamadoService.rotuloMotivoCancelamento("ABERTURA_INCORRETA")).contains("Abertura incorreta");
        assertThat(ChamadoService.rotuloMotivoCancelamento("CANCELADO_SOLICITANTE")).isEqualTo("Cancelado pelo solicitante");
        assertThat(ChamadoService.rotuloMotivoCancelamento("OUTRO")).isEqualTo("Outro motivo");
        assertThat(ChamadoService.rotuloMotivoCancelamento("NOVO")).isEqualTo("NOVO");
        assertThat(ChamadoService.rotuloMotivoCancelamento(null)).isNull();
    }
}
