package br.com.faturamed.conciliacao;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class EmpresaClienteService {
    public record Empresa(long id,String nome,String documento,boolean ativo,long versao,long medicos) {}
    public record Dados(@NotBlank @Size(max=180) String nome,@Size(max=30) String documento,
            boolean ativo,@NotNull @PositiveOrZero Long versao) {}
    private final JdbcTemplate jdbc;
    public EmpresaClienteService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public List<Empresa> listar() {
        return jdbc.query("select e.*,(select count(*) from medico_empresa me where me.empresa_id=e.id) as medicos from empresa_cliente e order by e.nome",
                (r,n)->new Empresa(r.getLong("id"),r.getString("nome"),r.getString("documento"),r.getBoolean("ativo"),r.getLong("versao"),r.getLong("medicos")));
    }
    @Transactional(readOnly=true)
    public Empresa buscar(long id) {
        return listar().stream().filter(e->e.id()==id).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Empresa nao encontrada"));
    }
    @Transactional(readOnly=true)
    public List<Long> medicosDa(long empresa) {
        return jdbc.queryForList("select medico_id from medico_empresa where empresa_id=? order by medico_id",Long.class,empresa);
    }
    @Transactional
    public Empresa salvar(Long id,Dados dados) {
        String nome=dados.nome().trim();
        if(nome.isBlank()) throw new IllegalArgumentException("Informe o nome da empresa");
        String documento=dados.documento()==null?null:dados.documento().trim();
        String normalizado=HospitalCadastroService.normalizarDocumento(documento);
        if(normalizado==null) documento=null;
        try {
            if(id==null) {
                if(dados.versao()!=0) throw new IllegalArgumentException("Versao inicial invalida");
                id=jdbc.queryForObject("insert into empresa_cliente(nome,documento,documento_normalizado,ativo) values(?,?,?,?) returning id",Long.class,nome,documento,normalizado,dados.ativo());
            } else {
                buscar(id);
                if(jdbc.update("update empresa_cliente set nome=?,documento=?,documento_normalizado=?,ativo=?,versao=versao+1 where id=? and versao=?",nome,documento,normalizado,dados.ativo(),id,dados.versao())==0)
                    throw new ConflitoRevisaoException("Cadastro alterado por outro operador. Recarregue antes de salvar");
            }
            return buscar(id);
        } catch(DuplicateKeyException e) { throw new ConflitoRevisaoException("CNPJ ja cadastrado em outra empresa"); }
    }
    @Transactional(readOnly=true)
    public Long atribuida(long relatorioId) {
        var linhas=jdbc.queryForList("select empresa_id from conciliacao_visitas where id=?",relatorioId);
        if(linhas.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        Object empresa=linhas.getFirst().get("empresa_id");
        return empresa==null?null:((Number)empresa).longValue();
    }
    /** Atribui (ou remove) a empresa de toda a cadeia de versoes de uma conciliacao e das suas publicacoes. */
    @Transactional
    public void atribuir(long relatorioId,Long empresaId) {
        var linhas=jdbc.queryForList("select medico_producao_id from conciliacao_visitas where id=? for update",relatorioId);
        if(linhas.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        if(empresaId!=null) {
            if(!buscar(empresaId).ativo()) throw new IllegalArgumentException("Selecione uma empresa ativa");
            Object medico=linhas.getFirst().get("medico_producao_id");
            if(medico!=null && !medicosDa(empresaId).contains(((Number)medico).longValue())) throw new IllegalArgumentException("O medico da producao nao pertence a empresa selecionada");
        }
        long raiz=jdbc.queryForObject("""
            with recursive acima as (select id,anterior_id from conciliacao_visitas where id=?
            union all select c.id,c.anterior_id from conciliacao_visitas c join acima a on c.id=a.anterior_id)
            select id from acima where anterior_id is null
            """,Long.class,relatorioId);
        jdbc.update("""
            with recursive cadeia as (select id from conciliacao_visitas where id=?
            union all select c.id from conciliacao_visitas c join cadeia k on c.anterior_id=k.id)
            update conciliacao_visitas set empresa_id=? where id in (select id from cadeia)
            """,raiz,empresaId);
        jdbc.update("update liberacao_relatorio set empresa_id=? where relatorio_raiz_id=?",empresaId,raiz);
    }
    /** Resolve a empresa da producao: a informada (validada) ou a unica empresa do medico. */
    @Transactional(readOnly=true)
    public Long resolverProducao(Long empresaId,Long medicoId) {
        if(empresaId!=null) {
            if(!buscar(empresaId).ativo()) throw new IllegalArgumentException("Selecione uma empresa ativa");
            if(medicoId!=null && !medicosDa(empresaId).contains(medicoId)) throw new IllegalArgumentException("O medico da producao nao pertence a empresa selecionada");
            return empresaId;
        }
        if(medicoId==null) return null;
        var empresas=jdbc.queryForList("select e.id from medico_empresa me join empresa_cliente e on e.id=me.empresa_id where me.medico_id=? and e.ativo",Long.class,medicoId);
        if(empresas.size()>1) throw new IllegalArgumentException("O medico atende por mais de uma empresa. Selecione a empresa da producao");
        return empresas.isEmpty()?null:empresas.getFirst();
    }
}
