package br.com.faturamed.conciliacao;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class IdentificacaoProducaoService {
    private final ConciliadorVisitas motor;
    private final MedicoCadastroService medicos;
    public IdentificacaoProducaoService(ConciliadorVisitas motor, MedicoCadastroService medicos) { this.motor=motor; this.medicos=medicos; }
    public ConciliadorVisitas.Relatorio conciliar(String hospital,String arquivoMedico,String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> producao,List<LeitorPlanilhaVisitas.Linha> faturamento,Long medicoId) {
        if(medicoId==null) return motor.conciliar(hospital,arquivoMedico,arquivoHospital,producao,faturamento);
        var cadastro=medicos.buscar(medicoId);
        if(!cadastro.ativo()) throw new IllegalArgumentException("Selecione um medico ativo para a producao");
        var lista=medicos.listar();
        Map<Integer,LeitorPlanilhaVisitas.Linha> originais=new HashMap<>();
        Map<Integer,ConciliadorVisitas.MedicoProducao> identidades=new HashMap<>();
        var preenchidas=producao.stream().map(l->{
            originais.put(l.numero(),l);
            boolean vazio=l.campo("medico").isBlank();
            boolean conflito=!vazio && !Objects.equals(identificar(l.campo("medico"),lista),medicoId);
            identidades.put(l.numero(),new ConciliadorVisitas.MedicoProducao(cadastro.id(),cadastro.nome(),vazio,conflito));
            if(conflito) return l;
            var campos=new HashMap<>(l.campos());campos.put("medico",cadastro.nome());
            return new LeitorPlanilhaVisitas.Linha(l.numero(),campos);
        }).toList();
        // Canonical names are used only for matching; hospital/source snapshots remain unchanged.
        var normalizadas=faturamento.stream().map(l->{
            if(!Objects.equals(identificar(l.campo("medico"),lista),medicoId)) return l;
            var campos=new HashMap<>(l.campos());campos.put("medico",cadastro.nome());return new LeitorPlanilhaVisitas.Linha(l.numero(),campos);
        }).toList();
        var base=motor.conciliar(hospital,arquivoMedico,arquivoHospital,producao,faturamento);
        Map<Integer,ConciliadorVisitas.RegistroHospital> registros=new HashMap<>();
        base.hospitalSemProducao().forEach(h->registros.put(h.linha(),h));
        base.visitas().forEach(v->v.candidatos().forEach(h->registros.put(h.linha(),h)));
        var calculado=motor.conciliar(hospital,arquivoMedico,arquivoHospital,preenchidas,normalizadas);
        var visitas=calculado.visitas().stream().map(v->{
            var identidade=identidades.get(v.linhaMedico());
            var h=v.hospital()==null?null:registros.get(v.hospital().linha());
            boolean hospitalIncompativel=h!=null && !Objects.equals(identificar(h.medico(),lista),medicoId);
            return new ConciliadorVisitas.Visita(v.linhaMedico(),v.atendimento(),v.data(),
                    identidade.conflito()||hospitalIncompativel?ConciliadorVisitas.Status.DIVERGENTE:v.status(),
                    identidade.conflito()?"Medico informado na linha difere do medico selecionado na importacao; conferir identificacao":
                        hospitalIncompativel?"Identificacao do medico hospitalar nao confirma o cadastro selecionado; conferir identificacao":v.motivo(),
                    originais.get(v.linhaMedico()).campos(),identidade.conflito()||hospitalIncompativel?null:h,
                    registros.values().stream().filter(c->c.atendimento().equals(v.atendimento()) && (v.data()==null || v.data().equals(c.data())))
                        .sorted(Comparator.comparingInt(ConciliadorVisitas.RegistroHospital::linha)).toList(),null,identidade);
        }).toList();
        var resultado=new ConciliadorVisitas.Relatorio(hospital,arquivoMedico,arquivoHospital,calculado.regraStatus(),visitas,
                calculado.hospitalSemProducao().stream().map(h->registros.get(h.linha())).toList(),Map.of(),java.math.BigDecimal.ZERO);
        return new AplicadorRevisaoVisita().recalcular(resultado,visitas);
    }
    private Long identificar(String nome,List<MedicoCadastroService.Medico> lista) {
        var v=new ConciliadorVisitas.Visita(0,"",null,ConciliadorVisitas.Status.PENDENTE,"",Map.of("medico",nome),null,List.of());
        return medicos.identificar(v,lista);
    }
}
