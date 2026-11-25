package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conciliacao unica: exclui uma importacao de producao (por engano ou na planilha errada).
 * So e permitido enquanto nenhuma visita dela estiver em um relatorio: relatorios tem liberacao, revisoes e
 * recebimentos e se excluem em Relatorios e revisoes. Repeticoes de outras importacoes que apontavam para uma
 * visita excluida passam a ser a visita original, e os registros do hospital liberados voltam a conciliar.
 */
@Service
public class ExclusaoProducaoService {
    public record Resultado(long arquivoId,String nome,int visitas,int repeticoesPromovidas) {}

    private final JdbcTemplate jdbc;
    private final VinculoBaseHospitalar vinculos;
    private final ConciliacaoUnicaService conciliacao;

    public ExclusaoProducaoService(JdbcTemplate jdbc,VinculoBaseHospitalar vinculos,ConciliacaoUnicaService conciliacao) {
        this.jdbc=jdbc; this.vinculos=vinculos; this.conciliacao=conciliacao;
    }

    public List<Map<String,Object>> listar(long hospitalId) {
        return jdbc.queryForList("""
                select a.id,a.nome,a.criado_em,m.nome as medico,count(v.id) as visitas,
                       count(v.id) filter (where v.arquivo_relatorio_id is not null) as em_relatorio,
                       string_agg(distinct coalesce(v.competencia_manual,v.competencia),',' order by coalesce(v.competencia_manual,v.competencia)) as competencias
                from producao_arquivo a
                left join medico_cadastro m on m.id=a.medico_id
                left join producao_visita v on v.arquivo_id=a.id
                where a.hospital_id=?
                group by a.id,a.nome,a.criado_em,m.nome
                order by a.id desc
                """,hospitalId);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado excluir(long arquivoId,UsuarioAutenticado usuario) throws Exception {
        var lista=jdbc.queryForList("select hospital_id,nome from producao_arquivo where id=?",arquivoId);
        if(lista.isEmpty()) throw new RecursoNaoEncontradoException("Importacao nao encontrada");
        long hospitalId=((Number)lista.getFirst().get("hospital_id")).longValue();
        String nome=lista.getFirst().get("nome").toString();
        vinculos.bloquear(hospitalId);
        Long emRelatorio=jdbc.queryForObject("select count(*) from producao_visita where arquivo_id=? and arquivo_relatorio_id is not null",Long.class,arquivoId);
        if(emRelatorio!=null && emRelatorio>0)
            throw new IllegalArgumentException("Nao foi possivel excluir: "+emRelatorio+(emRelatorio==1?" visita desta importacao esta":" visitas desta importacao estao")
                    +" em relatorios. Exclua antes o relatorio em Relatorios e revisoes.");
        String daImportacao="(select id from producao_visita where arquivo_id=?)";
        int visitas=jdbc.queryForObject("select count(*) from producao_visita where arquivo_id=?",Integer.class,arquivoId);
        // Repeticao cuja original sai passa a ser a original; as demais apenas perdem a referencia.
        int promovidas=jdbc.update("""
                update producao_visita set decisao='ORIGINAL',situacao='PENDENTE',anterior_id=null,
                    motivo='Visita original excluida; esta visita passou a ser a original'
                where arquivo_id<>? and decisao='EM_ANALISE' and anterior_id in
                """+daImportacao,arquivoId,arquivoId);
        jdbc.update("update producao_visita set anterior_id=null where arquivo_id<>? and anterior_id in "+daImportacao,arquivoId,arquivoId);
        jdbc.update("delete from producao_visita_historico where visita_id in "+daImportacao,arquivoId);
        jdbc.update("delete from producao_visita where arquivo_id=?",arquivoId);
        jdbc.update("delete from producao_arquivo where id=?",arquivoId);
        jdbc.update("insert into producao_arquivo_exclusao_auditoria(arquivo_id,hospital_id,nome,visitas,repeticoes_promovidas,usuario_id,responsavel) values(?,?,?,?,?,?,?)",
                arquivoId,hospitalId,nome,visitas,promovidas,usuario.id(),usuario.nome()+" ("+usuario.email()+")");
        // Registros do hospital liberados voltam a conciliar e repeticoes promovidas passam a competir por eles.
        conciliacao.conciliar(hospitalId);
        return new Resultado(arquivoId,nome,visitas,promovidas);
    }
}
