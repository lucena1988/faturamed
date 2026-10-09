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
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> atualizar(long baseId,long versao,String hospital,Long hospitalId,String arquivoMedico,String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> producao,List<LeitorPlanilhaVisitas.Linha> faturamento,String hashProducao,String hashHospital) throws Exception {
        var dados=jdbc.queryForList("select * from conciliacao_visitas where id=? for update",baseId);
        if(dados.isEmpty())throw new br.com.faturamed.shared.RecursoNaoEncontradoException("Relatorio base nao encontrado");
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",baseId).isEmpty())throw new ConflitoRevisaoException("Esse relatorio ja possui nova versao. Selecione a versao mais recente");
        var estado=revisoes.carregar(baseId);var anterior=estado.original();
        if(estado.versao()!=versao)throw new ConflitoRevisaoException("O relatorio recebeu revisoes durante a importacao. Atualize antes de tentar novamente");
        boolean mesmoHospital=anterior.hospitalCadastroId()!=null?Objects.equals(anterior.hospitalCadastroId(),hospitalId)
                : LeitorPlanilhaVisitas.normalizar(anterior.hospital()).equals(LeitorPlanilhaVisitas.normalizar(hospital));
        if(!mesmoHospital)throw new IllegalArgumentException("A nova versao deve pertencer ao mesmo hospital do relatorio base");
        var row=dados.getFirst();
        if(hashProducao.equals(row.get("hash_producao"))&&hashHospital.equals(row.get("hash_hospital")))return Map.of("id",baseId,"relatorio",estado.atual(),"semAlteracoes",true);
        var resultado=atualizador.atualizar(estado,arquivoMedico,arquivoHospital,producao,faturamento);
        int numero=((Number)row.get("numero_versao")).intValue()+1;
        Long id=jdbc.queryForObject("""
                insert into conciliacao_visitas(relatorio,anterior_id,numero_versao,hash_producao,hash_hospital,linhas_producao)
                values(?::jsonb,?,?,?,?,?::jsonb) returning id
                """,Long.class,json.writeValueAsString(resultado.relatorio()),baseId,numero,hashProducao,hashHospital,json.writeValueAsString(resultado.linhasProducao()));
        jdbc.update("""
                insert into revisao_visita(conciliacao_id,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em)
                select ?,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em from revisao_visita where conciliacao_id=? order by id
                """,id,baseId);
        return Map.of("id",id,"relatorio",revisoes.carregar(id).atual(),"numeroVersao",numero,"preservadas",resultado.preservadas(),
                "reavaliadas",resultado.reavaliadas(),"novas",resultado.novas(),"semAlteracoes",false);
    }
}
