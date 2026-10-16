package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AcompanhamentoBaseHospitalarService {
    public enum Etapa { AGUARDANDO_PRODUCAO, AGUARDANDO_FATURAMENTO, FATURADO_SEM_RECEBIMENTO, RECEBIDO_PARCIALMENTE, SEM_SALDO, EM_CONFERENCIA }
    public record Item(int visitaId,String atendimento,LocalDate data,String medico,Etapa etapa,Long relatorio,Integer linha,
            BigDecimal faturado,BigDecimal recebido,BigDecimal saldo) {}
    public record Resultado(Map<Etapa,Long> etapas,long visitasDistintas,long ocorrenciasEmAnalise,long duplicadas,
            long producaoSemBase,BigDecimal faturado,BigDecimal recebido,BigDecimal saldo,List<Item> visitas) {}
    private final JdbcTemplate jdbc;
    private final BaseHospitalarService base;
    private final RevisaoVisitasService revisoes;
    private final RecebimentoService recebimentos;
    public AcompanhamentoBaseHospitalarService(JdbcTemplate jdbc,BaseHospitalarService base,RevisaoVisitasService revisoes,RecebimentoService recebimentos) {
        this.jdbc=jdbc; this.base=base; this.revisoes=revisoes; this.recebimentos=recebimentos;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Resultado consultar(long hospitalId) throws Exception {
        Map<String,RecebimentoService.Visita> financeiro=new HashMap<>();
        long producaoSemBase=0;
        for(long relatorio:jdbc.queryForList("select relatorio_id from arquivo_base_hospitalar where hospital_id=? and tipo='PRODUCAO' and relatorio_id is not null",Long.class,hospitalId)) {
            for(var visita:recebimentos.listar(relatorio).visitas()) financeiro.put(relatorio+":"+visita.linha(),visita);
            producaoSemBase+=revisoes.carregar(relatorio).atual().visitas().stream().filter(FilaCobranca::elegivel).count();
        }
        var reservadas=new HashSet<>(jdbc.queryForList("select r.visita_id from reserva_base_hospitalar r join visita_base_hospitalar v on v.id=r.visita_id where v.hospital_id=?",Integer.class,hospitalId));
        List<Item> itens=new ArrayList<>();
        long emAnalise=0,duplicadas=0;
        for(var visita:base.visitas(hospitalId)) {
            if(visita.get("decisao").equals("EM_ANALISE")) { emAnalise++; continue; }
            if(visita.get("decisao").equals("DUPLICADO_PAGO_OUTRO_MES")) { duplicadas++; continue; }
            int id=((Number)visita.get("id")).intValue();
            Long relatorio=visita.get("relatorio_id")==null?null:((Number)visita.get("relatorio_id")).longValue();
            Integer linha=visita.get("linha_producao")==null?null:((Number)visita.get("linha_producao")).intValue();
            var saldo=relatorio==null?null:financeiro.get(relatorio+":"+linha);
            Etapa etapa;
            if(visita.get("situacao").equals("DIVERGENTE") || saldo!=null && saldo.inconsistente() || saldo==null && reservadas.contains(id)) etapa=Etapa.EM_CONFERENCIA;
            else if(saldo==null) etapa=Etapa.AGUARDANDO_PRODUCAO;
            else if(saldo.status().equals("PENDENTE")) etapa=Etapa.AGUARDANDO_FATURAMENTO;
            else if(saldo.faturado()==null || saldo.saldo()==null || !saldo.status().equals("PAGA")) etapa=Etapa.EM_CONFERENCIA;
            else if(saldo.saldo().signum()==0) etapa=Etapa.SEM_SALDO;
            else if(saldo.recebido().signum()>0) etapa=Etapa.RECEBIDO_PARCIALMENTE;
            else etapa=Etapa.FATURADO_SEM_RECEBIMENTO;
            boolean confirmado=saldo!=null && !saldo.inconsistente() && saldo.faturado()!=null && etapa!=Etapa.EM_CONFERENCIA;
            itens.add(new Item(id,visita.get("atendimento").toString(),((java.sql.Date)visita.get("data_visita")).toLocalDate(),
                    visita.get("medico_nome").toString(),etapa,relatorio,linha,confirmado?saldo.faturado():null,
                    confirmado?saldo.recebido():null,confirmado?saldo.saldo():null));
        }
        Map<Etapa,Long> contagens=new EnumMap<>(Etapa.class);
        for(var etapa:Etapa.values()) contagens.put(etapa,itens.stream().filter(item->item.etapa()==etapa).count());
        return new Resultado(contagens,itens.size(),emAnalise,duplicadas,producaoSemBase,
                itens.stream().map(Item::faturado).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),
                itens.stream().map(Item::recebido).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),
                itens.stream().map(Item::saldo).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),List.copyOf(itens));
    }
}
