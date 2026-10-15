package br.com.faturamed.seguranca;

import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NotificacaoService {
    public record Item(String tipo,long evento,String titulo,String hospital,long relatorio,Long solicitacao,
                       Instant criadoEm,Instant lidoEm,String destino) {}
    public record Caixa(long naoLidas,long total,int pagina,int tamanho,List<Item> itens) {}
    private final NamedParameterJdbcTemplate jdbc;
    public NotificacaoService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    // Audience is resolved from the current publication and the authenticated registry ID.
    private static final String EVENTOS="""
        with atuais as (
            select l.* from liberacao_relatorio l where not exists (
                select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
        ), eventos as (
            select 'LIBERACAO' as tipo,l.id as evento,'Relatorio liberado' as titulo,l.hospital,
                l.relatorio_id as relatorio,null::bigint as solicitacao,l.criado_em
            from atuais l where :perfil='MEDICO' and l.dados -> cast(:medico as text) is not null
            union all
            select 'PEDIDO',s.id,'Novo pedido de revisao',l.hospital,l.relatorio_id,s.id,s.criado_em
            from solicitacao_medico s join liberacao_relatorio l on l.id=s.liberacao_id where :perfil='ADMIN'
            union all
            select 'RESPOSTA',s.id,'Solicitacao respondida',l.hospital,a.relatorio_id,s.id,s.respondido_em
            from solicitacao_medico s join liberacao_relatorio l on l.id=s.liberacao_id
            join atuais a on a.relatorio_raiz_id=l.relatorio_raiz_id
            where :perfil='MEDICO' and s.medico_id=:medico and s.resposta is not null
                and a.dados -> cast(:medico as text) is not null
        ), caixa as (
            select e.*,r.lido_em from eventos e left join leitura_notificacao r
            on r.usuario_id=:usuario and r.escopo=:escopo and r.tipo=e.tipo and r.evento_id=e.evento
        )
        """;
    private Map<String,Object> parametros(UsuarioAutenticado user){
        long medico=user.medicoId()==null?0:user.medicoId();
        return new HashMap<>(Map.of("usuario",user.id(),"perfil",user.perfil(),"medico",medico,"escopo",user.perfil()+":"+medico));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Caixa listar(UsuarioAutenticado user,boolean naoLidas,int pagina){
        if(pagina<0||pagina>100000)throw new IllegalArgumentException("Pagina invalida");
        var args=parametros(user);args.put("offset",pagina*20);
        long pendentes=jdbc.queryForObject(EVENTOS+"select count(*) from caixa where lido_em is null",args,Long.class);
        String filtro=naoLidas?" where lido_em is null":"";
        long total=jdbc.queryForObject(EVENTOS+"select count(*) from caixa"+filtro,args,Long.class);
        var itens=jdbc.query(EVENTOS+"select * from caixa"+filtro+" order by criado_em desc,tipo,evento desc limit 20 offset :offset",args,(r,n)->{
            String tipo=r.getString("tipo");long relatorio=r.getLong("relatorio");Long solicitacao=r.getObject("solicitacao",Long.class);
            String destino=tipo.equals("PEDIDO")?"/solicitacoes-medicos.html?pedido="+solicitacao:
                "/portal.html?relatorio="+relatorio+(solicitacao==null?"":"&solicitacao="+solicitacao)+"#conference-title";
            return new Item(tipo,r.getLong("evento"),r.getString("titulo"),r.getString("hospital"),relatorio,solicitacao,
                r.getTimestamp("criado_em").toInstant(),r.getTimestamp("lido_em")==null?null:r.getTimestamp("lido_em").toInstant(),destino);
        });
        return new Caixa(pendentes,total,pagina,20,itens);
    }
    @Transactional
    public void ler(UsuarioAutenticado user,String tipo,long evento){
        if(!List.of("LIBERACAO","PEDIDO","RESPOSTA").contains(tipo)||evento<=0)throw new IllegalArgumentException("Notificacao invalida");
        var args=parametros(user);args.put("tipo",tipo);args.put("evento",evento);
        int inseridos=jdbc.update(EVENTOS+"""
            insert into leitura_notificacao(usuario_id,escopo,tipo,evento_id)
            select :usuario,:escopo,tipo,evento from caixa where tipo=:tipo and evento=:evento
            on conflict (usuario_id,escopo,tipo,evento_id) do update set lido_em=leitura_notificacao.lido_em
            """,args);
        if(inseridos==0)throw new RecursoNaoEncontradoException("Notificacao nao encontrada");
    }
}
