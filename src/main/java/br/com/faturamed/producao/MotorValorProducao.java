package br.com.faturamed.producao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Calcula o valor da producao por AGENDA (folha x medico) segundo as regras do hospital.
 * Ver V31__producao_regras_fechamento.sql para a descricao dos tipos de regra.
 */
final class MotorValorProducao {
    private MotorValorProducao() {}

    record Regra(long id, String nome, String tipo, BigDecimal valor, int minimo, BigDecimal incremento, Long medicoId,
            LocalDate inicio, LocalDate fim, Map<Long, Integer> pesos, NavigableMap<Integer, BigDecimal> faixas) {
        boolean vigente(LocalDate d) { return !d.isBefore(inicio) && (fim == null || !d.isAfter(fim)); }
        boolean sobrepoe(LocalDate a, LocalDate b) { return !inicio.isAfter(b) && (fim == null || !fim.isBefore(a)); }
    }
    record ItemAgenda(long itemId, Long exameId) {}
    record Agenda(long loteId, long medicoId, LocalDate data, String periodo, List<ItemAgenda> itens) {}
    record Calculo(Long loteId, LocalDate data, String periodo, Long medicoId, Long regraId, String descricao, int exames, int pontos, BigDecimal valor) {}
    record Resultado(List<Calculo> calculos, List<Long> itensSemRegra) {}

    static Resultado calcular(List<Regra> regras, List<Agenda> agendas, LocalDate inicio, LocalDate fim) {
        List<Calculo> calculos = new ArrayList<>();
        List<Long> semRegra = new ArrayList<>();
        Set<Long> medicos = new LinkedHashSet<>();
        for (Agenda a : agendas) {
            medicos.add(a.medicoId());
            Map<Regra, List<ItemAgenda>> porRegra = new LinkedHashMap<>();
            for (ItemAgenda it : a.itens()) {
                Optional<Regra> r = regras.stream()
                        .filter(x -> !"MENSAL_MEDICO".equals(x.tipo()) && it.exameId() != null && x.pesos().containsKey(it.exameId()) && x.vigente(a.data()))
                        .max(Comparator.comparing(Regra::inicio));
                if (r.isPresent()) porRegra.computeIfAbsent(r.get(), k -> new ArrayList<>()).add(it);
                else semRegra.add(it.itemId());
            }
            porRegra.forEach((r, itens) -> {
                int exames = itens.size();
                int pontos = itens.stream().mapToInt(i -> r.pesos().get(i.exameId())).sum();
                BigDecimal valor;
                String descricao;
                switch (r.tipo()) {
                    case "POR_EXAME" -> {
                        int qtd = Math.max(exames, r.minimo());
                        valor = r.valor().multiply(BigDecimal.valueOf(qtd));
                        descricao = r.nome() + ": " + exames + " exame(s)" + (qtd > exames ? " (minimo " + r.minimo() + ")" : "");
                    }
                    case "POR_AGENDA" -> { valor = r.valor(); descricao = r.nome() + ": agenda (" + exames + " exame(s))"; }
                    default -> {
                        valor = valorFaixa(r.faixas(), r.incremento(), pontos);
                        descricao = r.nome() + ": " + exames + " exame(s) = " + pontos + " ponto(s)";
                    }
                }
                calculos.add(new Calculo(a.loteId(), a.data(), a.periodo(), a.medicoId(), r.id(), descricao, exames, pontos, valor));
            });
        }
        for (Regra r : regras)
            if ("MENSAL_MEDICO".equals(r.tipo()) && r.sobrepoe(inicio, fim))
                calculos.add(new Calculo(null, null, null, r.medicoId(), r.id(), r.nome() + " (fixo)", 0, 0, r.valor()));
        return new Resultado(calculos, semRegra);
    }

    /** Ate a primeira faixa vale o valor dela (minimo); entre faixas vale a maior faixa <= pontos; acima da ultima soma incremento por ponto. */
    static BigDecimal valorFaixa(NavigableMap<Integer, BigDecimal> faixas, BigDecimal incremento, int pontos) {
        if (faixas.isEmpty()) return BigDecimal.ZERO;
        if (pontos > faixas.lastKey())
            return faixas.lastEntry().getValue().add(incremento.multiply(BigDecimal.valueOf(pontos - faixas.lastKey())));
        var piso = faixas.floorEntry(pontos);
        return piso == null ? faixas.firstEntry().getValue() : piso.getValue();
    }
}
