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
    public record Medico(long id, String nome, String crm, String uf, boolean ativo, long versao, List<String> aliases) {}
    public record Dados(@NotBlank @Size(max=180) String nome, @NotBlank @Pattern(regexp="[0-9]{1,20}") String crm,
            @NotBlank @Pattern(regexp="AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String uf,
            boolean ativo, @NotNull @Size(max=30) List<@NotBlank @Size(max=180) String> aliases,
            @NotNull @PositiveOrZero Long versao) {}
    private final JdbcTemplate jdbc;
    public MedicoCadastroService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public List<Medico> listar() {
        var aliases=jdbc.queryForList("select medico_id, nome from medico_alias order by nome");
        Map<Long,List<String>> porMedico=new HashMap<>();
        aliases.forEach(a -> porMedico.computeIfAbsent(((Number)a.get("medico_id")).longValue(),k->new ArrayList<>()).add(a.get("nome").toString()));
        return jdbc.query("select * from medico_cadastro order by nome, uf, crm",(r,n)->new Medico(r.getLong("id"),r.getString("nome"),r.getString("crm"),r.getString("uf"),r.getBoolean("ativo"),r.getLong("versao"),List.copyOf(porMedico.getOrDefault(r.getLong("id"),List.of()))));
    }
    @Transactional(readOnly=true)
    public Medico buscar(long id) {
        return listar().stream().filter(m->m.id()==id).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Medico nao encontrado"));
    }
    @Transactional
    public Medico salvar(Long id, Dados dados) {
        String nome=dados.nome().trim();
        if(nome.isBlank()) throw new IllegalArgumentException("Informe o nome do medico");
        String crm=dados.crm().replaceFirst("^0+(?!$)","");
        Map<String,String> aliases=new LinkedHashMap<>();
        for(String alias:dados.aliases()) {
            String chave=LeitorPlanilhaVisitas.normalizar(alias);
            if(chave.isBlank()) throw new IllegalArgumentException("Alias vazio");
            aliases.put(chave,alias.trim());
        }
        try {
            if(id==null) {
                if(dados.versao()!=0) throw new IllegalArgumentException("Versao inicial invalida");
                id=jdbc.queryForObject("insert into medico_cadastro(nome,crm,uf,ativo) values(?,?,?,?) returning id",Long.class,nome,crm,dados.uf(),dados.ativo());
            } else {
                buscar(id);
                if(jdbc.update("update medico_cadastro set nome=?,crm=?,uf=?,ativo=?,versao=versao+1 where id=? and versao=?",nome,crm,dados.uf(),dados.ativo(),id,dados.versao())==0)
                    throw new ConflitoRevisaoException("Cadastro alterado por outro operador. Recarregue antes de salvar");
                jdbc.update("delete from medico_alias where medico_id=?",id);
            }
            for(var alias:aliases.entrySet()) jdbc.update("insert into medico_alias(medico_id,nome,normalizado) values(?,?,?)",id,alias.getValue(),alias.getKey());
            return buscar(id);
        } catch(DuplicateKeyException e) {
            throw new ConflitoRevisaoException("CRM/UF ja cadastrado ou alias vinculado a outro medico");
        }
    }

    public Long identificar(ConciliadorVisitas.Visita v, List<Medico> medicos) {
        if(v.medicoCadastroId()!=null) return v.medicoCadastroId();
        String nome=LeitorPlanilhaVisitas.normalizar(v.medicoEfetivo());
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
