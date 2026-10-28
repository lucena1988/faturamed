package br.com.faturamed.conciliacao;

import java.math.*;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class RecebimentoService {
    public record Dados(@NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal valor,
            @NotNull LocalDate data, @NotNull @Size(max=2000) String observacoes,
            @NotNull @PositiveOrZero Long versao) {}
    public record Estorno(@NotBlank @Size(max=2000) String justificativa,@NotNull @PositiveOrZero Long versao) {}
    public record Evento(long id,BigDecimal valor,LocalDate data,String observacoes,String responsavel,Instant criadoEm,Long estornoDe,boolean estornado,Long pagamentoId,String referencia) {}
    public record Visita(int linha,String atendimento,LocalDate data,Long medicoId,String medico,String codigo,String status,
            BigDecimal faturado,BigDecimal recebido,BigDecimal saldo,long versao,boolean podeRegistrar,boolean inconsistente,List<Evento> historico) {}
    public record Resultado(String hospital,boolean historico,List<Visita> visitas,long versaoConciliacao) {
        public Resultado(String hospital,boolean historico,List<Visita> visitas) { this(hospital,historico,visitas,0); }
    }
    public record Movimento(long id,int linha,String atendimento,LocalDate dataVisita,String codigo,LocalDate data,
            BigDecimal valor,Long estornoDe,boolean estornado,Long pagamentoId,String referencia) {
        public Movimento(long id,int linha,String atendimento,LocalDate dataVisita,String codigo,LocalDate data,BigDecimal valor,Long estornoDe,boolean estornado) {
            this(id,linha,atendimento,dataVisita,codigo,data,valor,estornoDe,estornado,null,"");
        }
    }
    public record PagamentoRegistrado(long pagamentoId,int visitas,BigDecimal valorTotal) {}
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    private final MedicoCadastroService medicos;
    public RecebimentoService(JdbcTemplate jdbc,RevisaoVisitasService revisoes,MedicoCadastroService medicos) {
        this.jdbc=jdbc;this.revisoes=revisoes;this.medicos=medicos;
    }
    private long raiz(long id) {
        return jdbc.queryForObject("""
            with recursive cadeia as (select id,anterior_id from conciliacao_visitas where id=?
            union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id)
            select id from cadeia where anterior_id is null
            """,Long.class,id);
    }
    private boolean historico(long id) { return !jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty(); }
    public List<Movimento> movimentosDoMedico(long id,long medicoId) {
        return jdbc.query("""
            select r.id,r.linha_medico,r.atendimento,r.data_visita,r.codigo_procedimento,r.data_recebimento,r.valor,r.estorno_de,r.pagamento_id,p.referencia,
            exists(select 1 from recebimento_visita e where e.estorno_de=r.id) as estornado
            from recebimento_visita r left join pagamento_visitas p on p.id=r.pagamento_id where r.relatorio_raiz_id=? and r.medico_id=? order by r.data_recebimento,r.id
            """,(r,n)->new Movimento(r.getLong("id"),r.getInt("linha_medico"),r.getString("atendimento"),
                    r.getObject("data_visita",LocalDate.class),r.getString("codigo_procedimento"),r.getObject("data_recebimento",LocalDate.class),
                    r.getBigDecimal("valor"),r.getObject("estorno_de",Long.class),r.getBoolean("estornado"),r.getObject("pagamento_id",Long.class),r.getString("referencia")),raiz(id),medicoId);
    }
    static BigDecimal dinheiro(BigDecimal valor) { return valor==null?null:valor.setScale(2,RoundingMode.HALF_UP); }
    public Resultado listar(long id) throws Exception {
        var estado=revisoes.carregar(id);var base=estado.atual();var cadastros=medicos.listar();boolean antigo=historico(id);
        var rows=jdbc.queryForList("select * from recebimento_visita where relatorio_raiz_id=? order by id",raiz(id));
        Map<Long,String> referencias=new HashMap<>();jdbc.queryForList("select id,referencia from pagamento_visitas where relatorio_raiz_id=?",raiz(id))
                .forEach(p->referencias.put(((Number)p.get("id")).longValue(),p.get("referencia").toString()));
        Set<Long> estornados=new HashSet<>();for(var r:rows)if(r.get("estorno_de")!=null)estornados.add(((Number)r.get("estorno_de")).longValue());
        List<Visita> result=new ArrayList<>();
        for(var v:base.visitas()) {
            Long medico=medicos.identificar(v,cadastros);BigDecimal recebido=BigDecimal.ZERO;long versao=0;boolean divergente=false;List<Evento> eventos=new ArrayList<>();
            for(var row:rows)if(((Number)row.get("linha_medico")).intValue()==v.linhaMedico()) {
                long evento=((Number)row.get("id")).longValue();BigDecimal valor=(BigDecimal)row.get("valor");recebido=recebido.add(valor);versao=evento;
                Long estorno=row.get("estorno_de")==null?null:((Number)row.get("estorno_de")).longValue();
                if(estorno==null&&!estornados.contains(evento)) {
                    Object dataVisita=row.get("data_visita");LocalDate origem=dataVisita instanceof java.sql.Date d?d.toLocalDate():(LocalDate)dataVisita;
                    if(!Objects.equals(medico,((Number)row.get("medico_id")).longValue())||!Objects.equals(v.atendimento(),row.get("atendimento"))
                            ||!Objects.equals(v.data(),origem)||!Objects.equals(v.codigoEfetivo(),row.get("codigo_procedimento")))divergente=true;
                }
                Object criado=row.get("criado_em");Instant instant=criado instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)criado).toInstant();
                Object data=row.get("data_recebimento");LocalDate dia=data instanceof java.sql.Date d?d.toLocalDate():(LocalDate)data;
                Long pagamento=row.get("pagamento_id")==null?null:((Number)row.get("pagamento_id")).longValue();
                eventos.add(new Evento(evento,valor,dia,row.get("observacoes").toString(),row.get("responsavel").toString(),instant,estorno,estornados.contains(evento),pagamento,referencias.getOrDefault(pagamento,"")));
            }
            BigDecimal faturado=v.status()==ConciliadorVisitas.Status.PAGA?dinheiro(v.repasseEfetivo()):null;
            BigDecimal saldo=faturado==null?null:faturado.subtract(recebido);
            boolean inconsistente=divergente||(recebido.signum()>0&&(saldo==null||saldo.signum()<0));
            result.add(new Visita(v.linhaMedico(),v.atendimento(),v.data(),medico,v.medicoEfetivo(),v.codigoEfetivo(),v.status().name(),faturado,recebido,saldo,versao,
                    !antigo&&!inconsistente&&medico!=null&&saldo!=null&&saldo.signum()>0,inconsistente,List.copyOf(eventos)));
        }
        return new Resultado(base.hospital(),antigo,List.copyOf(result),estado.versao());
    }
    private void bloquear(long id) {
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",id).isEmpty())throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        if(historico(id))throw new ConflitoRevisaoException("Use a versao mais recente do relatorio");
    }
    private Visita visita(long id,int linha,long versao) throws Exception {
        var v=listar(id).visitas().stream().filter(r->r.linha()==linha).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Visita nao encontrada"));
        if(v.versao()!=versao)throw new ConflitoRevisaoException("Recebimentos alterados. Recarregue antes de salvar");return v;
    }
    static void validar(Dados dados,LocalDate hoje) {
        if(dados.valor()==null||dados.valor().signum()<=0||dados.valor().scale()>2||dados.valor().precision()-dados.valor().scale()>14)throw new IllegalArgumentException("Informe valor positivo com ate duas casas decimais");
        if(dados.data()==null||dados.data().isAfter(hoje))throw new IllegalArgumentException("Data do recebimento invalida ou futura");
    }
    @Transactional(rollbackFor=Exception.class)
    public Resultado registrar(long id,int linha,Dados dados,UsuarioAutenticado usuario) throws Exception {
        validar(dados,LocalDate.now(ZoneId.of("America/Sao_Paulo")));bloquear(id);var v=visita(id,linha,dados.versao());
        if(!v.podeRegistrar())throw new ConflitoRevisaoException("Visita precisa estar faturada, com medico cadastrado e saldo confirmado");
        if(dados.valor().compareTo(v.saldo())>0)throw new IllegalArgumentException("Valor recebido excede o saldo da visita");
        inserir(id,v,v.medicoId(),dados.valor(),dados.data(),dados.observacoes(),null,usuario);return listar(id);
    }
    @Transactional(rollbackFor=Exception.class)
    public PagamentoRegistrado distribuir(long id,PagamentoDistribuidoRequest dados,UsuarioAutenticado usuario) throws Exception {
        dados.validar(LocalDate.now(ZoneId.of("America/Sao_Paulo")));bloquear(id);var estado=listar(id);
        if(estado.versaoConciliacao()!=dados.versaoConciliacao())throw new ConflitoRevisaoException("A conciliacao foi revisada. Atualize as visitas; nenhum valor foi salvo");
        Map<Integer,Visita> visitas=new HashMap<>();estado.visitas().forEach(v->visitas.put(v.linha(),v));
        for(var a:dados.visitas()) {
            var v=visitas.get(a.linha());
            if(v==null||!v.podeRegistrar()||!Objects.equals(v.medicoId(),dados.medicoId()))throw new ConflitoRevisaoException("Linha "+a.linha()+" nao esta disponivel para este medico. Nenhum valor foi salvo");
            if(v.versao()!=a.versao())throw new ConflitoRevisaoException("Saldo da linha "+a.linha()+" foi alterado. Atualize; nenhum valor foi salvo");
            if(a.valor().compareTo(v.saldo())>0)throw new IllegalArgumentException("Distribuicao excede o saldo da linha "+a.linha());
        }
        long raiz=raiz(id);String referencia=dados.referencia().trim();
        if(!jdbc.queryForList("select id from pagamento_visitas where relatorio_raiz_id=? and medico_id=? and referencia_chave=lower(?)",raiz,dados.medicoId(),referencia).isEmpty())
            throw new ConflitoRevisaoException("Referencia ja registrada para este medico e relatorio. Nenhum valor foi salvo");
        long pagamento=jdbc.queryForObject("""
            insert into pagamento_visitas(relatorio_raiz_id,relatorio_id,medico_id,data_recebimento,valor_total,referencia,observacoes,responsavel,usuario_id)
            values(?,?,?,?,?,?,?,?,?) returning id
            """,Long.class,raiz,id,dados.medicoId(),dados.data(),dados.valorTotal(),referencia,dados.observacoes().trim(),usuario.nome()+" ("+usuario.email()+")",usuario.id());
        for(var a:dados.visitas())inserir(id,visitas.get(a.linha()),dados.medicoId(),a.valor(),dados.data(),dados.observacoes(),null,usuario,pagamento);
        return new PagamentoRegistrado(pagamento,dados.visitas().size(),dados.valorTotal());
    }
    @Transactional(rollbackFor=Exception.class)
    public Resultado estornar(long id,int linha,long evento,Estorno dados,UsuarioAutenticado usuario) throws Exception {
        if(dados.justificativa()==null||dados.justificativa().isBlank())throw new IllegalArgumentException("Informe a justificativa do estorno");
        bloquear(id);var v=visita(id,linha,dados.versao());
        var registro=v.historico().stream().filter(r->r.id()==evento&&r.estornoDe()==null&&!r.estornado()).findFirst().orElseThrow(()->new ConflitoRevisaoException("Recebimento nao encontrado ou ja estornado"));
        Long medico=jdbc.queryForObject("select medico_id from recebimento_visita where id=?",Long.class,evento);
        inserir(id,v,medico,registro.valor().negate(),LocalDate.now(ZoneId.of("America/Sao_Paulo")),dados.justificativa(),evento,usuario,registro.pagamentoId());return listar(id);
    }
    private void inserir(long id,Visita visita,Long medico,BigDecimal valor,LocalDate data,String obs,Long estorno,UsuarioAutenticado usuario) {
        jdbc.update("""
            insert into recebimento_visita(relatorio_raiz_id,relatorio_id,linha_medico,medico_id,valor,data_recebimento,observacoes,responsavel,usuario_id,estorno_de,atendimento,data_visita,codigo_procedimento)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,raiz(id),id,visita.linha(),medico,valor,data,obs.trim(),usuario.nome()+" ("+usuario.email()+")",usuario.id(),estorno,visita.atendimento(),visita.data(),visita.codigo());
    }
    private void inserir(long id,Visita visita,Long medico,BigDecimal valor,LocalDate data,String obs,Long estorno,UsuarioAutenticado usuario,Long pagamento) {
        if(pagamento==null){inserir(id,visita,medico,valor,data,obs,estorno,usuario);return;}
        jdbc.update("""
            insert into recebimento_visita(relatorio_raiz_id,relatorio_id,linha_medico,medico_id,valor,data_recebimento,observacoes,responsavel,usuario_id,estorno_de,atendimento,data_visita,codigo_procedimento,pagamento_id)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,raiz(id),id,visita.linha(),medico,valor,data,obs.trim(),usuario.nome()+" ("+usuario.email()+")",usuario.id(),estorno,visita.atendimento(),visita.data(),visita.codigo(),pagamento);
    }
}
