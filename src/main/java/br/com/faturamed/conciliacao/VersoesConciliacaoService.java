package br.com.faturamed.conciliacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersoesConciliacaoService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RevisaoVisitasService revisoes;
    private final AtualizadorConciliacao atualizador;
    public VersoesConciliacaoService(JdbcTemplate jdbc,ObjectMapper json,RevisaoVisitasService revisoes,AtualizadorConciliacao atualizador) {
        this.jdbc=jdbc;this.json=json;this.revisoes=revisoes;this.atualizador=atualizador;
    }
    public record BaseProducao(List<LeitorPlanilhaVisitas.Linha> linhas, Long medicoId, String hash,
            RevisaoVisitasService.Estado estado) {}
    public BaseProducao producaoBase(long id) throws Exception {
        var estado=revisoes.carregar(id);
        var dados=jdbc.queryForMap("select hash_producao,medico_producao_id,linhas_producao from conciliacao_visitas where id=?",id);
        Map<Integer,Integer> origem=dados.get("linhas_producao")==null?Map.of():json.readValue(dados.get("linhas_producao").toString(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<Integer,Integer>>(){});
        var linhas=estado.original().visitas().stream().map(v->new LeitorPlanilhaVisitas.Linha(origem.getOrDefault(v.linhaMedico(),v.linhaMedico()),v.original())).toList();
        Long medico=dados.get("medico_producao_id")==null?null:((Number)dados.get("medico_producao_id")).longValue();
        return new BaseProducao(linhas,medico,Objects.toString(dados.get("hash_producao"),""),estado);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> atualizarFaturamento(long id,long versao,String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> faturamento,String hashHospital) throws Exception {
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",id).isEmpty())
            throw new br.com.faturamed.shared.RecursoNaoEncontradoException("Relatorio base nao encontrado");
        var base=producaoBase(id);var original=base.estado().original();
        return atualizar(id,versao,original.hospital(),original.hospitalCadastroId(),original.arquivoMedico(),arquivoHospital,
                base.linhas(),faturamento,base.hash(),hashHospital,base.medicoId());
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> atualizar(long baseId,long versao,String hospital,Long hospitalId,String arquivoMedico,String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> producao,List<LeitorPlanilhaVisitas.Linha> faturamento,String hashProducao,String hashHospital,Long medicoProducaoId) throws Exception {
        var dados=jdbc.queryForList("select * from conciliacao_visitas where id=? for update",baseId);
        if(dados.isEmpty())throw new br.com.faturamed.shared.RecursoNaoEncontradoException("Relatorio base nao encontrado");
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",baseId).isEmpty())throw new ConflitoRevisaoException("Esse relatorio ja possui nova versao. Selecione a versao mais recente");
        var estado=revisoes.carregar(baseId);var anterior=estado.original();
        if(estado.versao()!=versao)throw new ConflitoRevisaoException("O relatorio recebeu revisoes durante a importacao. Atualize antes de tentar novamente");
        boolean mesmoHospital=anterior.hospitalCadastroId()!=null?Objects.equals(anterior.hospitalCadastroId(),hospitalId)
                : LeitorPlanilhaVisitas.normalizar(anterior.hospital()).equals(LeitorPlanilhaVisitas.normalizar(hospital));
        if(!mesmoHospital)throw new IllegalArgumentException("A nova versao deve pertencer ao mesmo hospital do relatorio base");
        var row=dados.getFirst();
        Long medicoAnterior=row.get("medico_producao_id")==null?null:((Number)row.get("medico_producao_id")).longValue();
        if(!Objects.equals(medicoAnterior,medicoProducaoId)) throw new IllegalArgumentException("Mantenha a identificacao da producao do relatorio base. Para mudar o medico, crie um novo relatorio");
        if(hashProducao.equals(row.get("hash_producao"))&&hashHospital.equals(row.get("hash_hospital")))return Map.of("id",baseId,"relatorio",estado.atual(),"semAlteracoes",true);
        var resultado=atualizador.atualizar(estado,arquivoMedico,arquivoHospital,producao,faturamento);
        int numero=((Number)row.get("numero_versao")).intValue()+1;
        Long id=jdbc.queryForObject("""
                insert into conciliacao_visitas(relatorio,anterior_id,numero_versao,hash_producao,hash_hospital,linhas_producao,medico_producao_id)
                values(?::jsonb,?,?,?,?,?::jsonb,?) returning id
                """,Long.class,json.writeValueAsString(resultado.relatorio()),baseId,numero,hashProducao,hashHospital,json.writeValueAsString(resultado.linhasProducao()),medicoProducaoId);
        jdbc.update("""
                insert into revisao_visita(conciliacao_id,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em)
                select ?,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em from revisao_visita where conciliacao_id=? order by id
                """,id,baseId);
        var atual=revisoes.carregar(id).atual();
        return Map.of("id",id,"relatorio",atual,"numeroVersao",numero,"preservadas",resultado.preservadas(),
                "reavaliadas",resultado.reavaliadas(),"novas",resultado.novas(),"semAlteracoes",false,
                "resumoAtualizacao",ResumoAtualizacao.comparar(estado.atual(),atual));
    }
}
