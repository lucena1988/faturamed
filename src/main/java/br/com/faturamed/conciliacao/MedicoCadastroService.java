package br.com.faturamed.conciliacao;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class MedicoCadastroService {
    public record Medico(long id, String nome, String crm, String uf, boolean ativo, long versao, List<String> aliases, List<Long> empresas,
            String email, String cpf, String empresaNome, String empresaCnpj, boolean cadastroCompleto) {
        public Medico(long id, String nome, String crm, String uf, boolean ativo, long versao, List<String> aliases, List<Long> empresas) { this(id,nome,crm,uf,ativo,versao,aliases,empresas,null,null,null,null,false); }
        public Medico(long id, String nome, String crm, String uf, boolean ativo, long versao, List<String> aliases) { this(id,nome,crm,uf,ativo,versao,aliases,List.of()); }
    }
    // CRM e UF sao opcionais no cadastro feito pela FaturaMed: o medico informa ao completar o cadastro.
    public record Dados(@NotBlank @Size(max=180) String nome, @Pattern(regexp="[0-9]{0,20}") String crm,
            @Pattern(regexp="|AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String uf,
            boolean ativo, @NotNull @Size(max=30) List<@NotBlank @Size(max=180) String> aliases,
            @NotNull @PositiveOrZero Long versao, @Size(max=50) List<@NotNull Long> empresas, @Size(max=500) String email) {
        public Dados(String nome, String crm, String uf, boolean ativo, List<String> aliases, Long versao, List<Long> empresas) { this(nome,crm,uf,ativo,aliases,versao,empresas,null); }
        public Dados(String nome, String crm, String uf, boolean ativo, List<String> aliases, Long versao) { this(nome,crm,uf,ativo,aliases,versao,null); }
    }
    /** Dados que o proprio medico completa (convite ou primeiro acesso apos a mudanca). */
    public record Complemento(String nome, String crm, String uf, String cpf, String empresaNome, String empresaCnpj, String emails) {}
    private final JdbcTemplate jdbc;
    public MedicoCadastroService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public List<Medico> listar() {
        var aliases=jdbc.queryForList("select medico_id, nome from medico_alias order by nome");
        Map<Long,List<String>> porMedico=new HashMap<>();
        aliases.forEach(a -> porMedico.computeIfAbsent(((Number)a.get("medico_id")).longValue(),k->new ArrayList<>()).add(a.get("nome").toString()));
        Map<Long,List<Long>> empresas=new HashMap<>();
        jdbc.queryForList("select medico_id, empresa_id from medico_empresa order by empresa_id").forEach(e -> empresas.computeIfAbsent(((Number)e.get("medico_id")).longValue(),k->new ArrayList<>()).add(((Number)e.get("empresa_id")).longValue()));
        return jdbc.query("select * from medico_cadastro order by nome, uf, crm",(r,n)->new Medico(r.getLong("id"),r.getString("nome"),r.getString("crm"),r.getString("uf"),r.getBoolean("ativo"),r.getLong("versao"),List.copyOf(porMedico.getOrDefault(r.getLong("id"),List.of())),List.copyOf(empresas.getOrDefault(r.getLong("id"),List.of())),
                r.getString("email"),r.getString("cpf"),r.getString("empresa_nome"),r.getString("empresa_cnpj"),r.getObject("cadastro_completo_em")!=null));
    }
    @Transactional(readOnly=true)
    public Medico buscar(long id) {
        return listar().stream().filter(m->m.id()==id).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Medico nao encontrado"));
    }
    @Transactional
    public Medico salvar(Long id, Dados dados) {
        String nome=dados.nome().trim();
        if(nome.isBlank()) throw new IllegalArgumentException("Informe o nome do medico");
        String crm=dados.crm()==null||dados.crm().isBlank()?null:dados.crm().replaceFirst("^0+(?!$)","");
        String uf=dados.uf()==null||dados.uf().isBlank()?null:dados.uf();
        // E-mail obrigatorio no cadastro novo; na edicao, sem o campo (clientes antigos), mantem o atual.
        String email=dados.email()==null?null:String.join(";",ValidacaoCadastroMedico.emails(dados.email()));
        if(id==null && email==null) throw new IllegalArgumentException("Informe o e-mail do medico para enviar o convite");
        Map<String,String> aliases=new LinkedHashMap<>();
        for(String alias:dados.aliases()) {
            String chave=LeitorPlanilhaVisitas.normalizar(alias);
            if(chave.isBlank()) throw new IllegalArgumentException("Alias vazio");
            aliases.put(chave,alias.trim());
        }
        try {
            if(id==null) {
                if(dados.versao()!=0) throw new IllegalArgumentException("Versao inicial invalida");
                id=jdbc.queryForObject("insert into medico_cadastro(nome,crm,uf,ativo,email) values(?,?,?,?,?) returning id",Long.class,nome,crm,uf,dados.ativo(),email);
            } else {
                buscar(id);
                if(jdbc.update("update medico_cadastro set nome=?,crm=?,uf=?,ativo=?,email=coalesce(?,email),versao=versao+1 where id=? and versao=?",nome,crm,uf,dados.ativo(),email,id,dados.versao())==0)
                    throw new ConflitoRevisaoException("Cadastro alterado por outro operador. Recarregue antes de salvar");
                jdbc.update("delete from medico_alias where medico_id=?",id);
            }
            for(var alias:aliases.entrySet()) jdbc.update("insert into medico_alias(medico_id,nome,normalizado) values(?,?,?)",id,alias.getValue(),alias.getKey());
            // Sem a lista (clientes antigos), os vinculos com empresas ficam como estao.
            if(dados.empresas()!=null) {
                var empresas=new LinkedHashSet<>(dados.empresas());
                for(Long empresa:empresas) if(jdbc.queryForList("select id from empresa_cliente where id=?",empresa).isEmpty()) throw new IllegalArgumentException("Empresa nao encontrada");
                jdbc.update("delete from medico_empresa where medico_id=?",id);
                for(Long empresa:empresas) jdbc.update("insert into medico_empresa(medico_id,empresa_id) values(?,?)",id,empresa);
            }
            return buscar(id);
        } catch(DuplicateKeyException e) {
            throw new ConflitoRevisaoException("CRM/UF ja cadastrado ou alias vinculado a outro medico");
        }
    }

    /** O medico completa o proprio cadastro; a empresa e vinculada pelo CNPJ (criada se ainda nao existir). */
    @Transactional
    public Medico completar(long id, Complemento dados) {
        if(dados==null) throw new IllegalArgumentException("Informe os dados do cadastro");
        String nome=ValidacaoCadastroMedico.nomeCompleto(dados.nome());
        String crm=ValidacaoCadastroMedico.crm(dados.crm());
        String uf=Objects.toString(dados.uf(),"").trim().toUpperCase(Locale.ROOT);
        if(!uf.matches("AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO")) throw new IllegalArgumentException("Selecione a UF do CRM");
        String cpf=ValidacaoCadastroMedico.cpf(dados.cpf());
        String empresaNome=Objects.toString(dados.empresaNome(),"").trim();
        if(empresaNome.length()<2 || empresaNome.length()>180) throw new IllegalArgumentException("Informe o nome da empresa");
        String cnpj=ValidacaoCadastroMedico.cnpj(dados.empresaCnpj());
        String emails=String.join(";",ValidacaoCadastroMedico.emails(dados.emails()));
        if(jdbc.queryForList("select id from medico_cadastro where id=? for update",id).isEmpty()) throw new RecursoNaoEncontradoException("Medico nao encontrado");
        try {
            jdbc.update("""
                update medico_cadastro set nome=?,crm=?,uf=?,cpf=?,empresa_nome=?,empresa_cnpj=?,email=?,cadastro_completo_em=now(),versao=versao+1 where id=?
                """,nome,crm,uf,cpf,empresaNome,cnpj,emails,id);
        } catch(DuplicateKeyException e) {
            throw new ConflitoRevisaoException("CRM/UF ou CPF ja cadastrado para outro medico. Fale com a FaturaMed");
        }
        var empresas=jdbc.queryForList("select id from empresa_cliente where documento_normalizado=?",Long.class,cnpj);
        long empresa=empresas.isEmpty()
                ? Objects.requireNonNull(jdbc.queryForObject("insert into empresa_cliente(nome,documento,documento_normalizado) values(?,?,?) returning id",Long.class,empresaNome,cnpj,cnpj))
                : empresas.getFirst();
        jdbc.update("insert into medico_empresa(medico_id,empresa_id) values(?,?) on conflict do nothing",id,empresa);
        return buscar(id);
    }
    @Transactional(readOnly=true)
    public boolean cadastroCompleto(long id) {
        return !jdbc.queryForList("select id from medico_cadastro where id=? and cadastro_completo_em is not null",id).isEmpty();
    }

    public Long identificar(ConciliadorVisitas.Visita v, List<Medico> medicos) {
        if(v.medicoCadastroId()!=null) return v.medicoCadastroId();
        return identificarNome(v.medicoEfetivo(),medicos);
    }
    public Long identificarNome(String valor, List<Medico> medicos) {
        String nome=LeitorPlanilhaVisitas.normalizar(valor);
        if(nome.isBlank()) return null;
        var candidatos=medicos.stream().filter(Medico::ativo).filter(m->LeitorPlanilhaVisitas.normalizar(m.nome()).equals(nome)
                || m.aliases().stream().anyMatch(a->LeitorPlanilhaVisitas.normalizar(a).equals(nome))).toList();
        return candidatos.size()==1?candidatos.getFirst().id():null;
    }
    public boolean nomeAmbiguo(String nome) {
        String chave=LeitorPlanilhaVisitas.normalizar(nome);
        return listar().stream().filter(Medico::ativo).filter(m->LeitorPlanilhaVisitas.normalizar(m.nome()).equals(chave)
                || m.aliases().stream().anyMatch(a->LeitorPlanilhaVisitas.normalizar(a).equals(chave))).count()>1;
    }
}
