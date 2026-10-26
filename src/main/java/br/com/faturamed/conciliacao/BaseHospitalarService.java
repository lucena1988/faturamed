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
    public record Resultado(long arquivoId, Long relatorioId, int visitas, int divergencias, boolean repetido, int atualizadas) {
        public Resultado(long arquivoId,Long relatorioId,int visitas,int divergencias,boolean repetido) {
            this(arquivoId,relatorioId,visitas,divergencias,repetido,0);
        }
    }
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
    private final AtualizacaoHospitalarService atualizacoes;

    public BaseHospitalarService(JdbcTemplate jdbc, ObjectMapper json, LeitorPlanilhaVisitas leitor,
            ConciliadorVisitas motor, HospitalCadastroService hospitais, MedicoCadastroService medicos,
            IdentificacaoProducaoService identificacao, VinculoBaseHospitalar vinculos, RevisaoVisitasService revisoes, AtualizacaoHospitalarService atualizacoes) {
        this.jdbc=jdbc; this.json=json; this.leitor=leitor; this.motor=motor; this.hospitais=hospitais;
        this.medicos=medicos; this.identificacao=identificacao; this.vinculos=vinculos;
        this.revisoes=revisoes;
        this.atualizacoes=atualizacoes;
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarHospital(long hospitalId, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        return importarHospital(hospitalId,competencia,"NOVO",arquivo,usuario);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarHospital(long hospitalId,String competencia,String modoHospital,MultipartFile arquivo,UsuarioAutenticado usuario) throws Exception {
        if(!List.of("NOVO","ATUALIZAR").contains(Objects.toString(modoHospital,""))) throw new IllegalArgumentException("Modo hospitalar invalido");
        validarHospital(hospitalId); competencia = normalizarCompetencia(competencia); vinculos.bloquear(hospitalId);
        var linhas=leitor.ler(arquivo,true);
        var registros=motor.conciliar("Base", "", arquivo.getOriginalFilename(), List.of(), linhas).hospitalSemProducao();
        String hash=hash(arquivo);
        var repetidos=jdbc.queryForList("select id from arquivo_base_hospitalar where hospital_id=? and tipo='HOSPITAL' and competencia=? and hash=?",hospitalId,competencia,hash);
        if(!repetidos.isEmpty()) return new Resultado(((Number)repetidos.getFirst().get("id")).longValue(),null,0,0,true);
        long arquivoId=salvarArquivo(hospitalId,"HOSPITAL",null,competencia,arquivo,hash,usuario.id());
        jdbc.update("update arquivo_base_hospitalar set modo_hospital=? where id=?",modoHospital,arquivoId);
        int divergencias=0,atualizadas=0;
        Map<Integer,LeitorPlanilhaVisitas.Linha> origens=new HashMap<>();
        linhas.forEach(linha->origens.put(linha.numero(),linha));
        var cadastros=medicos.listar();
        for(var registro:registros) {
            if(registro.data()==null) throw new IllegalArgumentException("Data da visita ausente na linha "+registro.linha());
            var linha=origens.get(registro.linha());
            if(modoHospital.equals("ATUALIZAR") && atualizacoes.aplicar(hospitalId,arquivoId,linha,registro,registros,cadastros,usuario.id())) {
                atualizadas++;
                continue;
            }
            var anteriores=jdbc.queryForList("select id,medico_nome from visita_base_hospitalar where hospital_id=? and atendimento=? and data_visita=? and decisao in ('ORIGINAL','VISITA_DISTINTA') order by id",hospitalId,registro.atendimento(),registro.data());
            var semelhantes=anteriores.stream().filter(anterior->mesmoMedico(registro.medico(),anterior.get("medico_nome").toString(),cadastros)).toList();
            Integer anteriorId=semelhantes.isEmpty()?null:((Number)semelhantes.getFirst().get("id")).intValue();
            if(anteriorId!=null) divergencias++;
            jdbc.update("""
                    insert into visita_base_hospitalar(arquivo_id,hospital_id,linha,atendimento,data_visita,medico_nome,campos,anterior_id,decisao,arquivo_atual_id,linha_atual)
                    values(?,?,?,?,?,?,?::jsonb,?,?,?,?)
                    """,arquivoId,hospitalId,registro.linha(),registro.atendimento(),registro.data(),registro.medico(),json.writeValueAsString(linha.campos()),anteriorId,anteriorId==null?"ORIGINAL":"EM_ANALISE",arquivoId,registro.linha());
        }
        return new Resultado(arquivoId,null,registros.size(),divergencias,false,atualizadas);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarProducao(long hospitalId, long medicoId, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        return importarProducao(hospitalId,medicoId,"UNICO",competencia,arquivo,usuario);
    }

    @Transactional(rollbackFor=Exception.class)
    public Resultado importarProducao(long hospitalId, Long medicoId, String modoProducao, String competencia, MultipartFile arquivo, UsuarioAutenticado usuario) throws Exception {
        validarHospital(hospitalId); competencia = normalizarCompetencia(competencia);
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
        Set<Integer> repeticoesEmAnalise=new HashSet<>(jdbc.queryForList("select anterior_id from visita_base_hospitalar where hospital_id=? and anterior_id is not null and decisao='EM_ANALISE'",Integer.class,hospitalId));
        Set<Integer> duplicadasPagas=new HashSet<>(jdbc.queryForList("select anterior_id from visita_base_hospitalar where hospital_id=? and anterior_id is not null and decisao='DUPLICADO_PAGO_OUTRO_MES'",Integer.class,hospitalId));
        var visitas=base.visitas().stream().map(visita->{
            if(visita.candidatos().stream().noneMatch(candidato->indisponiveis.contains(candidato.linha()))) return visita;
            String motivo=visita.candidatos().stream().anyMatch(candidato->repeticoesEmAnalise.contains(candidato.linha()))
                    ?"Possivel duplicidade: repeticao hospitalar ainda em analise. Resolva a ocorrencia na base hospitalar antes de vincular esta producao."
                    :visita.candidatos().stream().anyMatch(candidato->duplicadasPagas.contains(candidato.linha()))
                    ?"Possivel duplicidade: reapresentacao classificada como duplicado pago em outro mes. A visita original ja esta vinculada a outra producao; nao gera novo valor a receber."
                    :"Possivel duplicidade: visita hospitalar ja vinculada ao historico de outra producao. Confira o vinculo original na base hospitalar antes de revisar.";
            return new ConciliadorVisitas.Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),ConciliadorVisitas.Status.DIVERGENTE,
                    motivo,
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
        Map<Integer,RevisaoVisitasService.Revisao> ultimasRevisoes=new HashMap<>();
        estado.historico().forEach(revisao->ultimasRevisoes.put(revisao.linhaMedico(),revisao));
        Map<Integer,ConciliadorVisitas.Visita> anteriores=new HashMap<>();
        estado.atual().visitas().forEach(visita->anteriores.put(visita.linhaMedico(),visita));
        List<ConciliadorVisitas.Visita> visitas=new ArrayList<>();
        Map<Integer,ConciliadorVisitas.Visita> porLinha=new HashMap<>();
        for(var visita:calculado.visitas()) {
            var anterior=anteriores.get(visita.linhaMedico());
            var ultima=ultimasRevisoes.get(visita.linhaMedico());
            ConciliadorVisitas.Visita atualizada;
            if(ultima!=null && ultima.acao()!=RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO) {
                atualizada=atualizarRevisada(anterior,ultima,visita.candidatos());
            } else if(anterior.hospital()!=null) {
                atualizada=atualizarVinculada(estado.original(),anterior);
            } else {
                atualizada=visita;
            }
            visitas.add(atualizada);
            porLinha.put(atualizada.linhaMedico(),atualizada);
        }
        var relatorio=new AplicadorRevisaoVisita().recalcular(calculado,visitas);
        long novoId=jdbc.queryForObject("insert into conciliacao_visitas(relatorio,anterior_id,numero_versao,hash_producao,medico_producao_id,base_hospitalar) values(?::jsonb,?,?,?,?,true) returning id",Long.class,json.writeValueAsString(relatorio),relatorioId,((Number)linhas.getFirst().get("numero_versao")).intValue()+1,arquivo.get("hash"),medicoId);
        var revisoesAnteriores=jdbc.queryForList("select id,linha_medico,acao,responsavel,justificativa,antes::text as antes,depois::text as depois,criado_em from revisao_visita where conciliacao_id=? order by id",relatorioId);
        for(var rev:revisoesAnteriores) {
            int linhaMedico=((Number)rev.get("linha_medico")).intValue();
            long revId=((Number)rev.get("id")).longValue();
            var ultima=ultimasRevisoes.get(linhaMedico);
            String depois=ultima!=null && ultima.id()==revId
                    ? json.writeValueAsString(porLinha.get(linhaMedico))
                    : rev.get("depois").toString();
            jdbc.update("insert into revisao_visita(conciliacao_id,linha_medico,acao,responsavel,justificativa,antes,depois,criado_em) values(?,?,?,?,?,?::jsonb,?::jsonb,?)",
                    novoId,linhaMedico,rev.get("acao"),rev.get("responsavel"),rev.get("justificativa"),rev.get("antes"),depois,rev.get("criado_em"));
        }
        jdbc.update("delete from vinculo_base_hospitalar where relatorio_id=?",relatorioId);
        jdbc.update("update arquivo_base_hospitalar set relatorio_id=? where id=?",novoId,arquivo.get("id"));
        vinculos.sincronizar(novoId,relatorio);
        return novoId;
    }

    private ConciliadorVisitas.Visita atualizarRevisada(ConciliadorVisitas.Visita anterior,
            RevisaoVisitasService.Revisao ultima, List<ConciliadorVisitas.RegistroHospital> candidatos) throws Exception {
        if(anterior.hospital()==null) {
            return new ConciliadorVisitas.Visita(anterior.linhaMedico(),anterior.atendimento(),anterior.data(),
                    anterior.status(),anterior.motivo(),anterior.original(),null,candidatos,
                    anterior.camposRevisados(),anterior.medicoProducao());
        }
        var hospital=registroHospital(anterior.hospital().linha());
        var listaCandidatos=candidatos.stream().anyMatch(item->item.linha()==hospital.linha())
                ? candidatos.stream().map(item->item.linha()==hospital.linha()?hospital:item).toList()
                : java.util.stream.Stream.concat(candidatos.stream(),java.util.stream.Stream.of(hospital)).toList();
        var status=anterior.status();
        if(ultima.acao()==RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA
                || ultima.acao()==RevisaoVisitaRequest.Acao.CORRESPONDENCIA_AUTOMATICA) {
            if(!hospital.medico().isBlank() && !hospital.codigo().isBlank()
                    && hospital.valorTotal()!=null && hospital.repasse()!=null) {
                status=LeitorPlanilhaVisitas.normalizar(hospital.setor()).equals("faturado")
                        ? ConciliadorVisitas.Status.PAGA : ConciliadorVisitas.Status.PENDENTE;
            }
        }
        return new ConciliadorVisitas.Visita(anterior.linhaMedico(),anterior.atendimento(),hospital.data(),
                status,anterior.motivo(),anterior.original(),hospital,listaCandidatos,
                anterior.camposRevisados(),anterior.medicoProducao());
    }

    private ConciliadorVisitas.RegistroHospital registroHospital(int id) throws Exception {
        String campos=jdbc.queryForObject("select campos::text from visita_base_hospitalar where id=?",String.class,id);
        var linha=new LeitorPlanilhaVisitas.Linha(id,json.readValue(campos,new TypeReference<Map<String,String>>(){}));
        return motor.conciliar("Base","","",List.of(),List.of(linha)).hospitalSemProducao().getFirst();
    }

    private ConciliadorVisitas.Visita atualizarVinculada(ConciliadorVisitas.Relatorio relatorio,ConciliadorVisitas.Visita anterior) throws Exception {
        int id=anterior.hospital().linha();
        String campos=jdbc.queryForObject("select campos::text from visita_base_hospitalar where id=?",String.class,id);
        var hospital=new LeitorPlanilhaVisitas.Linha(id,json.readValue(campos,new TypeReference<Map<String,String>>(){}));
        Long medico=medicos.identificar(anterior,medicos.listar());
        return identificacao.conciliar(relatorio.hospital(),relatorio.arquivoMedico(),"Base hospitalar",
                List.of(new LeitorPlanilhaVisitas.Linha(anterior.linhaMedico(),anterior.original())),List.of(hospital),medico).visitas().getFirst();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> arquivos(long hospitalId) {
        return jdbc.queryForList("select a.id,a.tipo,a.nome,a.competencia,a.criado_em,a.relatorio_id,a.modo_hospital,m.nome as medico,case when a.tipo='PRODUCAO' and a.medico_id is null then 'VARIOS' else 'UNICO' end as modo_producao from arquivo_base_hospitalar a left join medico_cadastro m on m.id=a.medico_id where a.hospital_id=? order by a.id desc",hospitalId);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> visitas(long hospitalId) {
        return jdbc.queryForList("""
                select v.id,v.linha,v.atendimento,v.data_visita,v.medico_nome,v.decisao,v.anterior_id,a.competencia,a.id as arquivo_id,v.arquivo_atual_id,v.linha_atual,
                b.relatorio_id,b.linha_producao,v.campos->>'produto' as procedimento,
                case when v.decisao='EM_ANALISE' then 'DIVERGENTE'
                     when v.decisao in ('DUPLICADO_PAGO_OUTRO_MES','DUPLICADO_MESMA_COMPETENCIA') then v.decisao
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
        return jdbc.queryForList("""
                select r.id,r.decisao,r.anterior_id,r.justificativa,r.evidencia,r.criado_em,u.nome as responsavel
                from revisao_base_hospitalar r join usuario_acesso u on u.id=r.usuario_id where visita_id=?
                union all
                select a.id,'ATUALIZACAO_HOSPITALAR',null,
                'Setor: ' || coalesce(a.antes->>'setor','') || ' -> ' || coalesce(a.depois->>'setor','') ||
                '; repasse: ' || coalesce(a.antes->>'vl. a repassar','Nao informado') || ' -> ' || coalesce(a.depois->>'vl. a repassar','Nao informado'),
                'Arquivo ' || a.arquivo_id || ', linha ' || a.linha,a.criado_em,u.nome
                from atualizacao_visita_hospitalar a join usuario_acesso u on u.id=a.usuario_id where visita_id=?
                order by criado_em,id
                """,visitaId,visitaId);
    }

    @Transactional(rollbackFor=Exception.class)
    public void decidir(int visitaId, Decisao dados, UsuarioAutenticado usuario) {
        if(dados==null || !List.of("VISITA_DISTINTA","DUPLICADO_PAGO_OUTRO_MES","DUPLICADO_MESMA_COMPETENCIA").contains(Objects.toString(dados.acao(),""))) throw new IllegalArgumentException("Decisao invalida");
        validarTexto(dados.justificativa(),"Justificativa");
        var registros=jdbc.queryForList("select * from visita_base_hospitalar where id=?",visitaId);
        if(registros.isEmpty()) throw new RecursoNaoEncontradoException("Visita nao encontrada");
        long hospitalId=((Number)registros.getFirst().get("hospital_id")).longValue();
        vinculos.bloquear(hospitalId);
        var visita=jdbc.queryForMap("select * from visita_base_hospitalar where id=? for update",visitaId);
        if(!visita.get("decisao").equals("EM_ANALISE")) throw new ConflitoRevisaoException("Esta ocorrencia ja foi analisada. Atualize a base");
        String evidencia=Objects.toString(dados.evidencia(),"").trim();
        Integer anteriorId=dados.anteriorId();
        boolean mesmoMes=dados.acao().equals("DUPLICADO_MESMA_COMPETENCIA");
        if(mesmoMes || dados.acao().equals("DUPLICADO_PAGO_OUTRO_MES")) {
            if(!mesmoMes) validarTexto(evidencia,"Evidencia do pagamento anterior");
            else evidencia="";
            if(anteriorId==null || anteriorId==visitaId) throw new IllegalArgumentException("Informe o ID original da visita");
            var anteriores=jdbc.queryForList("select v.*,a.competencia from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id=v.arquivo_id where v.id=? and v.hospital_id=? and v.decisao in ('ORIGINAL','VISITA_DISTINTA')",anteriorId,hospitalId);
            if(anteriores.isEmpty()) throw new IllegalArgumentException("Visita original invalida ou de outro hospital");
            var anterior=anteriores.getFirst();
            if(!anterior.get("atendimento").equals(visita.get("atendimento")) || !anterior.get("data_visita").equals(visita.get("data_visita"))
                    || !mesmoMedico(anterior.get("medico_nome").toString(),visita.get("medico_nome").toString(),medicos.listar()))
                throw new IllegalArgumentException("Confira atendimento, data e medico da visita original");
            String competencia=jdbc.queryForObject("select competencia from arquivo_base_hospitalar where id=?",String.class,visita.get("arquivo_id"));
            if(mesmoMes && !competencia.equals(anterior.get("competencia"))) throw new IllegalArgumentException("Duplicidade na mesma competencia exige competencias iguais");
            if(!mesmoMes && competencia.equals(anterior.get("competencia"))) throw new IllegalArgumentException("Pago em outro mes exige competencias diferentes");
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
    static String normalizarCompetencia(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            throw new IllegalArgumentException("Formato invalido. Informe a competencia no formato AAAA/MM ou AAAA-MM (ex: 2026/01)");
        }
        String limpo = competencia.trim().replace('/', '-');
        if (limpo.matches("^[0-9]{4}-[0-9]{2}$")) {
            try {
                YearMonth.parse(limpo);
                return limpo;
            } catch (Exception erro) {
                throw new IllegalArgumentException("Formato invalido. Informe a competencia no formato AAAA/MM ou AAAA-MM (ex: 2026/01)");
            }
        }
        if (limpo.matches("^[0-9]{1,2}-[0-9]{4}$")) {
            String[] partes = limpo.split("-");
            String mes = partes[0].length() == 1 ? "0" + partes[0] : partes[0];
            String ano = partes[1];
            String normalizado = ano + "-" + mes;
            try {
                YearMonth.parse(normalizado);
                return normalizado;
            } catch (Exception erro) {
                throw new IllegalArgumentException("Formato invalido. Informe a competencia no formato AAAA/MM ou AAAA-MM (ex: 2026/01)");
            }
        }
        String lower = competencia.trim().toLowerCase(Locale.ROOT);
        Map<String, String> meses = Map.ofEntries(
            Map.entry("janeiro", "01"), Map.entry("jan", "01"),
            Map.entry("fevereiro", "02"), Map.entry("fev", "02"),
            Map.entry("marco", "03"), Map.entry("março", "03"), Map.entry("mar", "03"),
            Map.entry("abril", "04"), Map.entry("abr", "04"),
            Map.entry("maio", "05"), Map.entry("mai", "05"),
            Map.entry("junho", "06"), Map.entry("jun", "06"),
            Map.entry("julho", "07"), Map.entry("jul", "07"),
            Map.entry("agosto", "08"), Map.entry("ago", "08"),
            Map.entry("setembro", "09"), Map.entry("set", "09"),
            Map.entry("outubro", "10"), Map.entry("out", "10"),
            Map.entry("novembro", "11"), Map.entry("nov", "11"),
            Map.entry("dezembro", "12"), Map.entry("dez", "12")
        );
        for (var entry : meses.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                var matcher = java.util.regex.Pattern.compile("(20[0-9]{2}|19[0-9]{2})").matcher(lower);
                if (matcher.find()) {
                    String normalizado = matcher.group() + "-" + entry.getValue();
                    try {
                        YearMonth.parse(normalizado);
                        return normalizado;
                    } catch (Exception erro) {
                        throw new IllegalArgumentException("Formato invalido. Informe a competencia no formato AAAA/MM ou AAAA-MM (ex: 2026/01)");
                    }
                }
            }
        }
        throw new IllegalArgumentException("Formato invalido. Informe a competencia no formato AAAA/MM ou AAAA-MM (ex: 2026/01)");
    }

    static void validarCompetencia(String competencia) {
        normalizarCompetencia(competencia);
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
