package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import java.math.*;
import java.util.*;

final class FinanceiroPublicado {
    private FinanceiroPublicado() {}
    static List<RecebimentoService.Visita> analisar(PortalMedicoController.Relatorio publicado,List<RecebimentoService.Movimento> movimentos){
        List<RecebimentoService.Visita> result=new ArrayList<>();
        Map<Long,RecebimentoService.Movimento> porId=new HashMap<>();movimentos.forEach(m->porId.put(m.id(),m));
        for(var v:publicado.visitas()){
            BigDecimal recebido=BigDecimal.ZERO;boolean inconsistente=false;long versao=0;
            for(var m:movimentos)if(m.linha()==v.linhaMedico()){
                recebido=recebido.add(m.valor());versao=Math.max(versao,m.id());
                var original=m.estornoDe()==null?m:porId.get(m.estornoDe());
                boolean cancelado=original!=null&&original.estornado()&&(m.estornoDe()==null||m.valor().add(original.valor()).signum()==0);
                if(!mesmaIdentidade(v,m)&&!cancelado)inconsistente=true;
            }
            BigDecimal faturado=v.status()==ConciliadorVisitas.Status.PAGA&&v.repasse()!=null?v.repasse().setScale(2,RoundingMode.HALF_UP):null;
            BigDecimal saldo=faturado==null?null:faturado.subtract(recebido);
            if(recebido.signum()<0||recebido.signum()>0&&(saldo==null||saldo.signum()<0))inconsistente=true;
            result.add(new RecebimentoService.Visita(v.linhaMedico(),v.atendimento(),v.data(),v.medicoCadastroId(),v.medico(),v.codigoProcedimento(),v.status().name(),faturado,recebido,saldo,versao,false,inconsistente,List.of()));
        }
        return List.copyOf(result);
    }
    static boolean mesmaIdentidade(PortalMedicoController.Visita v,RecebimentoService.Movimento m){return v.linhaMedico()==m.linha()&&Objects.equals(v.atendimento(),m.atendimento())&&Objects.equals(v.data(),m.dataVisita())&&Objects.equals(v.codigoProcedimento(),m.codigo());}
}
