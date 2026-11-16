package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Fechamento da producao: regras de valor, rateio por etiqueta e transicoes de status (sem banco). */
@SuppressWarnings("unchecked")
class FechamentoProducaoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HistoricoProducao historico = mock(HistoricoProducao.class);
    private final AvisoHospitalEmail aviso = mock(AvisoHospitalEmail.class);
    private final FechamentoProducaoService service = new FechamentoProducaoService(jdbc, historico, aviso, mock(PrazoConferencia.class), mock(CriptografiaDados.class));
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    private static FechamentoProducaoService.RegraDados regra(String tipo, Long medico, List<FechamentoProducaoService.ExamePeso> exames,
            List<FechamentoProducaoService.Faixa> faixas, LocalDate fim) {
        return new FechamentoProducaoService.RegraDados(1L, " MAPA ", tipo, new BigDecimal("48.00"), 0, null, medico, INICIO, fim, null, true, exames, faixas);
    }
    private static final List<FechamentoProducaoService.ExamePeso> UM_EXAME = List.of(new FechamentoProducaoService.ExamePeso(7L, 1));

    // ------------------------------------------------------------------ regras de valor
    @Test
    void validaTipoMedicoExamesFaixasEVigencia() {
        assertThatThrownBy(() -> service.salvarRegra(null, regra("QUALQUER", null, UM_EXAME, null, null))).hasMessageContaining("Tipo de regra");
        assertThatThrownBy(() -> service.salvarRegra(null, regra("MENSAL_MEDICO", null, null, null, null))).hasMessageContaining("medico do valor mensal");
        assertThatThrownBy(() -> service.salvarRegra(null, regra("POR_EXAME", null, List.of(), null, null))).hasMessageContaining("ao menos um exame");
        assertThatThrownBy(() -> service.salvarRegra(null, regra("TABELA_AGENDA", null, UM_EXAME, List.of(), null))).hasMessageContaining("tabela de faixas");
        var repetidas = List.of(new FechamentoProducaoService.Faixa(2, BigDecimal.TEN), new FechamentoProducaoService.Faixa(2, BigDecimal.ONE));
        assertThatThrownBy(() -> service.salvarRegra(null, regra("TABELA_AGENDA", null, UM_EXAME, repetidas, null))).hasMessageContaining("Quantidade repetida");
        assertThatThrownBy(() -> service.salvarRegra(null, regra("POR_EXAME", null, UM_EXAME, null, INICIO.minusDays(1)))).hasMessageContaining("Fim da vigencia");
        verifyNoInteractions(jdbc);
    }

    @Test
    void criaRegraNormalizandoTipoNomeEExamesRepetidos() {
        when(jdbc.queryForObject(contains("insert into producao_regra_valor"), eq(Long.class), any(Object[].class))).thenReturn(10L);
        var exames = List.of(new FechamentoProducaoService.ExamePeso(7L, 0), new FechamentoProducaoService.ExamePeso(7L, 3), new FechamentoProducaoService.ExamePeso(8L, 2));
        var faixas = List.of(new FechamentoProducaoService.Faixa(1, BigDecimal.TEN), new FechamentoProducaoService.Faixa(3, new BigDecimal("25")));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.salvarRegra(null, regra(" tabela_agenda ", 5L, exames, faixas, null))).isInstanceOf(java.util.NoSuchElementException.class);
        // tipo em maiusculo, nome sem espacos, medico so vale para MENSAL_MEDICO, incremento nulo vira zero
        verify(jdbc).queryForObject(contains("insert into producao_regra_valor"), eq(Long.class),
                eq(1L), eq("MAPA"), eq("TABELA_AGENDA"), eq(new BigDecimal("48.00")), eq(0), eq(BigDecimal.ZERO), isNull(), eq(INICIO), isNull(), eq(""), eq(true));
        verify(jdbc).update("insert into producao_regra_exame(regra_id,exame_id,peso) values(?,?,?)", 10L, 7L, 1); // peso minimo 1, repetido ignorado
        verify(jdbc).update("insert into producao_regra_exame(regra_id,exame_id,peso) values(?,?,?)", 10L, 8L, 2);
        verify(jdbc, times(2)).update(eq("insert into producao_regra_faixa(regra_id,quantidade,valor) values(?,?,?)"), eq(10L), anyInt(), any());
    }

    @Test
    void atualizarRegraInexistenteDaErroEAtualizarExistenteRefazExamesEFaixas() {
        when(jdbc.update(contains("update producao_regra_valor"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.salvarRegra(4L, regra("MENSAL_MEDICO", 5L, null, null, null))).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.update(contains("update producao_regra_valor"), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.salvarRegra(4L, regra("MENSAL_MEDICO", 5L, null, null, null))).isInstanceOf(java.util.NoSuchElementException.class);
        verify(jdbc).update("delete from producao_regra_exame where regra_id=?", 4L);
        verify(jdbc).update("delete from producao_regra_faixa where regra_id=?", 4L);
    }

    @Test
    void naoExcluiRegraJaUsadaEmFechamento() {
        when(jdbc.queryForObject(contains("producao_fechamento_calculo"), eq(Integer.class), eq(3L))).thenReturn(2);
        assertThatThrownBy(() -> service.excluirRegra(3)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForObject(contains("producao_fechamento_calculo"), eq(Integer.class), eq(4L))).thenReturn(0);
        service.excluirRegra(4);
        verify(jdbc).update("delete from producao_regra_valor where id=?", 4L);
    }

    // ------------------------------------------------------------------ rateio e resumo
    private static FechamentoProducaoService.Calculo calc(Long lote, Long medico, String nome, int exames, String valor) {
        return new FechamentoProducaoService.Calculo(lote, INICIO, "MANHA", medico, nome, 1L, "r", exames, 0, valor == null ? null : new BigDecimal(valor));
    }

    @Test
    void rateioDistribuiCentavosParaAUltimaEtiquetaEIgnoraFixoMensal() {
        var calculos = List.of(calc(1L, 5L, "Ana", 3, "100.00"), calc(null, 5L, "Ana", 0, "500.00"), calc(2L, 6L, "Bia", 1, null));
        var itens = List.of(new FechamentoProducaoService.RefItem(11, 1, 5L), new FechamentoProducaoService.RefItem(12, 1, 5L),
                new FechamentoProducaoService.RefItem(13, 1, 5L), new FechamentoProducaoService.RefItem(21, 2, 6L), new FechamentoProducaoService.RefItem(31, 9, 7L));
        Map<Long, BigDecimal> v = FechamentoProducaoService.ratear(calculos, itens);
        assertThat(v.get(11L)).isEqualByComparingTo("33.33");
        assertThat(v.get(12L)).isEqualByComparingTo("33.33");
        assertThat(v.get(13L)).isEqualByComparingTo("33.34"); // soma bate com 100,00
        assertThat(v.get(21L)).isEqualByComparingTo("0");     // calculo sem valor
        assertThat(v.get(31L)).isEqualByComparingTo("0");     // agenda sem calculo
    }

    @Test
    void resumoPorMedicoContaAgendasExamesETotal() {
        var calculos = List.of(calc(1L, 5L, "Ana", 3, "100"), calc(2L, 5L, "Ana", 2, "50"), calc(null, 5L, "Ana", 0, "500"), calc(3L, 6L, "Bia", 1, "10"));
        var r = FechamentoProducaoService.resumir(calculos);
        assertThat(r).extracting(FechamentoProducaoService.ResumoMedico::medico).containsExactly("Ana", "Bia");
        assertThat(r.get(0).agendas()).isEqualTo(2);
        assertThat(r.get(0).exames()).isEqualTo(5);
        assertThat(r.get(0).total()).isEqualByComparingTo("650");
        assertThat(FechamentoProducaoService.total(List.of())).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ status do fechamento
    private void status(long id, String... s) {
        when(jdbc.queryForList(contains("select status from producao_fechamento"), eq(String.class), eq(id))).thenReturn(List.of(s));
    }

    @Test
    void aprovarExigeQuemAprovouEStatusAguardando() {
        assertThatThrownBy(() -> service.aprovar(1, " ", INICIO, "u")).hasMessageContaining("quem aprovou");
        status(1);
        assertThatThrownBy(() -> service.aprovar(1, "Hospital", INICIO, "u")).isInstanceOf(RecursoNaoEncontradoException.class);
        status(2, "NF_EMITIDA");
        assertThatThrownBy(() -> service.aprovar(2, "Hospital", INICIO, "u")).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("NF_EMITIDA");
    }

    @Test
    void registrarNfExigeNumeroEStatusAprovado() {
        assertThatThrownBy(() -> service.registrarNf(1, "", INICIO, "u")).hasMessageContaining("numero da NF");
        status(1, "AGUARDANDO");
        assertThatThrownBy(() -> service.registrarNf(1, "123", INICIO, "u")).isInstanceOf(ConflitoRevisaoException.class);
        verifyNoInteractions(aviso);
    }

    @Test
    void arquivoDaNfSoDepoisDaNfEmitida() {
        status(1, "APROVADO");
        assertThatThrownBy(() -> service.salvarNfArquivo(1, new byte[]{1}, "nf.pdf", "application/pdf", "u")).isInstanceOf(ConflitoRevisaoException.class);
        status(2, "NF_EMITIDA");
        service.salvarNfArquivo(2, new byte[]{1}, "nf.pdf", "application/pdf", "u");
        verify(historico).fechamento(2L, "NF_ARQUIVO", "Arquivo da NF anexado (nf.pdf)", "u");
        when(jdbc.queryForList(contains("nf_arquivo is not null"), eq(3L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.nfArquivo(3)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void cancelarDevolveOsLotesParaConferido() {
        status(5, "APROVADO");
        assertThatThrownBy(() -> service.cancelar(5, "u")).isInstanceOf(ConflitoRevisaoException.class);
        status(6, "AGUARDANDO");
        when(jdbc.queryForList(contains("select id from producao_lote where fechamento_id"), eq(Long.class), eq(6L))).thenReturn(List.of(41L, 42L));
        service.cancelar(6, "u");
        verify(historico).lote(eq(41L), eq("RETIRADA"), contains("Fechamento nº 6 cancelado"), eq("u"));
        verify(historico).lote(eq(42L), eq("RETIRADA"), anyString(), eq("u"));
        verify(historico).fechamento(6L, "CANCELADO", "Fechamento cancelado", "u");
        verify(jdbc).update(contains("set fechamento_id=null, status='CONFERIDO'"), eq(6L));
        verify(jdbc).update("delete from producao_fechamento where id=?", 6L);
    }

    @Test
    void contestacaoSoComDecisaoValidaEPendente() {
        assertThatThrownBy(() -> service.resolverContestacao(1, "talvez", "", "u")).hasMessageContaining("Decisao invalida");
        when(jdbc.update(contains("update producao_item set contestacao=?"), eq("ACEITA"), eq(""), eq(1L))).thenReturn(0);
        assertThatThrownBy(() -> service.resolverContestacao(1, "aceita", null, "u")).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.update(contains("update producao_item set contestacao=?"), eq("REINCLUIR"), eq("ok"), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("select lote_id from producao_item"), eq(Long.class), eq(2L))).thenReturn(30L);
        service.resolverContestacao(2, "reincluir", " ok ", "u");
        verify(historico).lote(eq(30L), eq("CONTESTACAO"), contains("recusada"), eq("u"));
    }

    @Test
    void valoresDeLoteInexistenteDaErro() {
        when(jdbc.queryForList(contains("from producao_lote where id"), eq(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.valoresLote(99)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
