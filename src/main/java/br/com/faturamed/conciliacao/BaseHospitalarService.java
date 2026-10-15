package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BaseHospitalarService {
    public record Resultado(long arquivoId, Long relatorioId, int visitas, int divergencias, boolean repetido) {}
    public record Decisao(String acao, Integer anteriorId, String justificativa, String evidencia) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LeitorPlanilhaVisitas leitor;
    private final ConciliadorVisitas motor;
    private final HospitalCadastroService hospitais;
    private final MedicoCadastroService medicos;
    private final IdentificacaoProducaoService identificacao;
    private final VinculoBaseHospitalar vinculos;
    private final RevisaoVisitasService revisoes;

    public BaseHospitalarService(JdbcTemplate jdbc, ObjectMapper json, LeitorPlanilhaVisitas leitor,
            ConciliadorVisitas motor, HospitalCadastroService hospitais, MedicoCadastroService medicos,
            IdentificacaoProducaoService identificacao, VinculoBaseHospitalar vinculos, RevisaoVisitasService revisoes) {
        this.jdbc=jdbc; this.json=json; this.leitor=leitor; this.motor=motor; this.hospitais=hospitais;
        this.medicos=medicos; this.identificacao=identificacao; this.vinculos=vinculos;
        this.revisoes=revisoes;
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarHospital(long hospitalId, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        validarHospital(hospitalId); validarCompetencia(competencia); vinculos.bloquear(hospitalId);
        var linhas=leitor.ler(arquivo,true);
        var registros=motor.conciliar("Base", "", arquivo.getOriginalFilename(), List.of(), linhas).hospitalSemProducao();
        String hash=hash(arquivo);
        var repetidos=jdbc.queryForList("select id from arquivo_base_hospitalar where hospital_id=? and tipo='HOSPITAL' and competencia=? and hash=?",hospitalId,competencia,hash);
        if(!repetidos.isEmpty()) return new Resultado(((Number)repetidos.getFirst().get("id")).longValue(),null,0,0,true);
        long arquivoId=salvarArquivo(hospitalId,"HOSPITAL",null,competencia,arquivo,hash,usuario.id());
        int divergencias=0;
        var cadastros=medicos.listar();
        for(var registro:registros) {
            if(registro.data()==null) throw new IllegalArgumentException("Data da visita ausente na linha "+registro.linha());
            var anteriores=jdbc.queryForList("select id,medico_nome from visita_base_hospitalar where hospital_id=? and atendimento=? and data_visita=? and decisao in ('ORIGINAL','VISITA_DISTINTA') order by id",hospitalId,registro.atendimento(),registro.data());
            var semelhantes=anteriores.stream().filter(anterior->mesmoMedico(registro.medico(),anterior.get("medico_nome").toString(),cadastros)).toList();
            Integer anteriorId=semelhantes.isEmpty()?null:((Number)semelhantes.getFirst().get("id")).intValue();
            if(anteriorId!=null) divergencias++;
            var linha=linhas.stream().filter(origem->origem.numero()==registro.linha()).findFirst().orElseThrow();
            jdbc.update("""
                    insert into visita_base_hospitalar(arquivo_id,hospital_id,linha,atendimento,data_visita,medico_nome,campos,anterior_id,decisao)
                    values(?,?,?,?,?,?,?::jsonb,?,?)
                    """,arquivoId,hospitalId,registro.linha(),registro.atendimento(),registro.data(),registro.medico(),json.writeValueAsString(linha.campos()),anteriorId,anteriorId==null?"ORIGINAL":"EM_ANALISE");
        }
        return new Resultado(arquivoId,null,registros.size(),divergencias,false);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarProducao(long hospitalId, long medicoId, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        return importarProducao(hospitalId,medicoId,"UNICO",competencia,arquivo,usuario);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarProducao(long hospitalId, Long medicoId, String modoProducao, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        validarHospital(hospitalId); validarCompetencia(competencia);
        if(!List.of("UNICO","VARIOS").contains(Objects.toString(modoProducao,""))) throw new IllegalArgumentException("Modo de producao invalido");
        if(modoProducao.equals("UNICO") && medicoId==null) throw new IllegalArgumentException("Selecione o medico da producao individual");
        if(modoProducao.equals("VARIOS") && medicoId!=null) throw new IllegalArgumentException("Producao coletiva identifica o medico por linha; nao selecione um medico unico");
        if(medicoId!=null && !medicos.buscar(medicoId).ativo()) throw new IllegalArgumentException("Selecione um medico ativo");
        vinculos.bloquear(hospitalId);
        var producao=leitor.ler(arquivo,false);
        for(var linha:producao) if(linha.campo("Dt.").isBlank()) throw new IllegalArgumentException("Informe Dt. na producao, linha "+linha.numero());
        String hash=hash(arquivo);
        var repetidos=jdbc.queryForList("select id,relatorio_id,medico_id from arquivo_base_hospitalar where hospital_id=? and tipo='PRODUCAO' and competencia=? and hash=? and medico_id is not distinct from ?",hospitalId,competencia,hash,medicoId);
        if(!repetidos.isEmpty()) {
            var anterior=repetidos.getFirst();
            return new Resultado(((Number)anterior.get("id")).longValue(),((Number)anterior.get("relatorio_id")).longValue(),0,0,true);
        }
        var relatorio=calcular(hospitalId,medicoId,arquivo.getOriginalFilename(),producao,null);
        long relatorioId=jdbc.queryForObject("insert into conciliacao_visitas(relatorio,hash_producao,medico_producao_id,base_hospitalar) values(?::jsonb,?,?,true) returning id",Long.class,json.writeValueAsString(relatorio),hash,medicoId);
        long arquivoId=salvarArquivo(hospitalId,"PRODUCAO",medicoId,competencia,arquivo,hash,usuario.id());
        jdbc.update("update arquivo_base_hospitalar set relatorio_id=? where id=?",relatorioId,arquivoId);
        vinculos.sincronizar(relatorioId,relatorio);
        return new Resultado(arquivoId,relatorioId,relatorio.visitas().size(),(int)relatorio.visitas().stream().filter(visita->visita.status()==ConciliadorVisitas.Status.DIVERGENTE).count(),false);
    }

    private ConciliadorVisitas.Relatorio calcular(long hospitalId,Long medicoId,String nome,List<LeitorPlanilhaVisitas.Linha> producao,Long proprioRelatorio) throws Exception {
        var hospital=hospitais.buscar(hospitalId);
        var cadastros=medicos.listar();
        var registros=jdbc.queryForList("select id,campos::text,medico_nome from visita_base_hospitalar where hospital_id=? and decisao in ('ORIGINAL','VISITA_DISTINTA') order by id",hospitalId);
        List<LeitorPlanilhaVisitas.Linha> hospitalLinhas=new ArrayList<>();
        for(var registro:registros) {
            if(medicoId!=null && !Objects.equals(identificar(registro.get("medico_nome").toString(),cadastros),medicoId)) continue;
            hospitalLinhas.add(new LeitorPlanilhaVisitas.Linha(((Number)registro.get("id")).intValue(),json.readValue(registro.get("campos").toString(),new TypeReference<Map<String,String>>(){})));
        }
        if(jdbc.queryForObject("select count(*) from visita_base_hospitalar where hospital_id=?",Long.class,hospitalId)==0)
            throw new IllegalArgumentException("Importe primeiro a planilha do hospital");
        var base=medicoId==null?conciliarColetiva(hospital.nome(),nome,producao,hospitalLinhas,cadastros)
                :identificacao.conciliar(hospital.nome(),nome,"Base hospitalar",producao,hospitalLinhas,medicoId);
        Set<Integer> indisponiveis=vinculos.indisponiveis(hospitalId);
        if(proprioRelatorio!=null) indisponiveis.removeAll(vinculos.reservadas(proprioRelatorio));
        var visitas=base.visitas().stream().map(visita->{
            if(visita.candidatos().stream().noneMatch(candidato->indisponiveis.contains(candidato.linha()))) return visita;
            return new ConciliadorVisitas.Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),ConciliadorVisitas.Status.DIVERGENTE,
                    "Possivel duplicidade: visita ja vinculada a outra producao ou com repeticao hospitalar em analise. Consulte a base hospitalar.",
                    visita.original(),null,visita.candidatos(),null,visita.medicoProducao());
        }).toList();
        if(medicoId==null) {
            var registrosCompletos=motor.conciliar(hospital.nome(),nome,"Base hospitalar",List.of(),hospitalLinhas).hospitalSemProducao();
            visitas=visitas.stream().map(visita->new ConciliadorVisitas.Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),visita.status(),visita.motivo(),
                    visita.original(),visita.hospital(),registrosCompletos.stream().filter(registro->registro.atendimento().equals(visita.atendimento()) && registro.data().equals(visita.data())).toList(),
                    visita.camposRevisados(),visita.medicoProducao())).toList();
        }
        return new AplicadorRevisaoVisita().recalcular(new ConciliadorVisitas.Relatorio(base.hospital(),base.arquivoMedico(),base.arquivoHospital(),base.regraStatus(),visitas,base.hospitalSemProducao(),base.resumo(),base.repasseCorrespondente(),hospitalId),visitas);
    }

    private ConciliadorVisitas.Relatorio conciliarColetiva(String hospital,String nome,List<LeitorPlanilhaVisitas.Linha> producao,
            List<LeitorPlanilhaVisitas.Linha> hospitalLinhas,List<MedicoCadastroService.Medico> cadastros) {
        Map<Long,List<LeitorPlanilhaVisitas.Linha>> grupos=new LinkedHashMap<>();
        List<ConciliadorVisitas.Visita> visitas=new ArrayList<>();
        Map<ConciliadorVisitas.Chave,List<LeitorPlanilhaVisitas.Linha>> porChave=new HashMap<>();
        for(var registro:hospitalLinhas) {
            var chave=new ConciliadorVisitas.Chave(LeitorPlanilhaVisitas.identificador(registro.campo("Atendimento")),LeitorPlanilhaVisitas.data(registro.campo("Data Consumo"),registro.numero()));
            porChave.computeIfAbsent(chave,ignorada->new ArrayList<>()).add(registro);
        }
        Set<Integer> inferidas=new HashSet<>();
        for(var linha:producao) {
            var chave=new ConciliadorVisitas.Chave(LeitorPlanilhaVisitas.identificador(linha.campo("Atendimento")),LeitorPlanilhaVisitas.data(linha.campo("Dt."),linha.numero()));
            Long medicoId=identificar(linha.campo("Medico"),cadastros);
            var candidatos=porChave.getOrDefault(chave,List.of());
            if(linha.campo("Medico").isBlank() && candidatos.size()==1) {
                medicoId=identificar(candidatos.getFirst().campo("Medico"),cadastros);
                if(medicoId!=null) inferidas.add(linha.numero());
            }
            if(medicoId==null) {
                boolean semBase=linha.campo("Medico").isBlank() && candidatos.isEmpty();
                String motivo=semBase?"Sem correspondente na base hospitalar para atendimento e data; aguardando registro do hospital":
                        linha.campo("Medico").isBlank() && candidatos.size()>1?"Mais de um registro hospitalar para atendimento e data; nao e seguro inferir o medico":
                        linha.campo("Medico").isBlank()?"Medico do registro hospitalar unico nao cadastrado ou ambiguo; confira o cadastro antes de vincular":
                        "Medico da producao coletiva nao cadastrado ou ambiguo. Confira a identificacao antes de vincular";
                visitas.add(new ConciliadorVisitas.Visita(linha.numero(),chave.atendimento(),chave.data(),
                        semBase?ConciliadorVisitas.Status.PENDENTE:ConciliadorVisitas.Status.DIVERGENTE,motivo,linha.campos(),null,List.of()));
            } else grupos.computeIfAbsent(medicoId,identidade->new ArrayList<>()).add(linha);
        }
        for(var grupo:grupos.entrySet()) {
            var hospitalDoMedico=hospitalLinhas.stream().filter(linha->Objects.equals(identificar(linha.campo("Medico"),cadastros),grupo.getKey())).toList();
            visitas.addAll(identificacao.conciliar(hospital,nome,"Base hospitalar",grupo.getValue(),hospitalDoMedico,grupo.getKey()).visitas().stream().map(visita->{
                if(!inferidas.contains(visita.linhaMedico())) return visita;
                var registro=visita.status()==ConciliadorVisitas.Status.DIVERGENTE?null:visita.hospital();
                return new ConciliadorVisitas.Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),visita.status(),
                        visita.motivo()+". Medico identificado pela correspondencia hospitalar unica de atendimento e data",
                        visita.original(),registro,visita.candidatos(),visita.camposRevisados(),visita.medicoProducao());
            }).toList());
        }
        visitas.sort(Comparator.comparingInt(ConciliadorVisitas.Visita::linhaMedico));
        var base=motor.conciliar(hospital,nome,"Base hospitalar",List.of(),hospitalLinhas);
        Set<Integer> usadas=new HashSet<>();
        visitas.forEach(visita->{ if(visita.hospital()!=null) usadas.add(visita.hospital().linha()); });
        var semProducao=base.hospitalSemProducao().stream().filter(registro->!usadas.contains(registro.linha())).toList();
        return new AplicadorRevisaoVisita().recalcular(new ConciliadorVisitas.Relatorio(hospital,nome,"Base hospitalar",base.regraStatus(),visitas,semProducao,Map.of(),java.math.BigDecimal.ZERO),visitas);
    }

    @Transactional(rollbackFor=Exception.class)
    public long atualizar(long relatorioId,long versao) throws Exception {
        var linhas=jdbc.queryForList("select numero_versao from conciliacao_visitas where id=? for update",relatorioId);
        if(linhas.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",relatorioId).isEmpty()) throw new ConflitoRevisaoException("Abra a versao mais recente");
        var arquivos=jdbc.queryForList("select * from arquivo_base_hospitalar where relatorio_id=?",relatorioId);
        if(arquivos.isEmpty()) throw new IllegalArgumentException("Relatorio anterior ao novo fluxo. Consulte seu historico pela conciliacao");
        var arquivo=arquivos.getFirst();
        long hospitalId=((Number)arquivo.get("hospital_id")).longValue();
        Long medicoId=arquivo.get("medico_id")==null?null:((Number)arquivo.get("medico_id")).longValue();
        vinculos.bloquear(hospitalId);
        var estado=revisoes.carregar(relatorioId);
        if(estado.versao()!=versao) throw new ConflitoRevisaoException("Relatorio alterado. Atualize antes de tentar novamente");
        var producao=estado.original().visitas().stream().map(visita->new LeitorPlanilhaVisitas.Linha(visita.linhaMedico(),visita.original())).toList();
        var calculado=calcular(hospitalId,medicoId,arquivo.get("nome").toString(),producao,relatorioId);
        Set<Integer> revisadas=new HashSet<>();
        estado.historico().forEach(revisao->revisadas.add(revisao.linhaMedico()));
        Map<Integer,ConciliadorVisitas.Visita> anteriores=new HashMap<>();
        estado.atual().visitas().forEach(visita->anteriores.put(visita.linhaMedico(),visita));
        var visitas=calculado.visitas().stream().map(visita->{
            var anterior=anteriores.get(visita.linhaMedico());
            return anterior.hospital()!=null || revisadas.contains(visita.linhaMedico()) ? anterior : visita;
        }).toList();
        var relatorio=new AplicadorRevisaoVisita().recalcular(calculado,visitas);
        long novoId=jdbc.queryForObject("insert into conciliacao_visitas(relatorio,anterior_id,numero_versao,hash_producao,medico_producao_id,base_hospitalar) values(?::jsonb,?,?,?,?,true) returning id",Long.class,json.writeValueAsString(relatorio),relatorioId,((Number)linhas.getFirst().get("numero_versao")).intValue()+1,arquivo.get("hash"),medicoId);
        jdbc.update("insert into revisao_visita(conciliacao_id,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em) select ?,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em from revisao_visita where conciliacao_id=? order by id",novoId,relatorioId);
        jdbc.update("delete from vinculo_base_hospitalar where relatorio_id=?",relatorioId);
        jdbc.update("update arquivo_base_hospitalar set relatorio_id=? where id=?",novoId,arquivo.get("id"));
        vinculos.sincronizar(novoId,relatorio);
        return novoId;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> arquivos(long hospitalId) {
        return jdbc.queryForList("select a.id,a.tipo,a.nome,a.competencia,a.criado_em,a.relatorio_id,m.nome as medico,case when a.tipo='PRODUCAO' and a.medico_id is null then 'VARIOS' else 'UNICO' end as modo_producao from arquivo_base_hospitalar a left join medico_cadastro m on m.id=a.medico_id where a.hospital_id=? order by a.id desc",hospitalId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> visitas(long hospitalId) {
        return jdbc.queryForList("""
                select v.id,v.linha,v.atendimento,v.data_visita,v.medico_nome,v.decisao,v.anterior_id,a.competencia,a.id as arquivo_id,
                b.relatorio_id,b.linha_producao,v.campos->>'produto' as procedimento,
                case when v.decisao='EM_ANALISE' then 'DIVERGENTE'
                     when v.decisao='DUPLICADO_PAGO_OUTRO_MES' then 'DUPLICADO_PAGO_OUTRO_MES'
                     when b.visita_id is not null and coalesce(
                         (select depois->>'status' from revisao_visita where conciliacao_id=b.relatorio_id and linha_medico=b.linha_producao order by id desc limit 1),
                         (select item->>'status' from jsonb_array_elements(r.relatorio->'visitas') item where (item->>'linhaMedico')::integer=b.linha_producao))='DIVERGENTE' then 'DIVERGENTE'
                     when b.visita_id is not null then 'CONCILIADA'
                     when exists(select 1 from visita_base_hospitalar repetida where repetida.anterior_id=v.id and repetida.decisao='EM_ANALISE') then 'DIVERGENTE'
                     else 'AGUARDANDO_PRODUCAO' end as situacao
                from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id=v.arquivo_id
                left join vinculo_base_hospitalar b on b.visita_id=v.id
                left join conciliacao_visitas r on r.id=b.relatorio_id where v.hospital_id=? order by v.id desc
                """,hospitalId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> historico(int visitaId) {
        return jdbc.queryForList("select r.id,r.decisao,r.anterior_id,r.justificativa,r.evidencia,r.criado_em,u.nome as responsavel from revisao_base_hospitalar r join usuario_acesso u on u.id=r.usuario_id where visita_id=? order by r.id",visitaId);
    }

    @Transactional(rollbackFor=Exception.class)
    public void decidir(int visitaId, Decisao dados, UsuarioAutenticado usuario) {
        if(dados==null || !List.of("VISITA_DISTINTA","DUPLICADO_PAGO_OUTRO_MES").contains(Objects.toString(dados.acao(),""))) throw new IllegalArgumentException("Decisao invalida");
        validarTexto(dados.justificativa(),"Justificativa");
        var registros=jdbc.queryForList("select * from visita_base_hospitalar where id=?",visitaId);
        if(registros.isEmpty()) throw new RecursoNaoEncontradoException("Visita nao encontrada");
        long hospitalId=((Number)registros.getFirst().get("hospital_id")).longValue();
        vinculos.bloquear(hospitalId);
        var visita=jdbc.queryForMap("select * from visita_base_hospitalar where id=? for update",visitaId);
        if(!visita.get("decisao").equals("EM_ANALISE")) throw new ConflitoRevisaoException("Esta ocorrencia ja foi analisada. Atualize a base");
        String evidencia=Objects.toString(dados.evidencia(),"").trim();
        Integer anteriorId=dados.anteriorId();
        if(dados.acao().equals("DUPLICADO_PAGO_OUTRO_MES")) {
            validarTexto(evidencia,"Evidencia do pagamento anterior");
            if(anteriorId==null || anteriorId==visitaId) throw new IllegalArgumentException("Informe o ID original da visita");
            var anteriores=jdbc.queryForList("select v.*,a.competencia from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id=v.arquivo_id where v.id=? and v.hospital_id=? and v.decisao in ('ORIGINAL','VISITA_DISTINTA')",anteriorId,hospitalId);
            if(anteriores.isEmpty()) throw new IllegalArgumentException("Visita original invalida ou de outro hospital");
            var anterior=anteriores.getFirst();
            if(!anterior.get("atendimento").equals(visita.get("atendimento")) || !anterior.get("data_visita").equals(visita.get("data_visita"))
                    || !mesmoMedico(anterior.get("medico_nome").toString(),visita.get("medico_nome").toString(),medicos.listar()))
                throw new IllegalArgumentException("Confira atendimento, data e medico da visita original");
            String competencia=jdbc.queryForObject("select competencia from arquivo_base_hospitalar where id=?",String.class,visita.get("arquivo_id"));
            if(competencia.equals(anterior.get("competencia"))) throw new IllegalArgumentException("Pago em outro mes exige competencias diferentes");
        } else anteriorId=null;
        jdbc.update("insert into revisao_base_hospitalar(visita_id,anterior_id,decisao,justificativa,evidencia,usuario_id) values(?,?,?,?,?,?)",visitaId,anteriorId,dados.acao(),dados.justificativa().trim(),evidencia,usuario.id());
        jdbc.update("update visita_base_hospitalar set decisao=?,anterior_id=? where id=?",dados.acao(),anteriorId,visitaId);
    }

    public Map<String,Object> arquivo(long id) {
        var registros=jdbc.queryForList("select nome,conteudo,tipo from arquivo_base_hospitalar where id=?",id);
        if(registros.isEmpty()) throw new RecursoNaoEncontradoException("Arquivo nao encontrado");
        return registros.getFirst();
    }

    private HospitalCadastroService.Hospital validarHospital(long id) {
        var hospital=hospitais.buscar(id);
        if(!hospital.ativo()) throw new IllegalArgumentException("Selecione um hospital ativo");
        return hospital;
    }
    private long salvarArquivo(long hospitalId,String tipo,Long medicoId,String competencia,MultipartFile arquivo,String hash,long usuarioId) throws Exception {
        String nome=Objects.toString(arquivo.getOriginalFilename(),"planilha.xlsx").replace('\\','/');
        nome=nome.substring(nome.lastIndexOf('/')+1);
        if(nome.length()>255) throw new IllegalArgumentException("Nome de arquivo muito longo");
        return jdbc.queryForObject("insert into arquivo_base_hospitalar(hospital_id,tipo,medico_id,competencia,nome,hash,conteudo,usuario_id) values(?,?,?,?,?,?,?,?) returning id",Long.class,hospitalId,tipo,medicoId,competencia,nome,hash,arquivo.getBytes(),usuarioId);
    }
    static void validarCompetencia(String competencia) {
        try { if(competencia==null || !competencia.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException(); YearMonth.parse(competencia); }
        catch(Exception erro) { throw new IllegalArgumentException("Informe a competencia no formato ano-mes"); }
    }
    static void validarTexto(String texto,String campo) {
        if(texto==null || texto.trim().length()<5 || texto.length()>2000) throw new IllegalArgumentException(campo+" deve ter entre 5 e 2000 caracteres");
    }
    private String hash(MultipartFile arquivo) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(arquivo.getBytes()));
    }
    private Long identificar(String nome,List<MedicoCadastroService.Medico> cadastros) {
        return medicos.identificarNome(nome,cadastros);
    }
    private boolean mesmoMedico(String primeiro,String segundo,List<MedicoCadastroService.Medico> cadastros) {
        if(primeiro.isBlank() || segundo.isBlank()) return true;
        Long primeiroId=identificar(primeiro,cadastros),segundoId=identificar(segundo,cadastros);
        return primeiroId!=null && segundoId!=null ? primeiroId.equals(segundoId)
                : LeitorPlanilhaVisitas.normalizar(primeiro).equals(LeitorPlanilhaVisitas.normalizar(segundo));
    }
}
