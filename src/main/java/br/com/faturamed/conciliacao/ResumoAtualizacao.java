package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.util.*;

public record ResumoAtualizacao(long novasPagas, long pendentes, long divergentes,
        BigDecimal valorNovasPagas, BigDecimal valorPendenteInformado, BigDecimal valorDivergenteInformado,
        long pendentesSemValor, long divergentesSemValor) {
    public static ResumoAtualizacao comparar(ConciliadorVisitas.Relatorio antes, ConciliadorVisitas.Relatorio depois) {
        Map<Integer,ConciliadorVisitas.Status> anteriores=new HashMap<>();
        antes.visitas().forEach(v->anteriores.put(v.linhaMedico(),v.status()));
        var pagas=depois.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.PAGA
                && anteriores.get(v.linhaMedico())!=ConciliadorVisitas.Status.PAGA).toList();
        var pendentes=depois.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.PENDENTE).toList();
        var divergentes=depois.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.DIVERGENTE).toList();
        return new ResumoAtualizacao(pagas.size(),pendentes.size(),divergentes.size(),soma(pagas),soma(pendentes),soma(divergentes),
                pendentes.stream().filter(v->v.repasseEfetivo()==null).count(),divergentes.stream().filter(v->v.repasseEfetivo()==null).count());
    }
    private static BigDecimal soma(List<ConciliadorVisitas.Visita> visitas) {
        return visitas.stream().map(ConciliadorVisitas.Visita::repasseEfetivo).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
}
