package br.com.faturamed.conciliacao;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class AcompanhamentoCobrancaService {
    public record Contato(long versao, ContatoCobrancaRequest.Situacao situacao, LocalDate dataCobranca,
            String resposta, String observacoes, LocalDate proximoContato, String responsavel, Instant criadoEm) {}
    public record Detalhe(int linha, String atendimento, String medico, boolean podeEditar, Contato atual, List<Contato> historico) {}
    public record Resumo(int linha, String atendimento, String medico, String statusConciliacao, boolean naFila, Contato atual) {}
    public record ResultadoLote(int registradas) {}
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    public AcompanhamentoCobrancaService(JdbcTemplate jdbc,RevisaoVisitasService revisoes) { this.jdbc=jdbc;this.revisoes=revisoes; }
    private long raiz(long id) {
        return jdbc.queryForObject("""
                with recursive cadeia as (
                  select id,anterior_id from conciliacao_visitas where id=?
                  union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id
                ) select id from cadeia where anterior_id is null
                """,Long.class,id);
    }
    private boolean historico(long id) { return !jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty(); }
    public List<Resumo> listar(long id) throws Exception {
        var estado=revisoes.carregar(id);long raiz=raiz(id);
        var contatos=jdbc.queryForList("select distinct on (linha_medico) * from cobranca_contato where relatorio_raiz_id=? order by linha_medico,id desc",raiz);
        Map<Integer,ConciliadorVisitas.Visita> visitas=new HashMap<>();estado.atual().visitas().forEach(v->visitas.put(v.linhaMedico(),v));
        List<Resumo> lista=new ArrayList<>();
        for(var row:contatos){int linha=((Number)row.get("linha_medico")).intValue();var v=visitas.get(linha);
            if(v!=null)lista.add(new Resumo(linha,v.atendimento(),v.medicoEfetivo(),v.status().name(),FilaCobranca.elegivel(v),contato(row)));
        }
        return List.copyOf(lista);
    }
    public Detalhe detalhar(long id,int linha) throws Exception {
        var estado=revisoes.carregar(id);
        var visita=estado.atual().visitas().stream().filter(v->v.linhaMedico()==linha).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Visita nao encontrada"));
        var registros=jdbc.queryForList("select * from cobranca_contato where relatorio_raiz_id=? and linha_medico=? order by id desc",raiz(id),linha);
        var contatos=registros.stream().map(this::contato).toList();
        var atual=contatos.isEmpty()?new Contato(0,ContatoCobrancaRequest.Situacao.NAO_COBRADA,null,"","",null,"",null):contatos.getFirst();
        return new Detalhe(linha,visita.atendimento(),visita.medicoEfetivo(),!historico(id)&&FilaCobranca.elegivel(visita),atual,contatos);
    }
    @Transactional(rollbackFor=Exception.class)
    public Detalhe registrar(long id,int linha,ContatoCobrancaRequest request,UsuarioAutenticado usuario) throws Exception {
        request.validar(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        bloquear(id);
        var detalhe=detalhar(id,linha);
        if(!detalhe.podeEditar())throw new ConflitoRevisaoException("A visita nao esta mais na fila ou o relatorio possui nova versao. Atualize antes de registrar");
        if(detalhe.atual().versao()!=request.versao())throw new ConflitoRevisaoException("O acompanhamento foi alterado por outro usuario. Recarregue antes de salvar");
        inserir(raiz(id),id,linha,request,usuario);
        return detalhar(id,linha);
    }
    @Transactional(rollbackFor=Exception.class)
    public ResultadoLote registrarLote(long id,CobrancaLoteRequest request,UsuarioAutenticado usuario) throws Exception {
        request.validar(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        bloquear(id);
        if(historico(id))throw new ConflitoRevisaoException("O relatorio possui nova versao. Atualize a fila antes de registrar o lote");
        var estado=revisoes.carregar(id);long raiz=raiz(id);
        Map<Integer,ConciliadorVisitas.Visita> visitas=new HashMap<>();estado.atual().visitas().forEach(v->visitas.put(v.linhaMedico(),v));
        Map<Integer,Long> versoes=new HashMap<>();
        jdbc.queryForList("select distinct on (linha_medico) linha_medico,id from cobranca_contato where relatorio_raiz_id=? order by linha_medico,id desc",raiz)
                .forEach(row->versoes.put(((Number)row.get("linha_medico")).intValue(),((Number)row.get("id")).longValue()));
        // Validate the entire selection under the same lock before appending any event.
        for(var selecao:request.visitas()) {
            var visita=visitas.get(selecao.linha());
            if(visita==null||!FilaCobranca.elegivel(visita))throw new ConflitoRevisaoException("Linha "+selecao.linha()+" nao esta mais na fila. Nenhum contato do lote foi salvo");
            if(!Objects.equals(versoes.getOrDefault(selecao.linha(),0L),selecao.versao()))throw new ConflitoRevisaoException("Acompanhamento da linha "+selecao.linha()+" foi alterado. Atualize a fila; nenhum contato do lote foi salvo");
        }
        for(var selecao:request.visitas())inserir(raiz,id,selecao.linha(),request.contato(selecao.versao()),usuario);
        return new ResultadoLote(request.visitas().size());
    }
    private void bloquear(long id) {
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",id).isEmpty())throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
    }
    private void inserir(long raiz,long id,int linha,ContatoCobrancaRequest request,UsuarioAutenticado usuario) {
        jdbc.update("""
                insert into cobranca_contato(relatorio_raiz_id,relatorio_id,linha_medico,situacao,data_cobranca,resposta,observacoes,proximo_contato,responsavel,usuario_id)
                values(?,?,?,?,?,?,?,?,?,?)
                """,raiz,id,linha,request.situacao().name(),request.dataCobranca(),texto(request.resposta()),texto(request.observacoes()),
                request.proximoContato(),usuario.nome()+" ("+usuario.email()+")",usuario.id());
    }
    private String texto(String value) { return value==null?"":value.trim(); }
    private LocalDate data(Object value) { return value==null?null:value instanceof java.sql.Date d?d.toLocalDate():(LocalDate)value; }
    private Contato contato(Map<String,Object> row) {
        Object timestamp=row.get("criado_em");
        Instant criado=timestamp instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)timestamp).toInstant();
        return new Contato(((Number)row.get("id")).longValue(),ContatoCobrancaRequest.Situacao.valueOf(row.get("situacao").toString()),
                data(row.get("data_cobranca")),texto((String)row.get("resposta")),texto((String)row.get("observacoes")),data(row.get("proximo_contato")),row.get("responsavel").toString(),criado);
    }
}
