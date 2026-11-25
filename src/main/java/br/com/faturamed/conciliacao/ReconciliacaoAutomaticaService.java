package br.com.faturamed.conciliacao;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Depois de importar uma planilha do hospital, reconcilia sozinho as producoes que tem atendimentos nela.
 * A planilha de uma competencia de faturamento (NF) costuma trazer exames de varios meses; cada exame casa
 * com a producao da sua propria competencia, seja ela qual for.
 * Cada producao afetada ganha uma nova versao (revisoes e recebimentos preservados); as ja liberadas ao
 * medico ficam aguardando nova liberacao.
 */
@Service
public class ReconciliacaoAutomaticaService {
    public record Reconciliada(long arquivoId,String nome,String competencia,String medico,long relatorioAnterior,
            Long relatorioNovo,boolean liberada,String erro) {}

    private final JdbcTemplate jdbc;
    private final BaseHospitalarService base;
    private final RevisaoVisitasService revisoes;
    public ReconciliacaoAutomaticaService(JdbcTemplate jdbc,BaseHospitalarService base,RevisaoVisitasService revisoes) {
        this.jdbc=jdbc; this.base=base; this.revisoes=revisoes;
    }

    /** Quantidade de exames da planilha por competencia do procedimento (mes da data do exame). */
    public Map<String,Long> competenciasDosExames(long arquivoHospitalId) {
        Map<String,Long> resultado=new LinkedHashMap<>();
        jdbc.query("select to_char(data_visita,'YYYY-MM') as mes,count(*) as total from visita_base_hospitalar where arquivo_atual_id=? group by 1 order by 1",
                rs->{ resultado.put(rs.getString("mes"),rs.getLong("total")); },arquivoHospitalId);
        return resultado;
    }

    /** Producoes do hospital com algum atendimento (ou, sem atendimento, alguma data) presente na planilha importada. */
    public List<Reconciliada> reconciliarAfetadas(long hospitalId,long arquivoHospitalId) {
        var afetadas=jdbc.queryForList("""
                select a.id,a.nome,a.competencia,a.relatorio_id,coalesce(m.nome,case when a.medico_id is null then 'Varios medicos' end) as medico
                from arquivo_base_hospitalar a left join medico_cadastro m on m.id=a.medico_id
                where a.hospital_id=? and a.tipo='PRODUCAO' and a.relatorio_id is not null
                  and exists(select 1 from conciliacao_visitas c, jsonb_array_elements(c.relatorio->'visitas') item
                             where c.id=a.relatorio_id
                               and (item->>'atendimento' in (select atendimento from visita_base_hospitalar where arquivo_atual_id=?)
                                    or (coalesce(item->>'atendimento','')='' and item->>'data' in (select data_visita::text from visita_base_hospitalar where arquivo_atual_id=?))))
                order by a.competencia,a.id
                """,hospitalId,arquivoHospitalId,arquivoHospitalId);
        List<Reconciliada> resultado=new ArrayList<>();
        for(var producao:afetadas) {
            long arquivoId=((Number)producao.get("id")).longValue();
            long relatorio=((Number)producao.get("relatorio_id")).longValue();
            String nome=(String)producao.get("nome"),competencia=(String)producao.get("competencia"),medico=(String)producao.get("medico");
            boolean liberada=liberada(relatorio);
            try {
                long novo=base.atualizar(relatorio,revisoes.carregar(relatorio).versao());
                resultado.add(new Reconciliada(arquivoId,nome,competencia,medico,relatorio,novo,liberada,null));
            } catch(Exception erro) {
                // Uma producao com conflito nao impede as demais; fica indicada para atualizar manualmente.
                resultado.add(new Reconciliada(arquivoId,nome,competencia,medico,relatorio,null,liberada,erro.getMessage()));
            }
        }
        return resultado;
    }

    private boolean liberada(long relatorio) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                with recursive acima as (select id,anterior_id from conciliacao_visitas where id=?
                    union all select c.id,c.anterior_id from conciliacao_visitas c join acima a on c.id=a.anterior_id)
                select exists(select 1 from liberacao_relatorio l where l.relatorio_raiz_id in (select id from acima) or l.relatorio_id in (select id from acima))
                """,Boolean.class,relatorio));
    }
}
