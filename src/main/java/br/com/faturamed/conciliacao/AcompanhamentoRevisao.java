package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AcompanhamentoRevisao {
    public enum Origem { NAO_REVISADA, AUTOMATICA_IMPORTACAO, AUTOMATICA_REVISAO, MANUAL }
    public enum Categoria { SEM_PENDENCIA, AMBIGUIDADE, DADOS_FALTANTES, VALOR_DIVERGENTE,
        SEM_CORRESPONDENCIA, AGUARDANDO_FATURAMENTO, DADOS_DIVERGENTES, CONFERENCIA_MANUAL }
    public record Item(int linhaMedico, Origem origem, Categoria categoria, boolean precisaConferencia, boolean medicoIdentificado) {}
    public record Resultado(long versao, List<Item> visitas, ConciliadorVisitas.Relatorio relatorio) {}

    public Resultado analisar(RevisaoVisitasService.Estado estado) {
        Map<Integer, RevisaoVisitaRequest.Acao> ultimas = new HashMap<>();
        estado.historico().forEach(r -> ultimas.put(r.linhaMedico(), r.acao()));
        Map<Integer, Integer> disputas = new HashMap<>();
        Set<Integer> ocupados = new HashSet<>();
        estado.atual().visitas().stream().filter(v -> v.hospital() != null).forEach(v -> ocupados.add(v.hospital().linha()));
        estado.atual().visitas().stream().filter(v -> v.hospital() == null).forEach(v ->
                v.candidatos().stream().filter(h -> !ocupados.contains(h.linha())).forEach(h -> disputas.merge(h.linha(), 1, Integer::sum)));
        var itens = estado.atual().visitas().stream().map(v -> {
            var origem = origem(v, ultimas.get(v.linhaMedico()));
            var categoria = categoria(v, ocupados, disputas);
            boolean medico = !v.medicoEfetivo().isBlank();
            boolean conferir = v.status() == ConciliadorVisitas.Status.DIVERGENTE || incompleta(v)
                    || (v.status() == ConciliadorVisitas.Status.PENDENTE && origem == Origem.NAO_REVISADA);
            return new Item(v.linhaMedico(), origem, categoria, conferir, medico);
        }).toList();
        return new Resultado(estado.versao(), itens, estado.atual());
    }

    private Origem origem(ConciliadorVisitas.Visita v, RevisaoVisitaRequest.Acao acao) {
        if (acao == RevisaoVisitaRequest.Acao.CORRESPONDENCIA_AUTOMATICA) return Origem.AUTOMATICA_REVISAO;
        if (acao != null && acao != RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO) return Origem.MANUAL;
        return v.status() == ConciliadorVisitas.Status.PAGA ? Origem.AUTOMATICA_IMPORTACAO : Origem.NAO_REVISADA;
    }

    private Categoria categoria(ConciliadorVisitas.Visita v, Set<Integer> ocupados, Map<Integer, Integer> disputas) {
        if (v.status() == ConciliadorVisitas.Status.PAGA) return Categoria.SEM_PENDENCIA;
        if (v.hospital() == null && v.camposRevisados() == null) {
            var livres = v.candidatos().stream().filter(h -> !ocupados.contains(h.linha())).toList();
            if (livres.size() > 1 || livres.stream().anyMatch(h -> disputas.getOrDefault(h.linha(), 0) > 1)) return Categoria.AMBIGUIDADE;
            if (v.candidatos().isEmpty() || livres.isEmpty()) return Categoria.SEM_CORRESPONDENCIA;
        }
        if (incompleta(v)) return Categoria.DADOS_FALTANTES;
        var h = v.hospital();
        if (h != null && h.regra() != null && v.valorEfetivo().multiply(h.regra()).subtract(v.repasseEfetivo()).abs()
                .compareTo(new BigDecimal("0.01")) > 0) return Categoria.VALOR_DIVERGENTE;
        if (v.status() == ConciliadorVisitas.Status.DIVERGENTE && h != null) return Categoria.DADOS_DIVERGENTES;
        if (h != null && !LeitorPlanilhaVisitas.normalizar(h.setor()).equals("faturado")) return Categoria.AGUARDANDO_FATURAMENTO;
        return Categoria.CONFERENCIA_MANUAL;
    }

    private boolean incompleta(ConciliadorVisitas.Visita v) {
        return v.data() == null || v.medicoEfetivo().isBlank() || v.procedimentoEfetivo().isBlank()
                || v.codigoEfetivo() == null || v.codigoEfetivo().isBlank() || v.valorEfetivo() == null || v.repasseEfetivo() == null;
    }
}
