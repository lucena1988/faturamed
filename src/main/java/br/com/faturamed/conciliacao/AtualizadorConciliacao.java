package br.com.faturamed.conciliacao;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AtualizadorConciliacao {
    public record Resultado(ConciliadorVisitas.Relatorio relatorio,int preservadas,int reavaliadas,int novas,Map<Integer,Integer> linhasProducao) {}
    private final ConciliadorVisitas motor;
    public AtualizadorConciliacao(ConciliadorVisitas motor) { this.motor=motor; }

    public Resultado atualizar(RevisaoVisitasService.Estado anterior, String arquivoMedico,String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> producao,List<LeitorPlanilhaVisitas.Linha> faturamento) {
        var base=anterior.original();
        Set<Integer> revisadas=new HashSet<>();anterior.historico().forEach(r->revisadas.add(r.linhaMedico()));
        Map<Integer,ConciliadorVisitas.Visita> atuais=new HashMap<>();anterior.atual().visitas().forEach(v->atuais.put(v.linhaMedico(),v));
        Map<Map<String,String>,Deque<ConciliadorVisitas.Visita>> grupos=new LinkedHashMap<>();
        base.visitas().forEach(v->grupos.computeIfAbsent(chave(v.original()),k->new ArrayDeque<>()).add(v));
        int maxLinha=base.visitas().stream().mapToInt(ConciliadorVisitas.Visita::linhaMedico).max().orElse(1);
        Map<Integer,ConciliadorVisitas.Visita> originais=new HashMap<>();
        Map<Integer,Integer> origem=new LinkedHashMap<>();List<LeitorPlanilhaVisitas.Linha> linhas=new ArrayList<>();
        for(var linha:producao) {
            var grupo=grupos.get(chave(linha.campos()));var v=grupo==null||grupo.isEmpty()?null:grupo.removeFirst();
            int id=v==null?++maxLinha:v.linhaMedico();
            if(v!=null)originais.put(id,v);
            origem.put(id,linha.numero());linhas.add(new LeitorPlanilhaVisitas.Linha(id,linha.campos()));
        }
        if(grupos.values().stream().anyMatch(g->!g.isEmpty())) throw new ConflitoRevisaoException("A producao reduziu ou alterou visitas anteriores. Envie a versao completa ou confira o relatorio antes de atualizar");
        var novo=motor.conciliar(base.hospital(),arquivoMedico,arquivoHospital,linhas,faturamento);
        List<ConciliadorVisitas.RegistroHospital> novos=registros(novo);
        var antigos=registros(base);
        Map<Integer,ConciliadorVisitas.RegistroHospital> remapeados=new HashMap<>();Set<Integer> usados=new HashSet<>();
        Set<Integer> preservadas=new HashSet<>();
        atuais.values().stream().filter(v->v.status()==ConciliadorVisitas.Status.PAGA||revisadas.contains(v.linhaMedico())).forEach(v->preservadas.add(v.linhaMedico()));
        // Associa registros preservados pelo conteudo completo, nunca pela posicao na planilha.
        Map<Integer,ConciliadorVisitas.RegistroHospital> protegidos=new LinkedHashMap<>();
        atuais.values().stream().filter(v->preservadas.contains(v.linhaMedico())&&v.hospital()!=null).forEach(v->protegidos.put(v.hospital().linha(),v.hospital()));
        base.visitas().stream().filter(v->preservadas.contains(v.linhaMedico())&&v.hospital()!=null).forEach(v->protegidos.putIfAbsent(v.hospital().linha(),v.hospital()));
        for(var h:protegidos.values()) {
            var encontrado=novos.stream().filter(n->!usados.contains(n.linha())&&mesmoRegistro(h,n)).findFirst()
                    .orElseThrow(()->new ConflitoRevisaoException("Registro hospitalar ja conciliado alterado ou ausente: atendimento "+h.atendimento()+", linha "+h.linha()+". Confira antes de atualizar"));
            remapeados.put(encontrado.linha(),comLinha(encontrado,h.linha()));usados.add(encontrado.linha());
        }
        Set<Integer> ids=new HashSet<>();remapeados.values().forEach(h->ids.add(h.linha()));
        int maxHospital=antigos.stream().mapToInt(ConciliadorVisitas.RegistroHospital::linha).max().orElse(1);
        for(var n:novos) if(!usados.contains(n.linha())) {
            var antigo=antigos.stream().filter(h->!ids.contains(h.linha())&&identidade(h).equals(identidade(n))).findFirst();
            int id=antigo.isPresent()?antigo.get().linha():++maxHospital;ids.add(id);remapeados.put(n.linha(),comLinha(n,id));
        }
        var hospital=novos.stream().map(h->remapeados.get(h.linha())).toList();
        Set<Integer> reservados=new HashSet<>();
        atuais.values().stream().filter(v->preservadas.contains(v.linhaMedico())&&v.hospital()!=null).forEach(v->reservados.add(v.hospital().linha()));
        var livres=hospital.stream().filter(h->!reservados.contains(h.linha())).map(this::linha).toList();
        var restantes=linhas.stream().filter(l->!preservadas.contains(l.numero())||atuais.get(l.numero()).hospital()==null).map(l->{
            if(!preservadas.contains(l.numero()))return l;
            var v=atuais.get(l.numero());Map<String,String> campos=new HashMap<>();campos.put("atendimento",v.atendimento());
            if(v.data()!=null)campos.put("dt.",v.data().toString());return new LeitorPlanilhaVisitas.Linha(l.numero(),campos);
        }).toList();
        var recalculado=motor.conciliar(base.hospital(),arquivoMedico,arquivoHospital,restantes,livres);
        Map<Integer,ConciliadorVisitas.Visita> calculadas=new HashMap<>();recalculado.visitas().forEach(v->calculadas.put(v.linhaMedico(),v));
        List<ConciliadorVisitas.Visita> visitas=new ArrayList<>();
        for(var l:linhas) {
            var v=preservadas.contains(l.numero())?originais.get(l.numero()):calculadas.get(l.numero());
            var candidatos=hospital.stream().filter(h->h.atendimento().equals(v.atendimento())&&(v.data()==null||v.data().equals(h.data()))).toList();
            visitas.add(new ConciliadorVisitas.Visita(v.linhaMedico(),v.atendimento(),v.data(),v.status(),v.motivo(),v.original(),v.hospital(),candidatos,v.camposRevisados()));
        }
        var resultado=new ConciliadorVisitas.Relatorio(base.hospital(),arquivoMedico,arquivoHospital,base.regraStatus(),visitas,hospital,
                Map.of(),BigDecimal.ZERO,base.hospitalCadastroId());
        resultado=new AplicadorRevisaoVisita().recalcular(resultado,visitas);
        return new Resultado(resultado,preservadas.size(),originais.size()-preservadas.size(),linhas.size()-originais.size(),origem);
    }
    private Map<String,String> chave(Map<String,String> campos) {
        var copia=new TreeMap<>(campos);copia.entrySet().removeIf(e->e.getValue().isBlank());
        copia.computeIfPresent("atendimento",(k,v)->LeitorPlanilhaVisitas.identificador(v));
        copia.computeIfPresent("dt.",(k,v)->LeitorPlanilhaVisitas.data(v,0).toString());
        return Map.copyOf(copia);
    }
    private List<ConciliadorVisitas.RegistroHospital> registros(ConciliadorVisitas.Relatorio r) {
        Map<Integer,ConciliadorVisitas.RegistroHospital> mapa=new LinkedHashMap<>();r.hospitalSemProducao().forEach(h->mapa.put(h.linha(),h));
        r.visitas().forEach(v->v.candidatos().forEach(h->mapa.put(h.linha(),h)));return List.copyOf(mapa.values());
    }
    private List<Object> identidade(ConciliadorVisitas.RegistroHospital h) {
        return Arrays.asList(h.atendimento(),h.data(),LeitorPlanilhaVisitas.normalizar(h.paciente()),LeitorPlanilhaVisitas.normalizar(h.medico()),h.codigo(),LeitorPlanilhaVisitas.normalizar(h.procedimento()),h.conta());
    }
    private boolean mesmoRegistro(ConciliadorVisitas.RegistroHospital a,ConciliadorVisitas.RegistroHospital b) {
        return identidade(a).equals(identidade(b))&&LeitorPlanilhaVisitas.normalizar(a.setor()).equals(LeitorPlanilhaVisitas.normalizar(b.setor()))
                && decimal(a.valorTotal(),b.valorTotal())&&decimal(a.regra(),b.regra())&&decimal(a.repasse(),b.repasse());
    }
    private boolean decimal(BigDecimal a,BigDecimal b) { return a==null?b==null:b!=null&&a.compareTo(b)==0; }
    private ConciliadorVisitas.RegistroHospital comLinha(ConciliadorVisitas.RegistroHospital h,int linha) {
        return new ConciliadorVisitas.RegistroHospital(linha,h.atendimento(),h.data(),h.paciente(),h.convenio(),h.medico(),h.codigo(),h.procedimento(),h.conta(),h.setor(),h.valorTotal(),h.regra(),h.repasse());
    }
    private LeitorPlanilhaVisitas.Linha linha(ConciliadorVisitas.RegistroHospital h) {
        Map<String,String> m=new HashMap<>();m.put("atendimento",h.atendimento());m.put("data consumo",h.data().toString());m.put("paciente",h.paciente());m.put("operadora",h.convenio());m.put("medico",h.medico());m.put("cod produto",h.codigo());m.put("produto",h.procedimento());m.put("conta paciente",h.conta());m.put("setor",h.setor());
        m.put("valor tot",Objects.toString(h.valorTotal(),""));m.put("regra",Objects.toString(h.regra(),""));m.put("vl. a repassar",Objects.toString(h.repasse(),""));return new LeitorPlanilhaVisitas.Linha(h.linha(),m);
    }
}
