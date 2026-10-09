package br.com.faturamed.conciliacao;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
public class HospitalCadastroService {
    public record Hospital(long id,String nome,String documento,String cidade,String uf,boolean ativo,long versao) {}
    public record Dados(@NotBlank @Size(max=180) String nome,@Size(max=30) String documento,
            @NotNull @Size(max=120) String cidade,
            @NotNull @Pattern(regexp="|AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String uf,
            boolean ativo,@NotNull @PositiveOrZero Long versao) {}
    private final JdbcTemplate jdbc;
    public HospitalCadastroService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public List<Hospital> listar() {
        return jdbc.query("select * from hospital_cadastro order by nome",(r,n)->new Hospital(r.getLong("id"),r.getString("nome"),r.getString("documento"),r.getString("cidade"),r.getString("uf"),r.getBoolean("ativo"),r.getLong("versao")));
    }
    @Transactional(readOnly=true)
    public Hospital buscar(long id) {
        return listar().stream().filter(h->h.id()==id).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Hospital nao encontrado"));
    }
    @Transactional
    public Hospital salvar(Long id,Dados dados) {
        String nome=dados.nome().trim();
        if(nome.isBlank()) throw new IllegalArgumentException("Informe o nome do hospital");
        String documento=dados.documento()==null?null:dados.documento().trim();
        String normalizado=normalizarDocumento(documento);
        if(normalizado==null) documento=null;
        try {
            if(id==null) {
                if(dados.versao()!=0) throw new IllegalArgumentException("Versao inicial invalida");
                id=jdbc.queryForObject("insert into hospital_cadastro(nome,nome_normalizado,documento,documento_normalizado,cidade,uf,ativo) values(?,?,?,?,?,?,?) returning id",
                        Long.class,nome,LeitorPlanilhaVisitas.normalizar(nome),documento,normalizado,dados.cidade().trim(),dados.uf(),dados.ativo());
            } else {
                buscar(id);
                if(jdbc.update("update hospital_cadastro set nome=?,nome_normalizado=?,documento=?,documento_normalizado=?,cidade=?,uf=?,ativo=?,versao=versao+1 where id=? and versao=?",
                        nome,LeitorPlanilhaVisitas.normalizar(nome),documento,normalizado,dados.cidade().trim(),dados.uf(),dados.ativo(),id,dados.versao())==0)
                    throw new ConflitoRevisaoException("Cadastro alterado por outro operador. Recarregue antes de salvar");
            }
            return buscar(id);
        } catch(DuplicateKeyException e) { throw new ConflitoRevisaoException("Nome ou documento ja cadastrado em outro hospital"); }
    }
    static String normalizarDocumento(String documento) {
        if(documento==null||documento.isBlank()) return null;
        String normalizado=documento.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);
        if(normalizado.isBlank()) throw new IllegalArgumentException("Documento invalido");
        return normalizado;
    }
}
