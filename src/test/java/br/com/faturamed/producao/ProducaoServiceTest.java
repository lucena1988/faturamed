package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Lotes de producao: validacoes do lote, conferencia, exclusao, foto e exames (sem banco). */
@SuppressWarnings("unchecked")
class ProducaoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HistoricoProducao historico = mock(HistoricoProducao.class);
    private final CriptografiaDados cripto = mock(CriptografiaDados.class);
    private final ProducaoService service = spy(new ProducaoService(jdbc, historico, cripto));
    private static final LocalDate DIA = LocalDate.of(2026, 10, 10);

    private static ProducaoService.Item item(String atendimento, String paciente, Long medico, Long exame, boolean pendente) {
        return new ProducaoService.Item(null, atendimento, "", paciente, "", "", "", "", "", "", "CART", "", "", "", "", "", null, pendente, medico, exame);
    }
    private static ProducaoService.Salvar salvar(String periodo, List<Long> medicos, List<Long> exames, long versao, ProducaoService.Item... itens) {
        return new ProducaoService.Salvar(new ProducaoService.Cabecalho(1L, medicos, exames, DIA, periodo, "", "", "", versao), List.of(itens));
    }
    private ProducaoService.Lote lote(long id, String status, List<Long> exames, ProducaoService.Item... itens) {
        var l = new ProducaoService.Lote(id, 1L, "H", List.of(5L), exames, DIA, "MANHA", "", "", "", status, false, 0L, List.of(itens));
        doReturn(l).when(service).buscar(id);
        return l;
    }

    // ------------------------------------------------------------------ salvar
    @Test
    void validaPeriodoMedicoEEtiquetasForaDoLote() {
        assertThatThrownBy(() -> service.salvar(null, salvar("MADRUGADA", List.of(5L), List.of(), 0), "u")).hasMessageContaining("Periodo invalido");
        assertThatThrownBy(() -> service.salvar(null, salvar("MANHA", List.of(), List.of(), 0), "u")).hasMessageContaining("ao menos um medico");
        assertThatThrownBy(() -> service.salvar(null, salvar("manha", List.of(5L), List.of(7L), 0, item("1234567", "P", 9L, null, false)), "u"))
                .hasMessageContaining("medico fora do lote");
        assertThatThrownBy(() -> service.salvar(null, salvar("manha", List.of(5L), List.of(7L), 0, item("1234567", "P", 5L, 8L, false)), "u"))
                .hasMessageContaining("exame fora do lote");
        assertThatThrownBy(() -> service.salvar(null, salvar("MANHA", List.of(5L), List.of(), 3), "u")).hasMessageContaining("Versao inicial");
        verifyNoInteractions(jdbc);
    }

    @Test
    void novoLoteGravaItensComMedicoEExameUnicosEDadosSensiveisCifrados() {
        when(jdbc.queryForObject(contains("insert into producao_lote"), eq(Long.class), any(Object[].class))).thenReturn(30L);
        when(cripto.cifrar(anyString())).thenAnswer(a -> "enc:" + a.getArgument(0));
        lote(30, "RASCUNHO", List.of(7L));
        service.salvar(null, salvar(" tarde ", List.of(5L), List.of(7L), 0, item("123 4567", "Paciente", null, null, false)), "operador");
        verify(historico).lote(30L, "CRIADA", "Lote criado com 1 etiqueta(s)", "operador");
        verify(jdbc).update("insert into producao_lote_medico(lote_id,medico_id) values(?,?)", 30L, 5L);
        verify(jdbc).update("insert into producao_lote_exame(lote_id,exame_id) values(?,?)", 30L, 7L);
        verify(cripto).cifrar("CART");
        // atendimento sem espacos, origem padrao MANUAL e medico/exame herdados do lote
        verify(jdbc).update(contains("insert into producao_item"), eq(30L), eq(0), eq("1234567"), any(), eq("Paciente"), any(), any(), any(), any(), any(), any(),
                eq("enc:CART"), any(), any(), any(), any(), any(), eq("MANUAL"), eq(false), eq(5L), eq(7L));
    }

    @Test
    void naoAlteraLoteEnviadoEmFechamentoOuDesatualizado() {
        lote(31, "ENVIADO", List.of());
        assertThatThrownBy(() -> service.salvar(31L, salvar("MANHA", List.of(5L), List.of(), 1), "u")).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("fechamento");
        lote(32, "CONFERIDO", List.of());
        when(jdbc.queryForObject(contains("producao_fechamento_item"), eq(Integer.class), eq(32L))).thenReturn(2);
        assertThatThrownBy(() -> service.salvar(32L, salvar("MANHA", List.of(5L), List.of(), 1), "u")).hasMessageContaining("fechamento ativo");
        lote(33, "RASCUNHO", List.of());
        when(jdbc.queryForObject(contains("producao_fechamento_item"), eq(Integer.class), eq(33L))).thenReturn(0);
        when(jdbc.update(contains("update producao_lote set hospital_id"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.salvar(33L, salvar("MANHA", List.of(5L), List.of(), 1), "u")).hasMessageContaining("outro operador");
    }

    @Test
    void alterarLoteRefazItensERegistraQuantidadeAnterior() {
        lote(34, "RASCUNHO", List.of(), item("1234567", "A", 5L, null, false), item("1234568", "B", 5L, null, false));
        when(jdbc.queryForObject(contains("producao_fechamento_item"), eq(Integer.class), eq(34L))).thenReturn(0);
        when(jdbc.update(contains("update producao_lote set hospital_id"), any(Object[].class))).thenReturn(1);
        var origemOcr = new ProducaoService.Item(null, "1234567", "", "A", "", "", "", "", "", "", "", "", "", "", "", "", "ocr", false, 5L, null);
        service.salvar(34L, salvar("MANHA", List.of(5L), List.of(), 1, origemOcr), "u");
        verify(jdbc).update("delete from producao_item where lote_id=?", 34L);
        verify(historico).lote(34L, "ALTERADA", "Lote salvo: 1 etiqueta(s) (antes 2)", "u");
        verify(jdbc).update(contains("insert into producao_item"), eq(34L), eq(0), eq("1234567"), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), eq("OCR"), eq(false), eq(5L), isNull());
    }

    // ------------------------------------------------------------------ status
    @Test
    void alterarStatusValidaStatusFechamentoEEnvio() {
        assertThatThrownBy(() -> service.alterarStatus(40, "ARQUIVADO", "u")).hasMessageContaining("Status invalido");
        lote(40, "RASCUNHO", List.of());
        when(jdbc.queryForObject(contains("select fechamento_id"), eq(Long.class), eq(40L))).thenReturn(9L);
        assertThatThrownBy(() -> service.alterarStatus(40, "CONFERIDO", "u")).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("n. 9");
        lote(41, "RASCUNHO", List.of());
        when(jdbc.queryForObject(contains("select fechamento_id"), eq(Long.class), eq(41L))).thenReturn(null);
        assertThatThrownBy(() -> service.alterarStatus(41, "enviado", "u")).hasMessageContaining("Fechamento e envio");
        assertThatThrownBy(() -> service.alterarStatus(41, "CONFERIDO", "u")).hasMessageContaining("Lote sem itens");
    }

    @Test
    void conferirListaOsProblemasDeCadaEtiqueta() {
        lote(42, "RASCUNHO", List.of(7L), item("12", "", null, null, true), item("1234567", "Ok", 5L, 7L, false));
        when(jdbc.queryForObject(contains("select fechamento_id"), eq(Long.class), eq(42L))).thenReturn(null);
        assertThatThrownBy(() -> service.alterarStatus(42, "CONFERIDO", "u"))
                .hasMessage("Corrija antes de conferir: linha 1 (atendimento invalido, sem paciente, sem medico, sem exame, nao conferida)");
    }

    @Test
    void conferirResumeQuandoHaMaisDeDezProblemas() {
        var itens = new ArrayList<ProducaoService.Item>();
        for (int n = 0; n < 12; n++) itens.add(item("1", "P", 5L, null, false));
        lote(43, "RASCUNHO", List.of(), itens.toArray(ProducaoService.Item[]::new));
        when(jdbc.queryForObject(contains("select fechamento_id"), eq(Long.class), eq(43L))).thenReturn(null);
        assertThatThrownBy(() -> service.alterarStatus(43, "CONFERIDO", "u")).hasMessageEndingWith("e mais 2 etiqueta(s)");
    }

    @Test
    void conferirEReabrirRegistramHistorico() {
        lote(44, "RASCUNHO", List.of(), item("1234567", "P", 5L, null, false));
        when(jdbc.queryForObject(contains("select fechamento_id"), eq(Long.class), eq(44L))).thenReturn(null);
        service.alterarStatus(44, "conferido", "u");
        verify(historico).lote(44L, "CONFERIDA", "Lote marcado como conferido (1 etiqueta(s))", "u");
        service.alterarStatus(44, "RASCUNHO", "u");
        verify(historico).lote(44L, "REABERTA", "Lote reaberto para edição", "u");
    }

    // ------------------------------------------------------------------ exclusao, foto, exames e duplicidades
    @Test
    void naoExcluiLoteEnviado() {
        lote(50, "ENVIADO", List.of());
        assertThatThrownBy(() -> service.excluir(50)).isInstanceOf(ConflitoRevisaoException.class);
        lote(51, "RASCUNHO", List.of());
        service.excluir(51);
        verify(jdbc).update("delete from producao_lote where id=?", 51L);
    }

    @Test
    void fotoGravadaCifradaELidaDecifrada() {
        lote(60, "RASCUNHO", List.of());
        byte[] foto = {1, 2, 3}, cifrada = {9};
        when(cripto.cifrarArquivo(foto)).thenReturn(cifrada);
        service.salvarFoto(60, foto, "image/jpeg", "u");
        verify(jdbc).update(contains("set foto=?"), eq(cifrada), eq("image/jpeg"), eq(60L));
        verify(historico).lote(60L, "FOTO", "Foto da folha anexada", "u");
        when(jdbc.queryForList(contains("foto is not null"), eq(61L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.foto(61)).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.queryForList(contains("foto is not null"), eq(62L))).thenReturn(List.of(Map.of("foto", cifrada, "foto_tipo", "image/jpeg")));
        when(cripto.decifrarArquivo(cifrada)).thenReturn(foto);
        assertThat(service.foto(62)).containsEntry("foto", foto).containsEntry("foto_tipo", "image/jpeg");
    }

    @Test
    void criarExameNormalizaNomeERecusaDuplicado() {
        assertThatThrownBy(() -> service.criarExame(" ")).hasMessageContaining("nome do exame");
        assertThatThrownBy(() -> service.criarExame("x".repeat(121))).hasMessageContaining("120");
        when(jdbc.queryForObject(contains("insert into producao_exame_tipo"), eq(Long.class), eq("Ecocardiograma  fetal"), eq("ECOCARDIOGRAMA FETAL"))).thenReturn(9L);
        assertThat(service.criarExame(" Ecocardiograma  fetal ").id()).isEqualTo(9L);
        when(jdbc.queryForObject(contains("insert into producao_exame_tipo"), eq(Long.class), eq("ECG"), eq("ECG"))).thenThrow(new DuplicateKeyException("dup"));
        assertThatThrownBy(() -> service.criarExame("ECG")).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test
    void duplicidadesIgnoramVaziosEUsamOLoteAtual() {
        assertThat(service.duplicidades(null, null)).isEmpty();
        assertThat(service.duplicidades(List.of(" ", ""), null)).isEmpty();
        when(jdbc.query(contains("i.nr_atendimento in (?,?)"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThat(service.duplicidades(java.util.Arrays.asList("123 4567", "1234567", "7654321", null), 3L)).isEmpty();
        verify(jdbc).query(contains("i.nr_atendimento in (?,?)"), any(RowMapper.class), eq("1234567"), eq("7654321"), eq(3L));
    }
}
