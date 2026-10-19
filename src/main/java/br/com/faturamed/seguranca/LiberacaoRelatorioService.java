package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class LiberacaoRelatorioService {
    public record Dados(@NotNull @PositiveOrZero Long versao,@NotNull @Size(max=2000) String observacao,boolean confirmaPendencias) {}
    public record Registro(long id,long relatorio,long versao,String responsavel,String observacao,Instant criadoEm) {}
    public record Estado(String situacao,long versao,boolean podeLiberar,long visitas,long pendentes,long divergentes,long semMedico,Registro publicado,List<Registro> historico) {}
    private final JdbcTemplate jdbc;private final RevisaoVisitasService revisoes;private final MedicoCadastroService medicos;private final ObjectMapper json;
    public LiberacaoRelatorioService(JdbcTemplate jdbc,RevisaoVisitasService revisoes,MedicoCadastroService medicos,ObjectMapper json){this.jdbc=jdbc;this.revisoes=revisoes;this.medicos=medicos;this.json=json;}
    private long raiz(long id){return jdbc.queryForObject("""
        with recursive cadeia as (select id,anterior_id from conciliacao_visitas where id=?
        union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id)
        select id from cadeia where anterior_id is null
        """,Long.class,id);}
    private boolean historico(long id){return !jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty();}
    private Map<Long,List<PortalMedicoController.Visita>> preparar(ConciliadorVisitas.Relatorio relatorio){
        var cadastros=medicos.listar();Map<Long,MedicoCadastroService.Medico> porId=new HashMap<>();cadastros.forEach(m->porId.put(m.id(),m));Map<Long,List<PortalMedicoController.Visita>> dados=new LinkedHashMap<>();
        for(var v:relatorio.visitas()){Long id=medicos.identificar(v,cadastros);var medico=porId.get(id);if(medico!=null)dados.computeIfAbsent(id,k->new ArrayList<>()).add(PortalMedicoController.sanitizar(v,medico));}
        return dados;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Estado consultar(long id) throws Exception {
        var estado=revisoes.carregar(id);var relatorio=estado.atual();var dados=preparar(relatorio);
        var registros=jdbc.queryForList("select id,relatorio_id,versao_revisao,responsavel,observacao,criado_em from liberacao_relatorio where relatorio_raiz_id=? order by id desc",raiz(id)).stream().map(this::registro).toList();
        Registro publicado=registros.isEmpty()?null:registros.getFirst();boolean liberado=publicado!=null&&publicado.relatorio()==id&&publicado.versao()==estado.versao();
        return new Estado(liberado?"LIBERADO":"EM_REVISAO",estado.versao(),!historico(id)&&!liberado,relatorio.visitas().size(),relatorio.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.PENDENTE).count(),relatorio.visitas().stream().filter(v->v.status()==ConciliadorVisitas.Status.DIVERGENTE).count(),relatorio.visitas().size()-dados.values().stream().mapToInt(List::size).sum(),publicado,registros);
    }
    @Transactional(rollbackFor=Exception.class)
    public Estado liberar(long id,Dados request,UsuarioAutenticado user) throws Exception {
        if(request.versao()==null||request.versao()<0||request.observacao()==null||request.observacao().length()>2000)throw new IllegalArgumentException("Dados da liberacao invalidos");
        if(jdbc.queryForList("select id from conciliacao_visitas where id=? for update",id).isEmpty())throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        jdbc.queryForList("select id from conciliacao_visitas where id=? for update",raiz(id));
        if(historico(id))throw new ConflitoRevisaoException("Libere somente a versao mais recente do relatorio");
        var estado=revisoes.carregar(id);if(estado.versao()!=request.versao())throw new ConflitoRevisaoException("O relatorio recebeu revisoes. Atualize antes de liberar");
        if(!jdbc.queryForList("select id from liberacao_relatorio where relatorio_id=? and versao_revisao=?",id,request.versao()).isEmpty())throw new ConflitoRevisaoException("Esta revisao ja foi liberada");
        var relatorio=estado.atual();var dados=preparar(relatorio);if(dados.isEmpty())throw new IllegalArgumentException("Vincule ao menos uma visita a um medico cadastrado antes de liberar");
        boolean alerta=relatorio.visitas().stream().anyMatch(v->v.status()!=ConciliadorVisitas.Status.PAGA)||dados.values().stream().mapToInt(List::size).sum()!=relatorio.visitas().size();
        if(alerta&&!request.confirmaPendencias())throw new IllegalArgumentException("Confirme a conferencia das pendencias, divergencias e visitas sem medico");
        jdbc.update("insert into liberacao_relatorio(relatorio_id,relatorio_raiz_id,versao_revisao,hospital,dados,observacao,usuario_id,responsavel) values(?,?,?,?,?::jsonb,?,?,?)",id,raiz(id),estado.versao(),relatorio.hospital(),json.writeValueAsString(dados),request.observacao().trim(),user.id(),user.nome()+" ("+user.email()+")");
        return consultar(id);
    }
    @Transactional(readOnly=true)
    public List<PortalMedicoController.Item> listarDoMedico(long medico){
        return jdbc.query("""
            select l.relatorio_id,l.hospital,jsonb_array_length(l.dados -> cast(? as text)) as visitas from liberacao_relatorio l
            where l.dados -> cast(? as text) is not null and not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
            order by l.id desc limit 100
            """,(r,n)->new PortalMedicoController.Item(r.getLong("relatorio_id"),r.getString("hospital"),r.getLong("visitas")),String.valueOf(medico),String.valueOf(medico));
    }
    @Transactional(readOnly=true)
    public PortalMedicoController.Relatorio buscarDoMedico(long id,long medico) throws Exception {
        var rows=jdbc.queryForList("""
            select l.hospital,l.dados -> cast(? as text) as visitas from liberacao_relatorio l where l.relatorio_id=?
            and l.dados -> cast(? as text) is not null and not exists(select 1 from liberacao_relatorio n where n.relatorio_raiz_id=l.relatorio_raiz_id and n.id>l.id)
            """,String.valueOf(medico),id,String.valueOf(medico));
        if(rows.isEmpty())throw new RecursoNaoEncontradoException("Relatorio nao liberado ou nao encontrado");
        var row=rows.getFirst();List<PortalMedicoController.Visita> visitas=json.readValue(row.get("visitas").toString(),new TypeReference<List<PortalMedicoController.Visita>>(){});
        return new PortalMedicoController.Relatorio(id,row.get("hospital").toString(),visitas);
    }
    private Registro registro(Map<String,Object> r){Object v=r.get("criado_em");Instant criado=v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();return new Registro(((Number)r.get("id")).longValue(),((Number)r.get("relatorio_id")).longValue(),((Number)r.get("versao_revisao")).longValue(),r.get("responsavel").toString(),r.get("observacao").toString(),criado);}
}
