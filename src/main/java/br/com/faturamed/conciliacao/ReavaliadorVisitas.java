package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReavaliadorVisitas {
    private final AplicadorRevisaoVisita aplicador;
    public ReavaliadorVisitas(AplicadorRevisaoVisita aplicador) { this.aplicador = aplicador; }

    public Map<Integer, ConciliadorVisitas.RegistroHospital> propostas(ConciliadorVisitas.Relatorio base,
            List<ConciliadorVisitas.Visita> visitas, String atendimento, Set<Integer> revisadas) {
        Set<Integer> ocupados = new HashSet<>();
        visitas.stream().filter(v -> v.hospital() != null).forEach(v -> ocupados.add(v.hospital().linha()));
        Map<Integer, List<ConciliadorVisitas.RegistroHospital>> disponiveis = new LinkedHashMap<>();
        Map<Integer, Integer> disputas = new HashMap<>();
        for (var v : visitas) if (v.hospital() == null && v.atendimento().equals(atendimento)) {
            var candidatos = aplicador.candidatos(base, atendimento, v.data()).stream()
                    .filter(h -> !ocupados.contains(h.linha())).toList();
            disponiveis.put(v.linhaMedico(), candidatos);
            candidatos.forEach(h -> disputas.merge(h.linha(), 1, Integer::sum));
        }
        Map<Integer, ConciliadorVisitas.RegistroHospital> propostas = new LinkedHashMap<>();
        for (var v : visitas) {
            var candidatos = disponiveis.getOrDefault(v.linhaMedico(), List.of());
            if (revisadas.contains(v.linhaMedico()) || v.camposRevisados() != null
                    || v.status() != ConciliadorVisitas.Status.DIVERGENTE || candidatos.size() != 1) continue;
            var h = candidatos.getFirst();
            if (disputas.get(h.linha()) == 1 && seguro(v, h)) propostas.put(v.linhaMedico(), h);
        }
        return propostas;
    }

    private boolean seguro(ConciliadorVisitas.Visita visita, ConciliadorVisitas.RegistroHospital h) {
        if (h.medico().isBlank() || h.codigo().isBlank() || h.procedimento().isBlank()
                || h.valorTotal() == null || h.repasse() == null) return false;
        if (h.regra() != null && h.valorTotal().multiply(h.regra()).subtract(h.repasse()).abs()
                .compareTo(new BigDecimal("0.01")) > 0) return false;
        var dados = visita.original();
        String medico = dados.getOrDefault("medico", "");
        String procedimento = dados.getOrDefault("procedimento/mat-med", "");
        String valor = dados.getOrDefault("valor orig", "");
        if (!medico.isBlank() && !LeitorPlanilhaVisitas.normalizar(medico).equals(LeitorPlanilhaVisitas.normalizar(h.medico()))) return false;
        if (!procedimento.isBlank() && !procedimento.equals(h.codigo())
                && !LeitorPlanilhaVisitas.normalizar(procedimento).equals(LeitorPlanilhaVisitas.normalizar(h.procedimento()))) return false;
        try { return valor.isBlank() || new BigDecimal(valor).compareTo(h.valorTotal()) == 0; }
        catch (NumberFormatException e) { return false; }
    }
}
