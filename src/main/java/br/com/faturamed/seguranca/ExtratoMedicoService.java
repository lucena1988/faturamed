package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtratoMedicoService {
    public record Filtros(LocalDate visitaDe,LocalDate visitaAte,LocalDate recebimentoDe,LocalDate recebimentoAte) {
        public void validar() {
            if(visitaDe!=null&&visitaAte!=null&&visitaDe.isAfter(visitaAte))throw new IllegalArgumentException("Periodo das visitas invertido");
            if(recebimentoDe!=null&&recebimentoAte!=null&&recebimentoDe.isAfter(recebimentoAte))throw new IllegalArgumentException("Periodo dos recebimentos invertido");
        }
        private static boolean inclui(LocalDate data,LocalDate de,LocalDate ate) {
            return de==null&&ate==null||data!=null&&(de==null||!data.isBefore(de))&&(ate==null||!data.isAfter(ate));
        }
        public boolean visita(LocalDate data) { return inclui(data,visitaDe,visitaAte); }
        public boolean movimento(LocalDate data) { return inclui(data,recebimentoDe,recebimentoAte); }
    }
    public record Visita(int linha,String atendimento,LocalDate data,String codigo,String procedimento,String status,
            BigDecimal valorInformado,BigDecimal faturado,BigDecimal recebido,BigDecimal saldo,String situacao) {}
    public record Resumo(long visitas,BigDecimal faturado,BigDecimal recebidoAcumulado,BigDecimal saldoAtual,
            long pendentes,BigDecimal aguardandoFaturamento,long divergentes,BigDecimal emConferencia,long semValor,
            long financeiroNaoConfirmado,BigDecimal entradasPeriodo,BigDecimal estornosPeriodo,BigDecimal liquidoPeriodo) {}
    public record Extrato(long relatorio,String hospital,String medico,Instant geradoEm,Filtros filtros,Resumo resumo,
            List<Visita> visitas,List<RecebimentoService.Movimento> movimentos) {}
    private final PortalMedicoController portal;
    private final RecebimentoService recebimentos;
    public ExtratoMedicoService(PortalMedicoController portal,RecebimentoService recebimentos) { this.portal=portal;this.recebimentos=recebimentos; }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Extrato buscar(long id,Filtros filtros,UsuarioAutenticado user) throws Exception {
        filtros.validar();var autorizado=portal.buscar(id,user);
        var financeiro=recebimentos.listar(id).visitas().stream().filter(v->Objects.equals(v.medicoId(),user.medicoId())).toList();
        return montar(autorizado,financeiro,recebimentos.movimentosDoMedico(id,user.medicoId()),filtros,autorizado.visitas().getFirst().medico(),Instant.now());
    }
    static Extrato montar(PortalMedicoController.Relatorio relatorio,List<RecebimentoService.Visita> financeiro,
            List<RecebimentoService.Movimento> movimentos,Filtros filtros,String medico,Instant agora) {
        Map<Integer,RecebimentoService.Visita> porLinha=new HashMap<>();financeiro.forEach(v->porLinha.put(v.linha(),v));
        List<Visita> visitas=new ArrayList<>();Set<Integer> linhasConfirmadas=new HashSet<>();
        for(var v:relatorio.visitas())if(filtros.visita(v.data())) {
            var f=porLinha.get(v.linhaMedico());boolean confirmado=f!=null&&!f.inconsistente();
            BigDecimal valor=moeda(v.repasse()),faturado=v.status()==ConciliadorVisitas.Status.PAGA?valor:null;
            BigDecimal recebido=confirmado?f.recebido():null,saldo=confirmado?f.saldo():null;
            String situacao=!confirmado?"CONFERENCIA":faturado==null?"SEM_FATURAMENTO":saldo!=null&&saldo.signum()<=0?"RECEBIDO":recebido!=null&&recebido.signum()>0?"PARCIAL":"A_RECEBER";
            visitas.add(new Visita(v.linhaMedico(),v.atendimento(),v.data(),v.codigoProcedimento(),v.procedimento(),v.status().name(),valor,faturado,recebido,saldo,situacao));
            if(confirmado)linhasConfirmadas.add(v.linhaMedico());
        }
        visitas.sort(Comparator.comparing(Visita::data,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(Visita::linha));
        var eventos=movimentos.stream().filter(e->linhasConfirmadas.contains(e.linha())&&filtros.movimento(e.data()))
                .sorted(Comparator.comparing(RecebimentoService.Movimento::data).thenComparingLong(RecebimentoService.Movimento::id)).toList();
        BigDecimal faturado=BigDecimal.ZERO,recebido=BigDecimal.ZERO,saldo=BigDecimal.ZERO,pendente=BigDecimal.ZERO,divergente=BigDecimal.ZERO,entradas=BigDecimal.ZERO,estornos=BigDecimal.ZERO;
        long pendentes=0,divergentes=0,semValor=0,naoConfirmado=0;
        for(var v:visitas) {
            if(v.faturado()!=null)faturado=faturado.add(v.faturado());
            if(v.recebido()!=null)recebido=recebido.add(v.recebido());else naoConfirmado++;
            if(v.saldo()!=null)saldo=saldo.add(v.saldo());
            if(v.valorInformado()==null)semValor++;
            if(v.status().equals("PENDENTE")){pendentes++;if(v.valorInformado()!=null)pendente=pendente.add(v.valorInformado());}
            if(v.status().equals("DIVERGENTE")){divergentes++;if(v.valorInformado()!=null)divergente=divergente.add(v.valorInformado());}
        }
        for(var e:eventos)if(e.valor().signum()>0)entradas=entradas.add(e.valor());else estornos=estornos.subtract(e.valor());
        return new Extrato(relatorio.id(),relatorio.hospital(),medico,agora,filtros,new Resumo(visitas.size(),faturado,recebido,saldo,pendentes,pendente,divergentes,divergente,semValor,naoConfirmado,entradas,estornos,entradas.subtract(estornos)),List.copyOf(visitas),eventos);
    }
    private static BigDecimal moeda(BigDecimal valor) { return valor==null?null:valor.setScale(2,RoundingMode.HALF_UP); }
}
