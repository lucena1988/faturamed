package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conciliacao unica: ajustes manuais sobre a base da producao, sempre com justificativa e historico.
 * - Correcao da competencia de uma visita (a derivada da data continua guardada e pode ser restaurada).
 * - Decisao sobre repeticoes em analise: visita distinta (volta a ser conciliavel) ou duplicada (sai da conciliacao).
 * Visitas que ja estao em um relatorio nao mudam aqui: o relatorio e a fonte a partir da geracao.
 */
@Service
public class AjusteProducaoService {
    private final JdbcTemplate jdbc;
    private final VinculoBaseHospitalar vinculos;
    private final ConciliacaoUnicaService conciliacao;

    public AjusteProducaoService(JdbcTemplate jdbc, VinculoBaseHospitalar vinculos, ConciliacaoUnicaService conciliacao) {
        this.jdbc=jdbc; this.vinculos=vinculos; this.conciliacao=conciliacao;
    }

    /** competencia em branco restaura a derivada da data do atendimento. */
    @Transactional(rollbackFor=Exception.class)
    public void corrigirCompetencia(long visitaId,String competencia,String justificativa,UsuarioAutenticado usuario) {
        BaseHospitalarService.validarTexto(justificativa,"Justificativa");
        var visita=travar(visitaId);
        if("DUPLICADA".equals(visita.get("decisao"))) throw new ConflitoRevisaoException("Visita duplicada nao tem competencia a corrigir");
        if(visita.get("arquivo_relatorio_id")!=null) throw new ConflitoRevisaoException("A visita ja esta em um relatorio. Ajuste pelo relatorio");
        String derivada=visita.get("competencia").toString();
        String atual=visita.get("competencia_manual")==null?derivada:visita.get("competencia_manual").toString();
        boolean restaurar=competencia==null || competencia.isBlank();
        String nova=restaurar?derivada:BaseHospitalarService.normalizarCompetencia(competencia);
        if(nova.equals(atual)) throw new IllegalArgumentException("A visita ja esta nessa competencia");
        jdbc.update("update producao_visita set competencia_manual=? where id=?",nova.equals(derivada)?null:nova,visitaId);
        registrar(visitaId,nova.equals(derivada)?"RESTAURAR_COMPETENCIA":"CORRIGIR_COMPETENCIA",atual,nova,justificativa,usuario);
    }

    @Transactional(rollbackFor=Exception.class)
    public void decidirRepeticao(long visitaId,String acao,String justificativa,UsuarioAutenticado usuario) throws Exception {
        if(!List.of("VISITA_DISTINTA","DUPLICADA").contains(Objects.toString(acao,""))) throw new IllegalArgumentException("Decisao invalida");
        BaseHospitalarService.validarTexto(justificativa,"Justificativa");
        var visita=travar(visitaId);
        if(!"EM_ANALISE".equals(visita.get("decisao"))) throw new ConflitoRevisaoException("Esta ocorrencia ja foi analisada. Atualize a tela");
        long hospitalId=((Number)visita.get("hospital_id")).longValue();
        if(acao.equals("DUPLICADA")) {
            jdbc.update("update producao_visita set decisao='DUPLICADA',situacao='DUPLICADA',motivo=?,hospital_visita_id=null where id=?",
                    "Duplicidade confirmada"+(visita.get("anterior_id")==null?"":" da visita ID "+visita.get("anterior_id"))+"; fora da conciliacao",visitaId);
            registrar(visitaId,"DUPLICADA","EM_ANALISE","DUPLICADA",justificativa,usuario);
        } else {
            jdbc.update("update producao_visita set decisao='ORIGINAL',situacao='PENDENTE',motivo='Confirmada como visita distinta; aguardando registro do hospital' where id=?",visitaId);
            registrar(visitaId,"VISITA_DISTINTA","EM_ANALISE","ORIGINAL",justificativa,usuario);
            // Passa a competir por um registro do hospital como as demais visitas.
            conciliacao.conciliar(hospitalId);
        }
    }

    public List<Map<String,Object>> historico(long visitaId) {
        if(jdbc.queryForObject("select count(*) from producao_visita where id=?",Long.class,visitaId)==0) throw new RecursoNaoEncontradoException("Visita nao encontrada");
        return jdbc.queryForList("select id,acao,antes,depois,justificativa,responsavel,criado_em from producao_visita_historico where visita_id=? order by id",visitaId);
    }

    private Map<String,Object> travar(long visitaId) {
        var existe=jdbc.queryForList("select hospital_id from producao_visita where id=?",visitaId);
        if(existe.isEmpty()) throw new RecursoNaoEncontradoException("Visita nao encontrada");
        vinculos.bloquear(((Number)existe.getFirst().get("hospital_id")).longValue());
        return jdbc.queryForMap("select * from producao_visita where id=? for update",visitaId);
    }

    private void registrar(long visitaId,String acao,String antes,String depois,String justificativa,UsuarioAutenticado usuario) {
        jdbc.update("insert into producao_visita_historico(visita_id,acao,antes,depois,justificativa,usuario_id,responsavel) values(?,?,?,?,?,?,?)",
                visitaId,acao,antes,depois,justificativa.trim(),usuario.id(),usuario.nome()+" <"+usuario.email()+">");
    }
}
