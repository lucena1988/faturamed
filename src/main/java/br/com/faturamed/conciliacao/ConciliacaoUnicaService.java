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
    private final ReconciliacaoAutomaticaService relatorios;

    public ConciliacaoUnicaService(JdbcTemplate jdbc, ObjectMapper json, HospitalCadastroService hospitais, MedicoCadastroService medicos,
            IdentificacaoProducaoService identificacao, RepasseProcedimentoService repasses, VinculoBaseHospitalar vinculos, BaseHospitalarService base,
            ReconciliacaoAutomaticaService relatorios) {
        this.jdbc=jdbc; this.json=json; this.hospitais=hospitais; this.medicos=medicos; this.identificacao=identificacao;
        this.repasses=repasses; this.vinculos=vinculos; this.base=base; this.relatorios=relatorios;
    }

    /**
     * Importa o arquivo do hospital, atualiza os relatorios ja gerados e concilia o restante. A competencia e a do
     * faturamento (NF). Cada etapa tem a propria transacao: um relatorio que nao atualiza nao desfaz a importacao
     * (o erro vem na resposta) e a conciliacao pode ser repetida em POST /conciliar.
     */
    public Map<String,Object> importarHospital(long hospitalId,String competencia,String modoHospital,String tipoProcedimento,
            MultipartFile arquivo,UsuarioAutenticado usuario) throws Exception {
        var resultado=base.importarHospital(hospitalId,competencia,modoHospital,arquivo,usuario);
        Map<String,Object> resposta=new LinkedHashMap<>();
        resposta.put("arquivoId",resultado.arquivoId()); resposta.put("repetido",resultado.repetido());
        resposta.put("visitas",resultado.visitas()); resposta.put("divergencias",resultado.divergencias());
        resposta.put("atualizadas",resultado.atualizadas()); resposta.put("foraDaCompetencia",resultado.foraDaCompetencia());
        if(!resultado.repetido()) {
            base.definirTipoArquivo(resultado.arquivoId(),tipoProcedimento);
            // Relatorios ja gerados tem prioridade sobre o hospital novo; so depois concilia o que ainda nao virou relatorio.
            resposta.put("relatoriosAtualizados",relatorios.reconciliarAfetadas(hospitalId,resultado.arquivoId()));
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
                where hospital_id=? and decisao='ORIGINAL' and hospital_visita_id is null and arquivo_relatorio_id is null order by id
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

    public record FaturamentoMes(String mes,int registros,java.math.BigDecimal repasse,
            int conciliadoRegistros,java.math.BigDecimal conciliadoRepasse,
            int semProducaoRegistros,java.math.BigDecimal semProducaoRepasse,
            int outrosRegistros,java.math.BigDecimal outrosRepasse) {}
    public record FaturamentoArquivo(long arquivoId,String nome,String competencia,String modo,java.time.LocalDateTime criadoEm,
            int registros,java.math.BigDecimal repasse,int conciliadoRegistros,java.math.BigDecimal conciliadoRepasse,
            int semProducaoRegistros,java.math.BigDecimal semProducaoRepasse,int outrosRegistros,java.math.BigDecimal outrosRepasse,
            List<FaturamentoMes> meses) {}

    /** Valor como veio na planilha: 1234.56, 1234,56 ou 1.234,56, com ou sem R$. Vazio ou ilegivel vira null. */
    static java.math.BigDecimal valorDaPlanilha(String texto) {
        if(texto==null) return null;
        String limpo=texto.replace("R$","").replace(" ","").trim().replace(" ","");
        if(limpo.isEmpty()) return null;
        if(limpo.contains(",")) limpo=limpo.replace(".","").replace(",",".");
        try { return new java.math.BigDecimal(limpo); } catch(NumberFormatException erro) { return null; }
    }

    /**
     * Total faturado por competencia de faturamento (cada planilha do hospital) aberto pelo mes do exame, separando o que ja
     * casou com uma producao do que ainda esta sem producao. O total do arquivo e a soma de Vl. A Repassar de todas as linhas,
     * como na planilha; sem essa coluna, vale Valor Tot x Regra. Repeticoes em analise e duplicadas entram no total, em "outros".
     */
    public List<FaturamentoArquivo> faturamento(long hospitalId) {
        var arquivos=jdbc.queryForList("select id,nome,competencia,modo_hospital,criado_em from arquivo_base_hospitalar where hospital_id=? and tipo='HOSPITAL' order by competencia desc,id desc",hospitalId);
        List<FaturamentoArquivo> resultado=new ArrayList<>();
        for(var arquivo:arquivos) {
            long arquivoId=((Number)arquivo.get("id")).longValue();
            var linhas=jdbc.queryForList("""
                    select to_char(v.data_visita,'YYYY-MM') as mes,
                           case when v.decisao not in ('ORIGINAL','VISITA_DISTINTA') then 'OUTROS'
                                when exists(select 1 from producao_visita p where p.hospital_visita_id=v.id)
                                  or exists(select 1 from reserva_base_hospitalar r where r.visita_id=v.id)
                                  or exists(select 1 from vinculo_base_hospitalar b where b.visita_id=v.id) then 'CONCILIADO'
                                else 'SEM_PRODUCAO' end as situacao,
                           v.campos->>'vl. a repassar' as repasse,v.campos->>'valor tot' as valor_tot,v.campos->>'regra' as regra
                    from visita_base_hospitalar v where v.arquivo_atual_id=?
                    """,arquivoId);
            // mes -> [registros e repasse de cada situacao]
            SortedMap<String,java.math.BigDecimal[]> valores=new TreeMap<>();
            SortedMap<String,int[]> contagens=new TreeMap<>();
            for(var linha:linhas) {
                var repasse=valorDaPlanilha((String)linha.get("repasse"));
                if(repasse==null) {
                    var valorTot=valorDaPlanilha((String)linha.get("valor_tot"));
                    var regra=valorDaPlanilha((String)linha.get("regra"));
                    // Regra pode vir como 0,5 ou 50 (percentual).
                    if(valorTot!=null && regra!=null) repasse=valorTot.multiply(regra.compareTo(java.math.BigDecimal.ONE)>0?regra.movePointLeft(2):regra);
                }
                int indice=switch((String)linha.get("situacao")) { case "CONCILIADO"->0; case "SEM_PRODUCAO"->1; default->2; };
                String mes=(String)linha.get("mes");
                var soma=valores.computeIfAbsent(mes,k->new java.math.BigDecimal[]{java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO});
                var conta=contagens.computeIfAbsent(mes,k->new int[3]);
                conta[indice]++;
                if(repasse!=null) soma[indice]=soma[indice].add(repasse);
            }
            List<FaturamentoMes> meses=new ArrayList<>();
            int[] totais=new int[3]; var somas=new java.math.BigDecimal[]{java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO};
            for(var mes:valores.keySet()) {
                var s=valores.get(mes); var c=contagens.get(mes);
                for(int i=0;i<3;i++) { totais[i]+=c[i]; somas[i]=somas[i].add(s[i]); }
                meses.add(new FaturamentoMes(mes,c[0]+c[1]+c[2],dinheiro(s[0].add(s[1]).add(s[2])),c[0],dinheiro(s[0]),c[1],dinheiro(s[1]),c[2],dinheiro(s[2])));
            }
            resultado.add(new FaturamentoArquivo(arquivoId,(String)arquivo.get("nome"),(String)arquivo.get("competencia"),(String)arquivo.get("modo_hospital"),
                    ((java.sql.Timestamp)arquivo.get("criado_em")).toLocalDateTime(),totais[0]+totais[1]+totais[2],dinheiro(somas[0].add(somas[1]).add(somas[2])),
                    totais[0],dinheiro(somas[0]),totais[1],dinheiro(somas[1]),totais[2],dinheiro(somas[2]),meses));
        }
        return resultado;
    }
    private static java.math.BigDecimal dinheiro(java.math.BigDecimal valor) { return valor.setScale(2,java.math.RoundingMode.HALF_UP); }

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
