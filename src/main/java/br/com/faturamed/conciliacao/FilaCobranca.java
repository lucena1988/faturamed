package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FilaCobranca {
    public record Item(int linha, String idOrigem, String atendimento, LocalDate data, Long dias,
            String chaveMedico, String medico, String crm, String codigo, String procedimento, BigDecimal repasse) {}
    public record Grupo(String chave, String medico, String crm, long visitas) {}
    public record Resultado(String hospital, LocalDate referencia, long totalSemCorrespondencia,
            List<Grupo> medicos, List<Item> visitas, BigDecimal repasseInformado, long semValor, long semMedico, long semData,
            Map<PrioridadesCobranca.Filtro,Long> prioridades) {
        public Resultado(String hospital,LocalDate referencia,long totalSemCorrespondencia,List<Grupo> medicos,List<Item> visitas,
                BigDecimal repasseInformado,long semValor,long semMedico,long semData) {
            this(hospital,referencia,totalSemCorrespondencia,medicos,visitas,repasseInformado,semValor,semMedico,semData,Map.of());
        }
    }
    private final MedicoCadastroService cadastro;
    public FilaCobranca(MedicoCadastroService cadastro) { this.cadastro=cadastro; }
    public static boolean elegivel(ConciliadorVisitas.Visita visita) {
        return visita.status()==ConciliadorVisitas.Status.PENDENTE&&visita.hospital()==null&&visita.candidatos().isEmpty();
    }

    public Resultado analisar(ConciliadorVisitas.Relatorio relatorio,LocalDate referencia,int minDias,String medico) {
        if(minDias<0||minDias>36500)throw new IllegalArgumentException("Tempo minimo invalido");
        var cadastros=cadastro.listar();
        List<Item> todos=new ArrayList<>();
        for(var v:relatorio.visitas()) {
            // Candidates or a hospital link require reconciliation, not a missing-record charge.
            if(!elegivel(v))continue;
            Long id=cadastro.identificar(v,cadastros);
            var c=id==null?null:cadastros.stream().filter(m->m.id()==id).findFirst().orElse(null);
            String nome=c==null?v.medicoEfetivo():c.nome();
            String chave=id!=null?"id:"+id:nome.isBlank()?"sem":"nome:"+LeitorPlanilhaVisitas.normalizar(nome);
            String codigo=v.codigoEfetivo();if(codigo==null||codigo.isBlank())codigo=v.original().getOrDefault("cod produto","");
            todos.add(new Item(v.linhaMedico(),v.original().getOrDefault("id",""),v.atendimento(),v.data(),
                    v.data()==null?null:ChronoUnit.DAYS.between(v.data(),referencia),chave,nome,c==null?"":c.crm()+"/"+c.uf(),
                    codigo,v.procedimentoEfetivo(),v.repasseEfetivo()));
        }
        var grupos=todos.stream().collect(java.util.stream.Collectors.groupingBy(Item::chaveMedico,LinkedHashMap::new,java.util.stream.Collectors.toList()));
        var medicos=grupos.entrySet().stream().map(e->new Grupo(e.getKey(),e.getValue().getFirst().medico(),e.getValue().getFirst().crm(),e.getValue().size()))
                .sorted(Comparator.comparing(Grupo::medico)).toList();
        var filtradas=todos.stream().filter(v->medico.isBlank()||v.chaveMedico().equals(medico))
                .filter(v->minDias==0||(v.dias()!=null&&v.dias()>=minDias))
                .sorted(Comparator.comparing(Item::data,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(Item::linha)).toList();
        return new Resultado(relatorio.hospital(),referencia,todos.size(),medicos,filtradas,
                filtradas.stream().map(Item::repasse).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),
                filtradas.stream().filter(v->v.repasse()==null).count(),filtradas.stream().filter(v->v.medico().isBlank()).count(),
                filtradas.stream().filter(v->v.data()==null).count());
    }
}
