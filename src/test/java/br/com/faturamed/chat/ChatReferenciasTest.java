package br.com.faturamed.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cartoes de referencia do chat (#lote, #chamado, #relatorio, #div) sem banco. */
class ChatReferenciasTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatReferencias referencias = new ChatReferencias(jdbc);

    @Test
    void loteViraCartaoComStatusPeriodoELink() {
        when(jdbc.queryForList(contains("from producao_lote"), eq(150L))).thenReturn(List.of(Map.of(
                "id", 150L, "status", "CONFERIDO", "data_producao", "2026-11-06", "periodo", "TARDE", "hospital", "Hospital A", "itens", 5L)));
        var r = referencias.resolver(List.of("lote:150")).getFirst();
        assertThat(r.titulo()).isEqualTo("Lote #150 · Hospital A");
        assertThat(r.detalhe()).isEqualTo("06/11/2026 · Tarde · 5 item(ns)");
        assertThat(r.status()).isEqualTo("Conferido");
        assertThat(r.cor()).isEqualTo("azul");
        assertThat(r.link()).isEqualTo("producao.html?folha=150");
    }

    @Test
    void loteEnviadoERascunhoTemCoresDiferentes() {
        when(jdbc.queryForList(contains("from producao_lote"), eq(1L))).thenReturn(List.of(Map.of(
                "id", 1L, "status", "ENVIADO", "data_producao", "2026-01-02", "periodo", "MANHA", "hospital", "H", "itens", 1L)));
        when(jdbc.queryForList(contains("from producao_lote"), eq(2L))).thenReturn(List.of(Map.of(
                "id", 2L, "status", "RASCUNHO", "data_producao", "2026-01-02", "periodo", "INTEGRAL", "hospital", "H", "itens", 0L)));
        var lista = referencias.resolver(List.of("lote:1", "lote:2"));
        assertThat(lista).extracting(ChatReferencias.Referencia::cor).containsExactly("verde", "ambar");
        assertThat(lista.get(1).detalhe()).contains("Integral");
    }

    @Test
    void chamadoMostraResponsavelEStatus() {
        var linha = new java.util.HashMap<String, Object>(Map.of("id", 12L, "protocolo", "CH-2026-0012", "titulo", "Erro na planilha", "status", "EM_ATENDIMENTO"));
        linha.put("responsavel_nome", "Ana Souza");
        when(jdbc.queryForList(contains("from chamado_suporte"), eq(12L))).thenReturn(List.of(linha));
        var r = referencias.resolver(List.of("chamado:12")).getFirst();
        assertThat(r.titulo()).isEqualTo("CH-2026-0012 · Erro na planilha");
        assertThat(r.detalhe()).isEqualTo("Com Ana Souza");
        assertThat(r.status()).isEqualTo("Em atendimento");
        assertThat(r.link()).isEqualTo("chamados.html?chamado=12");
    }

    @Test
    void chamadoSemResponsavelEResolvido() {
        var linha = new java.util.HashMap<String, Object>(Map.of("id", 3L, "protocolo", "CH-3", "titulo", "t", "status", "RESOLVIDO"));
        linha.put("responsavel_nome", null);
        when(jdbc.queryForList(contains("from chamado_suporte"), eq(3L))).thenReturn(List.of(linha));
        var r = referencias.resolver(List.of("chamado:3")).getFirst();
        assertThat(r.detalhe()).isEqualTo("Sem responsável");
        assertThat(r.cor()).isEqualTo("verde");
    }

    @Test
    void relatorioComESemDivergencias() {
        var com = new java.util.HashMap<String, Object>(Map.of("id", 6L, "hospital", "Itaim", "divergentes", 3, "pagas", 7, "total", 10));
        com.put("arquivo", "producao.xlsx");
        var sem = new java.util.HashMap<String, Object>(Map.of("id", 7L, "hospital", "Morumbi", "divergentes", 0, "pagas", 4, "total", 4));
        sem.put("arquivo", null);
        when(jdbc.queryForList(contains("from conciliacao_visitas where id"), eq(6L))).thenReturn(List.of(com));
        when(jdbc.queryForList(contains("from conciliacao_visitas where id"), eq(7L))).thenReturn(List.of(sem));
        var lista = referencias.resolver(List.of("relatorio:6", "relatorio:7"));
        assertThat(lista.get(0).status()).isEqualTo("3 divergente(s)");
        assertThat(lista.get(0).cor()).isEqualTo("rosa");
        assertThat(lista.get(0).detalhe()).contains("producao.xlsx");
        assertThat(lista.get(1).status()).isEqualTo("Sem divergências");
        assertThat(lista.get(1).cor()).isEqualTo("verde");
    }

    @Test
    void divergenciaDeUmAtendimentoAbreOFiltroNaConciliacao() {
        when(jdbc.queryForList(contains("jsonb_array_elements"), eq(6L), eq("10002"))).thenReturn(List.of(Map.of(
                "hospital", "Itaim", "status", "PENDENTE", "motivo", "Sem correspondente", "data", "2026-01-02")));
        var r = referencias.resolver(List.of("div:6/10002")).getFirst();
        assertThat(r.titulo()).isEqualTo("Atendimento 10002 · Itaim");
        assertThat(r.status()).isEqualTo("Pendente");
        assertThat(r.link()).isEqualTo("conciliacao.html?relatorio=6&atendimento=10002");
    }

    @Test
    void divSemAtendimentoUsaORelatorio() {
        when(jdbc.queryForList(contains("from conciliacao_visitas where id"), eq(9L))).thenReturn(List.of());
        assertThat(referencias.resolver(List.of("div:9"))).isEmpty();
        verify(jdbc).queryForList(contains("from conciliacao_visitas where id"), eq(9L));
    }

    @Test
    void ignoraChavesInvalidasRepetidasENulas() {
        assertThat(referencias.resolver(null)).isEmpty();
        assertThat(referencias.resolver(List.of("xpto:1", "lote:abc", "lote:", ""))).isEmpty();
        when(jdbc.queryForList(contains("from producao_lote"), eq(5L))).thenReturn(List.of());
        assertThat(referencias.resolver(List.of("lote:5", "lote:5"))).isEmpty();
        verify(jdbc, times(1)).queryForList(contains("from producao_lote"), eq(5L));
    }
}
