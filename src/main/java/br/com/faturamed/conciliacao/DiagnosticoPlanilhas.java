package br.com.faturamed.conciliacao;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticoPlanilhas {
    public record Arquivo(int linhas, long idsAusentes, long idsRepetidos, long linhasComIdRepetido,
            long semMedico, long semData, LocalDate inicio, LocalDate fim) {}
    public record Alerta(String codigo, String descricao, long quantidade, List<Integer> linhas) {}
    public record Resultado(Arquivo producao, Arquivo hospital, long correspondenciasSeguras,
            long semCorrespondencia, long ambiguas, long dadosDivergentes, List<Alerta> alertas) {}

    public Resultado analisar(List<LeitorPlanilhaVisitas.Linha> producao,
            List<LeitorPlanilhaVisitas.Linha> hospital, ConciliadorVisitas.Relatorio simulacao) {
        var p=arquivo(producao,"Dt."); var h=arquivo(hospital,"Data Consumo");
        List<Alerta> alertas=new ArrayList<>();
        repetidos(alertas,producao,"ID_PRODUCAO_REPETIDO","ID repetido na producao");
        repetidos(alertas,hospital,"ID_HOSPITAL_REPETIDO","ID repetido no hospital");
        adicionar(alertas,"ID_PRODUCAO_AUSENTE","Producao sem ID",producao.stream().filter(l->l.campo("ID").isBlank()).map(LeitorPlanilhaVisitas.Linha::numero).toList());
        adicionar(alertas,"ID_HOSPITAL_AUSENTE","Hospital sem ID",hospital.stream().filter(l->l.campo("ID").isBlank()).map(LeitorPlanilhaVisitas.Linha::numero).toList());
        adicionar(alertas,"MEDICO_NAO_IDENTIFICADO","Visita sem medico identificado",simulacao.visitas().stream().filter(v->v.medicoEfetivo().isBlank()).map(ConciliadorVisitas.Visita::linhaMedico).toList());
        adicionar(alertas,"DATA_AUSENTE","Producao sem data da visita",producao.stream().filter(l->l.campo("Dt.").isBlank()).map(LeitorPlanilhaVisitas.Linha::numero).toList());
        var sem=simulacao.visitas().stream().filter(v->v.candidatos().isEmpty()).map(ConciliadorVisitas.Visita::linhaMedico).toList();
        var ambiguas=simulacao.visitas().stream().filter(v->v.hospital()==null&&!v.candidatos().isEmpty()).map(ConciliadorVisitas.Visita::linhaMedico).toList();
        var divergentes=simulacao.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.DIVERGENTE&&v.hospital()!=null).map(ConciliadorVisitas.Visita::linhaMedico).toList();
        adicionar(alertas,"SEM_CORRESPONDENCIA","Sem correspondente no arquivo hospitalar",sem);
        adicionar(alertas,"CORRESPONDENCIA_AMBIGUA","Correspondencia exige conferencia (duplicidade ou identificacao conflitante)",ambiguas);
        adicionar(alertas,"DADOS_DIVERGENTES","Correspondencia com dados ou valores divergentes",divergentes);
        Set<String> atendimentos=new HashSet<>(); hospital.forEach(l->atendimentos.add(LeitorPlanilhaVisitas.identificador(l.campo("Atendimento"))));
        adicionar(alertas,"ATENDIMENTO_AUSENTE_HOSPITAL","Atendimento nao aparece no arquivo hospitalar",producao.stream().filter(l->!atendimentos.contains(LeitorPlanilhaVisitas.identificador(l.campo("Atendimento")))).map(LeitorPlanilhaVisitas.Linha::numero).toList());
        long seguras=simulacao.visitas().stream().filter(v->v.hospital()!=null&&v.status()!=ConciliadorVisitas.Status.DIVERGENTE).count();
        return new Resultado(p,h,seguras,sem.size(),ambiguas.size(),divergentes.size(),List.copyOf(alertas));
    }
    private Arquivo arquivo(List<LeitorPlanilhaVisitas.Linha> linhas,String data) {
        var grupos=grupos(linhas);
        var datas=linhas.stream().filter(l->!l.campo(data).isBlank()).map(l->LeitorPlanilhaVisitas.data(l.campo(data),l.numero())).sorted().toList();
        return new Arquivo(linhas.size(),linhas.stream().filter(l->l.campo("ID").isBlank()).count(),
                grupos.values().stream().filter(g->g.size()>1).count(),grupos.values().stream().filter(g->g.size()>1).mapToLong(List::size).sum(),
                linhas.stream().filter(l->l.campo("Medico").isBlank()).count(),linhas.stream().filter(l->l.campo(data).isBlank()).count(),
                datas.isEmpty()?null:datas.getFirst(),datas.isEmpty()?null:datas.getLast());
    }
    private Map<String,List<Integer>> grupos(List<LeitorPlanilhaVisitas.Linha> linhas) {
        Map<String,List<Integer>> grupos=new LinkedHashMap<>();
        for(var l:linhas) {String id=LeitorPlanilhaVisitas.identificador(l.campo("ID"));if(!id.isBlank())grupos.computeIfAbsent(id,k->new ArrayList<>()).add(l.numero());}
        return grupos;
    }
    private void repetidos(List<Alerta> alertas,List<LeitorPlanilhaVisitas.Linha> linhas,String codigo,String descricao) {
        adicionar(alertas,codigo,descricao,grupos(linhas).values().stream().filter(g->g.size()>1).flatMap(List::stream).sorted().toList());
    }
    private void adicionar(List<Alerta> alertas,String codigo,String descricao,List<Integer> linhas) {
        if(!linhas.isEmpty())alertas.add(new Alerta(codigo,descricao,linhas.size(),linhas.stream().limit(20).toList()));
    }
}
