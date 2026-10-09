package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FiltroRelatorioMedico {
    public ConciliadorVisitas.Relatorio filtrar(ConciliadorVisitas.Relatorio base, String medico) {
        String chave = LeitorPlanilhaVisitas.normalizar(medico);
        if (chave.isBlank()) throw new IllegalArgumentException("Selecione um medico identificado");
        return selecionar(base,v -> LeitorPlanilhaVisitas.normalizar(v.medicoEfetivo()).equals(chave));
    }
    public ConciliadorVisitas.Relatorio filtrar(ConciliadorVisitas.Relatorio base, long id, MedicoCadastroService service) {
        service.buscar(id);
        var medicos=service.listar();
        return selecionar(base,v -> Objects.equals(service.identificar(v,medicos),id));
    }
    private ConciliadorVisitas.Relatorio selecionar(ConciliadorVisitas.Relatorio base, java.util.function.Predicate<ConciliadorVisitas.Visita> filtro) {
        var visitas = base.visitas().stream().filter(filtro).toList();
        if (visitas.isEmpty()) throw new IllegalArgumentException("Medico sem visitas identificadas neste relatorio");
        Map<ConciliadorVisitas.Status, Long> resumo = new EnumMap<>(ConciliadorVisitas.Status.class);
        for (var status : ConciliadorVisitas.Status.values()) resumo.put(status, visitas.stream().filter(v -> v.status() == status).count());
        var repasse = visitas.stream().filter(v -> v.status() == ConciliadorVisitas.Status.PAGA)
                .map(ConciliadorVisitas.Visita::repasseEfetivo).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ConciliadorVisitas.Relatorio(base.hospital(), base.arquivoMedico(), base.arquivoHospital(), base.regraStatus(),
                visitas, List.of(), resumo, repasse, base.hospitalCadastroId());
    }
}
