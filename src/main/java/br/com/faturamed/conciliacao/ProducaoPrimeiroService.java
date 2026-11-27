package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conciliacao unica, etapa 1: importa a producao do medico como base, sem exigir o arquivo do hospital.
 * Cada linha vira uma visita com ID permanente. A competencia nao e informada: deriva da data do atendimento
 * (Dt.), entao uma planilha que cruza meses se divide sozinha. O operador pode corrigi-la depois.
 */
@Service
public class ProducaoPrimeiroService {
    public record Resultado(long arquivoId, boolean repetido, int visitas, int emAnalise, int semMedico,
            SortedMap<String,Integer> competencias, ConciliacaoUnicaService.Resumo conciliacao) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LeitorPlanilhaVisitas leitor;
    private final HospitalCadastroService hospitais;
    private final MedicoCadastroService medicos;
    private final VinculoBaseHospitalar vinculos;
    private final ConciliacaoUnicaService conciliacao;

    public ProducaoPrimeiroService(JdbcTemplate jdbc, ObjectMapper json, LeitorPlanilhaVisitas leitor,
            HospitalCadastroService hospitais, MedicoCadastroService medicos, VinculoBaseHospitalar vinculos,
            ConciliacaoUnicaService conciliacao) {
        this.jdbc=jdbc; this.json=json; this.leitor=leitor; this.hospitais=hospitais; this.medicos=medicos; this.vinculos=vinculos;
        this.conciliacao=conciliacao;
    }

    /** Competencia AAAA-MM derivada da data da visita. */
    static String competenciaDe(LocalDate data) {
        return String.format("%04d-%02d",data.getYear(),data.getMonthValue());
    }

    /** Codigo do exame como veio na producao (colunas reconhecidas pelo motor de conciliacao). */
    static String codigoDe(Map<String,String> campos) { return ConciliadorVisitas.codigoProducao(campos); }
    /** Nome do exame como veio na producao: colunas do motor ou, na falta, a coluna Produto. */
    static String nomeDe(Map<String,String> campos) {
        String nome=ConciliadorVisitas.nomeProducao(campos);
        return nome.isBlank()?Objects.toString(campos.get("produto"),"").trim():nome;
    }
    /** Informado pelo operador, ou classificado pela coluna Tipo, descricao e codigo; sem codigo nem nome nao ha o que classificar. */
    static TipoProcedimento tipoDe(TipoProcedimento informado,Map<String,String> campos) {
        if(informado!=null) return informado;
        String codigo=codigoDe(campos),nome=nomeDe(campos);
        if(codigo.isBlank() && nome.isBlank()) return null;
        return TipoProcedimento.classificar(Objects.toString(campos.get(TipoProcedimento.CAMPO),""),codigo,nome);
    }

    public Resultado importar(long hospitalId, Long medicoId, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        return importar(hospitalId,medicoId,null,arquivo,usuario);
    }

    /** tipo: o que o operador informou para a planilha toda; null classifica cada linha pelo codigo e nome do exame. */
    @Transactional(rollbackFor=Exception.class)
    public Resultado importar(long hospitalId, Long medicoId, TipoProcedimento tipo, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        if(!hospitais.buscar(hospitalId).ativo()) throw new IllegalArgumentException("Selecione um hospital ativo");
        if(medicoId!=null && !medicos.buscar(medicoId).ativo()) throw new IllegalArgumentException("Selecione um medico ativo");
        vinculos.bloquear(hospitalId);
        var linhas=leitor.ler(arquivo,false);
        for(var linha:linhas) {
            if(linha.campo("Dt.").isBlank()) throw new IllegalArgumentException("Informe Dt. na producao, linha "+linha.numero());
            if(linha.campo("Atendimento").isBlank() && medicoId==null && linha.campo("Medico").isBlank())
                throw new IllegalArgumentException("Informe Atendimento ou o Medico na producao, linha "+linha.numero());
        }
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(arquivo.getBytes()));
        var repetidos=jdbc.queryForList("select id from producao_arquivo where hospital_id=? and medico_id is not distinct from ? and hash=?",hospitalId,medicoId,hash);
        if(!repetidos.isEmpty()) return new Resultado(((Number)repetidos.getFirst().get("id")).longValue(),true,0,0,0,new TreeMap<>(),null);

        String nome=Objects.toString(arquivo.getOriginalFilename(),"planilha.xlsx").replace('\\','/');
        nome=nome.substring(nome.lastIndexOf('/')+1);
        if(nome.length()>255) throw new IllegalArgumentException("Nome de arquivo muito longo");
        long arquivoId=Objects.requireNonNull(jdbc.queryForObject(
                "insert into producao_arquivo(hospital_id,medico_id,nome,hash,conteudo,usuario_id,tipo_procedimento) values(?,?,?,?,?,?,?) returning id",
                Long.class,hospitalId,medicoId,nome,hash,arquivo.getBytes(),usuario.id(),tipo==null?null:tipo.name()));

        var cadastros=medicoId==null?medicos.listar():List.<MedicoCadastroService.Medico>of();
        SortedMap<String,Integer> competencias=new TreeMap<>();
        int emAnalise=0,semMedico=0;
        for(var linha:linhas) {
            LocalDate data=LeitorPlanilhaVisitas.data(linha.campo("Dt."),linha.numero());
            String atendimento=LeitorPlanilhaVisitas.identificador(linha.campo("Atendimento"));
            String medicoNome=linha.campo("Medico");
            Long medico=medicoId!=null?medicoId:medicos.identificarNome(medicoNome,cadastros);
            if(medico==null) semMedico++;
            // Repeticao em outro arquivo entra em analise e nao vira segunda visita disponivel. Repeticoes
            // dentro do mesmo arquivo continuam sendo visitas distintas, como no fluxo atual.
            var anteriores=jdbc.queryForList("""
                    select id,campos::text as campos from producao_visita
                    where hospital_id=? and arquivo_id<>? and atendimento=? and data_visita=? and medico_id is not distinct from ?
                      and decisao='ORIGINAL' order by id
                    """,hospitalId,arquivoId,atendimento,data,medico);
            Long anteriorId=null;
            for(var anterior:anteriores) {
                Map<String,String> camposAnterior=json.readValue(anterior.get("campos").toString(),new TypeReference<LinkedHashMap<String,String>>(){});
                if(BaseHospitalarService.mesmoProcedimento(codigoDe(linha.campos()),nomeDe(linha.campos()),codigoDe(camposAnterior),nomeDe(camposAnterior))) {
                    anteriorId=((Number)anterior.get("id")).longValue(); break;
                }
            }
            var tipoDaLinha=tipoDe(tipo,linha.campos());
            if(anteriorId!=null) emAnalise++;
            String competencia=competenciaDe(data);
            competencias.merge(competencia,1,Integer::sum);
            jdbc.update("""
                    insert into producao_visita(arquivo_id,hospital_id,linha,atendimento,data_visita,medico_id,medico_nome,campos,competencia,anterior_id,decisao,situacao,motivo,tipo_procedimento)
                    values(?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)
                    """,arquivoId,hospitalId,linha.numero(),atendimento,data,medico,medicoNome,json.writeValueAsString(linha.campos()),
                    competencia,anteriorId,anteriorId==null?"ORIGINAL":"EM_ANALISE",anteriorId==null?"PENDENTE":"EM_ANALISE",
                    anteriorId==null?"Aguardando registro do hospital":"Repeticao de visita ja importada em outro arquivo; aguarda decisao",
                    tipoDaLinha==null?null:tipoDaLinha.name());
        }
        // Se o hospital ja foi importado, a producao ja nasce conciliada; senao fica aguardando.
        return new Resultado(arquivoId,false,linhas.size(),emAnalise,semMedico,competencias,conciliacao.conciliar(hospitalId));
    }

    /** Visitas da producao do hospital, com a competencia efetiva (a manual, se houver). */
    public List<Map<String,Object>> visitas(long hospitalId,String competencia) {
        var linhas=jdbc.queryForList("""
                select v.id,v.arquivo_id,v.linha,v.atendimento,v.data_visita,v.medico_id,v.medico_nome,
                       coalesce(v.competencia_manual,v.competencia) as competencia,v.competencia as competencia_derivada,
                       v.decisao,v.anterior_id,v.situacao,v.motivo,v.hospital_visita_id,v.medico_inferido,
                       a.relatorio_id as relatorio_id,v.linha_relatorio,
                       (select count(*) from producao_visita x where x.arquivo_relatorio_id=v.arquivo_relatorio_id) as visitas_no_relatorio,
                       v.campos::text as campos,v.tipo_procedimento as tipo_gravado,
                       h.campos->>'produto' as procedimento_hospital,h.campos->>'cod produto' as codigo_hospital
                from producao_visita v
                left join arquivo_base_hospitalar a on a.id=v.arquivo_relatorio_id
                left join vinculo_base_hospitalar b on b.relatorio_id=a.relatorio_id and b.linha_producao=v.linha_relatorio
                left join visita_base_hospitalar h on h.id=coalesce(v.hospital_visita_id,b.visita_id)
                where v.hospital_id=? and (?::text is null or coalesce(v.competencia_manual,v.competencia)=?)
                order by v.data_visita,v.id
                """,hospitalId,competencia,competencia);
        // Codigo e nome do exame vem da producao; sem eles, do registro do hospital ja vinculado.
        List<Map<String,Object>> resultado=new ArrayList<>();
        for(var linha:linhas) {
            Map<String,Object> item=new LinkedHashMap<>(linha);
            Map<String,String> campos;
            try { campos=json.readValue(item.remove("campos").toString(),new TypeReference<LinkedHashMap<String,String>>(){}); }
            catch(Exception erro) { throw new IllegalStateException("Dados da visita ilegiveis",erro); }
            String codigo=codigoDe(campos),nome=nomeDe(campos);
            if(codigo.isBlank()) codigo=Objects.toString(item.get("codigo_hospital"),"");
            if(nome.isBlank()) nome=Objects.toString(item.get("procedimento_hospital"),"");
            Object gravado=item.remove("tipo_gravado");
            item.remove("codigo_hospital"); item.remove("procedimento_hospital");
            item.put("codigo_procedimento",codigo.isBlank()?null:codigo);
            item.put("procedimento",nome.isBlank()?null:nome);
            // Visitas importadas antes do tipo existir sao classificadas na hora, como a importacao automatica faria.
            item.put("tipo_procedimento",gravado!=null?gravado:codigo.isBlank() && nome.isBlank()?null:TipoProcedimento.classificar(Objects.toString(campos.get(TipoProcedimento.CAMPO),""),codigo,nome).name());
            resultado.add(item);
        }
        return resultado;
    }

    /** Competencias com a quantidade de visitas, para o resumo da tela. */
    public SortedMap<String,Long> resumoPorCompetencia(long hospitalId) {
        SortedMap<String,Long> resumo=new TreeMap<>();
        jdbc.query("select coalesce(competencia_manual,competencia) as c,count(*) as total from producao_visita where hospital_id=? group by 1",
                rs->{ resumo.put(rs.getString("c"),rs.getLong("total")); },hospitalId);
        return resumo;
    }
}
