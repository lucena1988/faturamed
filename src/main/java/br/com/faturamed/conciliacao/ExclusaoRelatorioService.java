package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exclui definitivamente um relatorio, todas as suas versoes e tudo o que depende delas. */
@Service
public class ExclusaoRelatorioService {
    public record Resultado(long relatorioRaizId,List<Long> relatorios,String hospital,int publicacoes,int solicitacoes,int recebimentos) {}
    private final JdbcTemplate jdbc;
    public ExclusaoRelatorioService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public Resultado excluir(long id,UsuarioAutenticado usuario) {
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",id).isEmpty())
            throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        long raiz=jdbc.queryForObject("""
            with recursive acima as (select id,anterior_id from conciliacao_visitas where id=?
            union all select c.id,c.anterior_id from conciliacao_visitas c join acima a on c.id=a.anterior_id)
            select id from acima where anterior_id is null
            """,Long.class,id);
        List<Long> cadeia=jdbc.queryForList("""
            with recursive cadeia as (select id from conciliacao_visitas where id=?
            union all select c.id from conciliacao_visitas c join cadeia k on c.anterior_id=k.id)
            select id from cadeia order by id
            """,Long.class,raiz);
        String ids=cadeia.stream().map(String::valueOf).collect(Collectors.joining(","));
        // Bloqueia a cadeia inteira para que nenhuma gravacao concorrente sobreviva a exclusao.
        jdbc.queryForList("select id from conciliacao_visitas where id = any(?::bigint[]) for update","{"+ids+"}");
        var dados=jdbc.queryForMap("select relatorio->>'hospital' as hospital,relatorio->>'arquivoMedico' as arquivo,medico_producao_id,empresa_id from conciliacao_visitas where id=?",cadeia.getLast());
        Object[] cadeiaArg={"{"+ids+"}"};
        String naCadeia="any(?::bigint[])";
        int publicacoes=jdbc.queryForObject("select count(*) from liberacao_relatorio where relatorio_raiz_id="+naCadeia,Integer.class,cadeiaArg);
        int solicitacoes=jdbc.queryForObject("select count(*) from solicitacao_medico s join liberacao_relatorio l on l.id=s.liberacao_id where l.relatorio_raiz_id="+naCadeia,Integer.class,cadeiaArg);
        int recebimentos=jdbc.queryForObject("select count(*) from recebimento_visita where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia,Integer.class,cadeiaArg[0],cadeiaArg[0]);

        jdbc.update("delete from ciencia_relatorio where liberacao_id in (select id from liberacao_relatorio where relatorio_raiz_id="+naCadeia+")",cadeiaArg);
        jdbc.update("delete from solicitacao_medico where liberacao_id in (select id from liberacao_relatorio where relatorio_raiz_id="+naCadeia+")",cadeiaArg);
        jdbc.update("delete from liberacao_relatorio where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia,cadeiaArg[0],cadeiaArg[0]);
        jdbc.update("delete from pagamento_comprovante where pagamento_id in (select id from pagamento_visitas where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia+")",cadeiaArg[0],cadeiaArg[0]);
        jdbc.update("delete from recebimento_visita where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia,cadeiaArg[0],cadeiaArg[0]);
        jdbc.update("delete from pagamento_visitas where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia,cadeiaArg[0],cadeiaArg[0]);
        jdbc.update("delete from cobranca_contato where relatorio_raiz_id="+naCadeia+" or relatorio_id="+naCadeia,cadeiaArg[0],cadeiaArg[0]);
        // As visitas do hospital permanecem; apenas deixam de estar reservadas para esta producao.
        jdbc.update("delete from vinculo_base_hospitalar where relatorio_id="+naCadeia,cadeiaArg);
        jdbc.update("delete from reserva_base_hospitalar where relatorio_raiz_id="+naCadeia,cadeiaArg);
        // O arquivo de producao sai junto, para que a mesma planilha possa ser importada de novo.
        jdbc.update("""
            delete from arquivo_base_hospitalar a where a.tipo='PRODUCAO' and a.relatorio_id=%s
            and not exists(select 1 from visita_base_hospitalar v where v.arquivo_id=a.id or v.arquivo_atual_id=a.id)
            and not exists(select 1 from atualizacao_visita_hospitalar u where u.arquivo_id=a.id)
            """.formatted(naCadeia),cadeiaArg);
        jdbc.update("update arquivo_base_hospitalar set relatorio_id=null where relatorio_id="+naCadeia,cadeiaArg);
        jdbc.update("delete from revisao_visita where conciliacao_id="+naCadeia,cadeiaArg);
        jdbc.update("delete from conciliacao_visitas where id="+naCadeia,cadeiaArg);

        String hospital=Objects.toString(dados.get("hospital"),"");
        jdbc.update("""
            insert into relatorio_exclusao_auditoria(relatorio_raiz_id,relatorios,hospital,arquivo_medico,medico_producao_id,empresa_id,publicacoes,solicitacoes,recebimentos,usuario_id,responsavel)
            values(?,?,?,?,?,?,?,?,?,?,?)
            """,raiz,ids,hospital,dados.get("arquivo"),dados.get("medico_producao_id"),dados.get("empresa_id"),publicacoes,solicitacoes,recebimentos,
            usuario.id(),usuario.nome()+" ("+usuario.email()+")");
        return new Resultado(raiz,cadeia,hospital,publicacoes,solicitacoes,recebimentos);
    }
}
