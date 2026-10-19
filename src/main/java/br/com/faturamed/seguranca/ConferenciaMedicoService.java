package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ConferenciaMedicoService {
    public record Pedido(@Positive long publicacao, @Positive int linha, @NotBlank @Size(min=5,max=2000) String motivo) {}
    public record Ciencia(@Positive long publicacao) {}
    public record Resposta(@NotBlank @Size(min=5,max=2000) String resposta) {}
    public record Item(long id,long publicacao,long relatorio,String hospital,String medico,int linha,
                       String atendimento,String data,String procedimento,String codigo,String motivo,Instant criadoEm,
                       String resposta,Instant respondidoEm,boolean publicacaoAtual) {}
    public record Estado(long publicacao,Instant cienteEm,List<PortalMedicoController.Visita> visitas,List<Item> solicitacoes) {}
    public record Leitura(long publicacao,long relatorio,String hospital,String medico,Instant cienteEm) {}
    private final JdbcTemplate jdbc;
    private final LiberacaoRelatorioService liberacoes;
    public ConferenciaMedicoService(JdbcTemplate jdbc,LiberacaoRelatorioService liberacoes){this.jdbc=jdbc;this.liberacoes=liberacoes;}

    private static final String ITENS="""
        select s.id,s.liberacao_id,l.relatorio_id,l.hospital,v.d->>'medico' as medico,s.linha,
        v.d->>'atendimento' as atendimento,v.d->>'data' as data,v.d->>'procedimento' as procedimento,
        v.d->>'codigoProcedimento' as codigo,s.motivo,s.criado_em,s.resposta,s.respondido_em,
        not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id) as atual
        from solicitacao_medico s join liberacao_relatorio l on l.id=s.liberacao_id
        cross join lateral jsonb_array_elements(l.dados -> cast(s.medico_id as text)) v(d)
        where (v.d->>'linhaMedico')::integer=s.linha
        """;
    private List<Item> itens(String filtro,Object...args){
        return jdbc.query(ITENS+filtro+" order by s.id desc limit 500",(r,n)->new Item(r.getLong("id"),r.getLong("liberacao_id"),r.getLong("relatorio_id"),r.getString("hospital"),r.getString("medico"),r.getInt("linha"),r.getString("atendimento"),r.getString("data"),r.getString("procedimento"),r.getString("codigo"),r.getString("motivo"),r.getTimestamp("criado_em").toInstant(),r.getString("resposta"),r.getTimestamp("respondido_em")==null?null:r.getTimestamp("respondido_em").toInstant(),r.getBoolean("atual")),args);
    }
    private long publicacao(long relatorio,long medico) throws Exception {
        liberacoes.buscarDoMedico(relatorio,medico);
        return jdbc.queryForObject("""
            select l.id from liberacao_relatorio l where l.relatorio_id=?
            and l.dados -> cast(? as text) is not null
            and not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
            """,Long.class,relatorio,String.valueOf(medico));
    }
    private void bloquearCadeia(long publicacao){
        if(jdbc.queryForList("select r.id from conciliacao_visitas r join liberacao_relatorio l on l.relatorio_raiz_id=r.id where l.id=? for update of r",publicacao).isEmpty())
            throw new RecursoNaoEncontradoException("Publicacao nao encontrada");
    }
    private void validarAtual(long relatorio,long publicacao,long medico) throws Exception {
        bloquearCadeia(publicacao);
        if(publicacao(relatorio,medico)!=publicacao)throw new ConflitoRevisaoException("Uma nova publicacao esta disponivel. Atualize o relatorio antes de continuar");
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Estado consultar(long relatorio,long medico) throws Exception {
        long p=publicacao(relatorio,medico);
        var datas=jdbc.query("select criado_em from ciencia_relatorio where liberacao_id=? and medico_id=?",(r,n)->r.getTimestamp(1).toInstant(),p,medico);
        var pedidos=itens(" and s.medico_id=? and l.relatorio_raiz_id=(select relatorio_raiz_id from liberacao_relatorio where id=?)",medico,p);
        return new Estado(p,datas.isEmpty()?null:datas.getFirst(),liberacoes.buscarDoMedico(relatorio,medico).visitas(),pedidos);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Item pedidoDoMedico(long relatorio,long medico,long pedido) throws Exception {
        long p=publicacao(relatorio,medico);
        var itens=itens(" and s.id=? and s.medico_id=? and l.relatorio_raiz_id=(select relatorio_raiz_id from liberacao_relatorio where id=?)",pedido,medico,p);
        if(itens.isEmpty())throw new RecursoNaoEncontradoException("Solicitacao nao encontrada");
        return itens.getFirst();
    }
    @Transactional(readOnly=true)
    public Item pedidoAdministrativo(long pedido){
        var itens=itens(" and s.id=?",pedido);
        if(itens.isEmpty())throw new RecursoNaoEncontradoException("Solicitacao nao encontrada");
        return itens.getFirst();
    }
    @Transactional(rollbackFor=Exception.class)
    public void ciencia(long relatorio,Ciencia dados,UsuarioAutenticado user) throws Exception {
        validarAtual(relatorio,dados.publicacao(),user.medicoId());
        jdbc.update("insert into ciencia_relatorio(liberacao_id,medico_id,usuario_id) values(?,?,?) on conflict do nothing",dados.publicacao(),user.medicoId(),user.id());
    }
    @Transactional(rollbackFor=Exception.class)
    public void solicitar(long relatorio,Pedido dados,UsuarioAutenticado user) throws Exception {
        texto(dados.motivo());validarAtual(relatorio,dados.publicacao(),user.medicoId());
        var publicado=liberacoes.buscarDoMedico(relatorio,user.medicoId());
        if(publicado.visitas().stream().noneMatch(v->v.linhaMedico()==dados.linha()))throw new RecursoNaoEncontradoException("Visita nao encontrada");
        int inseridos=jdbc.update("insert into solicitacao_medico(liberacao_id,medico_id,linha,motivo,usuario_id) values(?,?,?,?,?) on conflict do nothing",dados.publicacao(),user.medicoId(),dados.linha(),dados.motivo().trim(),user.id());
        if(inseridos==0)throw new ConflitoRevisaoException("Ja existe um pedido aberto para esta visita e publicacao");
    }
    @Transactional(readOnly=true)
    public List<Leitura> ciencias(){
        return jdbc.query("""
            select l.id,l.relatorio_id,l.hospital,p.value->0->>'medico' as medico,c.criado_em
            from liberacao_relatorio l cross join lateral jsonb_each(l.dados) p
            left join ciencia_relatorio c on c.liberacao_id=l.id and c.medico_id=p.key::bigint
            where not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
            order by l.id desc,p.key limit 500
            """,(r,n)->new Leitura(r.getLong("id"),r.getLong("relatorio_id"),r.getString("hospital"),r.getString("medico"),r.getTimestamp("criado_em")==null?null:r.getTimestamp("criado_em").toInstant()));
    }
    @Transactional(readOnly=true)
    public List<Item> listar(String situacao){
        if(!List.of("ABERTAS","RESPONDIDAS","TODAS").contains(situacao))throw new IllegalArgumentException("Situacao invalida");
        return itens(switch(situacao){case "ABERTAS"->" and s.resposta is null";case "RESPONDIDAS"->" and s.resposta is not null";default->"";});
    }
    @Transactional
    public void responder(long id,Resposta dados,UsuarioAutenticado user){
        texto(dados.resposta());
        var rows=jdbc.queryForList("select id,resposta from solicitacao_medico where id=? for update",id);
        if(rows.isEmpty())throw new RecursoNaoEncontradoException("Solicitacao nao encontrada");
        if(rows.getFirst().get("resposta")!=null)throw new ConflitoRevisaoException("Esta solicitacao ja foi respondida. Atualize a fila");
        jdbc.update("update solicitacao_medico set resposta=?,respondido_por=?,respondido_em=now() where id=?",dados.resposta().trim(),user.id(),id);
    }
    private static void texto(String texto){if(texto==null||texto.trim().length()<5||texto.length()>2000)throw new IllegalArgumentException("Informe um texto de 5 a 2000 caracteres");}
}
