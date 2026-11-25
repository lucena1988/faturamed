package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
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

    @Transactional(rollbackFor=Exception.class)
    public Resultado importar(long hospitalId, Long medicoId, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
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
                "insert into producao_arquivo(hospital_id,medico_id,nome,hash,conteudo,usuario_id) values(?,?,?,?,?,?) returning id",
                Long.class,hospitalId,medicoId,nome,hash,arquivo.getBytes(),usuario.id()));

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
                    select id,campos->>'cod produto' as codigo,campos->>'produto' as produto from producao_visita
                    where hospital_id=? and arquivo_id<>? and atendimento=? and data_visita=? and medico_id is not distinct from ?
                      and decisao='ORIGINAL' order by id
                    """,hospitalId,arquivoId,atendimento,data,medico);
            Long anteriorId=anteriores.stream().filter(anterior->BaseHospitalarService.mesmoProcedimento(
                    linha.campo("Cod Produto"),linha.campo("Produto"),Objects.toString(anterior.get("codigo"),""),Objects.toString(anterior.get("produto"),"")))
                    .map(anterior->((Number)anterior.get("id")).longValue()).findFirst().orElse(null);
            if(anteriorId!=null) emAnalise++;
            String competencia=competenciaDe(data);
            competencias.merge(competencia,1,Integer::sum);
            jdbc.update("""
                    insert into producao_visita(arquivo_id,hospital_id,linha,atendimento,data_visita,medico_id,medico_nome,campos,competencia,anterior_id,decisao,situacao,motivo)
                    values(?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?)
                    """,arquivoId,hospitalId,linha.numero(),atendimento,data,medico,medicoNome,json.writeValueAsString(linha.campos()),
                    competencia,anteriorId,anteriorId==null?"ORIGINAL":"EM_ANALISE",anteriorId==null?"PENDENTE":"EM_ANALISE",
                    anteriorId==null?"Aguardando registro do hospital":"Repeticao de visita ja importada em outro arquivo; aguarda decisao");
        }
        // Se o hospital ja foi importado, a producao ja nasce conciliada; senao fica aguardando.
        return new Resultado(arquivoId,false,linhas.size(),emAnalise,semMedico,competencias,conciliacao.conciliar(hospitalId));
    }

    /** Visitas da producao do hospital, com a competencia efetiva (a manual, se houver). */
    public List<Map<String,Object>> visitas(long hospitalId,String competencia) {
        return jdbc.queryForList("""
                select v.id,v.arquivo_id,v.linha,v.atendimento,v.data_visita,v.medico_id,v.medico_nome,
                       coalesce(v.competencia_manual,v.competencia) as competencia,v.competencia as competencia_derivada,
                       v.decisao,v.anterior_id,v.situacao,v.motivo,v.hospital_visita_id,v.medico_inferido,
                       a.relatorio_id as relatorio_id,v.linha_relatorio,
                       coalesce(nullif(v.campos->>'produto',''),h.campos->>'produto') as procedimento,
                       coalesce(nullif(v.campos->>'cod produto',''),h.campos->>'cod produto') as codigo_procedimento
                from producao_visita v left join visita_base_hospitalar h on h.id=v.hospital_visita_id
                left join arquivo_base_hospitalar a on a.id=v.arquivo_relatorio_id where v.hospital_id=? and (?::text is null or coalesce(v.competencia_manual,v.competencia)=?)
                order by v.data_visita,v.id
                """,hospitalId,competencia,competencia);
    }

    /** Competencias com a quantidade de visitas, para o resumo da tela. */
    public SortedMap<String,Long> resumoPorCompetencia(long hospitalId) {
        SortedMap<String,Long> resumo=new TreeMap<>();
        jdbc.query("select coalesce(competencia_manual,competencia) as c,count(*) as total from producao_visita where hospital_id=? group by 1",
                rs->{ resumo.put(rs.getString("c"),rs.getLong("total")); },hospitalId);
        return resumo;
    }
}
