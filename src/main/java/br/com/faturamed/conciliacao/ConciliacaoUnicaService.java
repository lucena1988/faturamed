package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conciliacao unica, etapa 2: vincula visitas da producao aos registros do hospital em qualquer ordem de
 * importacao. Reaproveita o motor atual (IdentificacaoProducaoService) por medico, entao valores, repasse,
 * disputas e refinamento por codigo/procedimento seguem as mesmas regras do fluxo em duas etapas.
 * Vinculos ja feitos nao sao desfeitos por uma nova importacao; so visitas ainda sem vinculo sao recalculadas.
 */
@Service
public class ConciliacaoUnicaService {
    public record Resumo(int vinculadas, int divergentes, int pendentes, int medicosInferidos) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HospitalCadastroService hospitais;
    private final MedicoCadastroService medicos;
    private final IdentificacaoProducaoService identificacao;
    private final RepasseProcedimentoService repasses;
    private final VinculoBaseHospitalar vinculos;
    private final BaseHospitalarService base;

    public ConciliacaoUnicaService(JdbcTemplate jdbc, ObjectMapper json, HospitalCadastroService hospitais, MedicoCadastroService medicos,
            IdentificacaoProducaoService identificacao, RepasseProcedimentoService repasses, VinculoBaseHospitalar vinculos, BaseHospitalarService base) {
        this.jdbc=jdbc; this.json=json; this.hospitais=hospitais; this.medicos=medicos; this.identificacao=identificacao;
        this.repasses=repasses; this.vinculos=vinculos; this.base=base;
    }

    /** Importa o arquivo do hospital e concilia na mesma transacao. A competencia e a do faturamento (NF). */
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> importarHospital(long hospitalId,String competencia,String modoHospital,String tipoProcedimento,
            MultipartFile arquivo,UsuarioAutenticado usuario) throws Exception {
        var resultado=base.importarHospital(hospitalId,competencia,modoHospital,arquivo,usuario);
        Map<String,Object> resposta=new LinkedHashMap<>();
        resposta.put("arquivoId",resultado.arquivoId()); resposta.put("repetido",resultado.repetido());
        resposta.put("visitas",resultado.visitas()); resposta.put("divergencias",resultado.divergencias());
        resposta.put("atualizadas",resultado.atualizadas()); resposta.put("foraDaCompetencia",resultado.foraDaCompetencia());
        if(!resultado.repetido()) {
            base.definirTipoArquivo(resultado.arquivoId(),tipoProcedimento);
            resposta.put("conciliacao",conciliar(hospitalId));
        }
        return resposta;
    }

    @Transactional(rollbackFor=Exception.class)
    public Resumo conciliar(long hospitalId) throws Exception {
        vinculos.bloquear(hospitalId);
        String nomeHospital=hospitais.buscar(hospitalId).nome();
        var cadastros=medicos.listar();
        var abertas=jdbc.queryForList("""
                select id,atendimento,data_visita,medico_id,campos::text as campos from producao_visita
                where hospital_id=? and decisao='ORIGINAL' and hospital_visita_id is null order by id
                """,hospitalId);
        if(abertas.isEmpty()) return new Resumo(0,0,0,0);
        Set<Integer> indisponiveis=vinculos.indisponiveis(hospitalId);
        Map<Integer,Map<String,Object>> hospitalPorId=new LinkedHashMap<>();
        for(var registro:jdbc.queryForList("select id,atendimento,data_visita,medico_nome,campos::text as campos from visita_base_hospitalar where hospital_id=? and decisao in ('ORIGINAL','VISITA_DISTINTA') order by id",hospitalId)) {
            int id=((Number)registro.get("id")).intValue();
            if(!indisponiveis.contains(id)) hospitalPorId.put(id,registro);
        }
        Map<String,List<Map<String,Object>>> porChave=new HashMap<>();
        hospitalPorId.values().forEach(h->porChave.computeIfAbsent(h.get("atendimento")+"|"+h.get("data_visita"),k->new ArrayList<>()).add(h));

        int inferidos=0;
        Map<Long,List<Map<String,Object>>> grupos=new LinkedHashMap<>();
        List<Map<String,Object>> semMedico=new ArrayList<>();
        for(var visita:abertas) {
            Long medico=visita.get("medico_id")==null?null:((Number)visita.get("medico_id")).longValue();
            long id=((Number)visita.get("id")).longValue();
            if(medico==null && !visita.get("atendimento").toString().isBlank()) {
                // Como na producao coletiva atual: um unico registro hospitalar para atendimento e data identifica o medico.
                var candidatos=porChave.getOrDefault(visita.get("atendimento")+"|"+visita.get("data_visita"),List.of());
                if(candidatos.size()==1) {
                    Long identificado=medicos.identificarNome(candidatos.getFirst().get("medico_nome").toString(),cadastros);
                    if(identificado!=null) {
                        medico=identificado; inferidos++;
                        jdbc.update("update producao_visita set medico_id=?,medico_inferido=true where id=?",medico,id);
                    }
                }
            }
            if(medico==null) semMedico.add(visita); else grupos.computeIfAbsent(medico,k->new ArrayList<>()).add(visita);
        }

        int vinculadas=0,divergentes=0,pendentes=0;
        for(var visita:semMedico) {
            long id=((Number)visita.get("id")).longValue();
            boolean temCandidatos=!porChave.getOrDefault(visita.get("atendimento")+"|"+visita.get("data_visita"),List.of()).isEmpty() && !visita.get("atendimento").toString().isBlank();
            String situacao=temCandidatos?"DIVERGENTE":"PENDENTE";
            String motivo=temCandidatos?"Medico da producao nao identificado e ha registro do hospital para atendimento e data; confira o cadastro do medico"
                    :"Aguardando registro do hospital";
            if(temCandidatos) divergentes++; else pendentes++;
            jdbc.update("update producao_visita set situacao=?,motivo=?,conciliado_em=now() where id=?",situacao,motivo,id);
        }
        for(var grupo:grupos.entrySet()) {
            long medicoId=grupo.getKey();
            List<LeitorPlanilhaVisitas.Linha> producao=new ArrayList<>();
            for(var visita:grupo.getValue())
                producao.add(new LeitorPlanilhaVisitas.Linha(Math.toIntExact(((Number)visita.get("id")).longValue()),json.readValue(visita.get("campos").toString(),new TypeReference<>(){})));
            List<LeitorPlanilhaVisitas.Linha> hospitalLinhas=new ArrayList<>();
            for(var h:hospitalPorId.values()) {
                if(!Objects.equals(medicos.identificarNome(h.get("medico_nome").toString(),cadastros),medicoId)) continue;
                hospitalLinhas.add(new LeitorPlanilhaVisitas.Linha(((Number)h.get("id")).intValue(),json.readValue(h.get("campos").toString(),new TypeReference<>(){})));
            }
            var relatorio=identificacao.conciliar(nomeHospital,"Conciliacao unica","Base hospitalar",producao,repasses.complementar(hospitalId,hospitalLinhas),medicoId);
            for(var resultado:relatorio.visitas()) {
                long id=resultado.linhaMedico();
                boolean vincula=resultado.hospital()!=null && resultado.status()!=ConciliadorVisitas.Status.DIVERGENTE;
                if(vincula) {
                    vinculadas++;
                    jdbc.update("update producao_visita set situacao=?,motivo=?,hospital_visita_id=?,conciliado_em=now() where id=?",
                            resultado.status().name(),resultado.motivo(),resultado.hospital().linha(),id);
                } else {
                    if(resultado.status()==ConciliadorVisitas.Status.DIVERGENTE) divergentes++; else pendentes++;
                    jdbc.update("update producao_visita set situacao=?,motivo=?,conciliado_em=now() where id=?",resultado.status().name(),resultado.motivo(),id);
                }
            }
        }
        return new Resumo(vinculadas,divergentes,pendentes,inferidos);
    }

    /** Registros do hospital que ainda nao atendem nenhuma visita (nem da producao unica, nem do fluxo antigo). */
    public List<Map<String,Object>> hospitalSemProducao(long hospitalId,String competenciaExame) {
        return jdbc.queryForList("""
                select v.id,v.atendimento,v.data_visita,v.medico_nome,to_char(v.data_visita,'YYYY-MM') as competencia_exame,
                       v.campos->>'produto' as procedimento,v.campos->>'cod produto' as codigo_procedimento
                from visita_base_hospitalar v
                where v.hospital_id=? and v.decisao in ('ORIGINAL','VISITA_DISTINTA')
                  and not exists(select 1 from producao_visita p where p.hospital_visita_id=v.id)
                  and not exists(select 1 from reserva_base_hospitalar r where r.visita_id=v.id)
                  and (?::text is null or to_char(v.data_visita,'YYYY-MM')=?)
                order by v.data_visita,v.id
                """,hospitalId,competenciaExame,competenciaExame);
    }
}
