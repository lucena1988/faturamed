package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.MedicoCadastroService;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService implements UserDetailsService {
    public record Usuario(long id, String email, String nome, String perfil, Long medicoId, boolean ativo, long versao) {}
    public record Novo(@NotBlank @Email @Size(max=180) String email, @NotBlank @Size(max=180) String nome,
            @NotBlank String senha, @Pattern(regexp="ADMIN|MEDICO") @NotNull String perfil, Long medicoId) {}
    public record Situacao(boolean ativo, @NotNull @PositiveOrZero Long versao) {}
    public record NovaSenha(@NotBlank String senha, @NotNull @PositiveOrZero Long versao) {}
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final MedicoCadastroService medicos;
    public UsuarioService(JdbcTemplate jdbc, PasswordEncoder encoder, MedicoCadastroService medicos) {
        this.jdbc = jdbc; this.encoder = encoder; this.medicos = medicos;
    }
    public static String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("A senha deve ter no minimo 12 caracteres e no maximo 72 bytes");
    }
    @Override public UsuarioAutenticado loadUserByUsername(String email) {
        var users = jdbc.query("""
                select u.*, (u.ativo and coalesce(m.ativo,true)) as habilitado,
                (u.bloqueado_ate > now()) as bloqueado
                from usuario_acesso u left join medico_cadastro m on m.id=u.medico_id where u.email=?
                """, (r,n) -> new UsuarioAutenticado(r.getLong("id"),r.getString("email"),r.getString("nome"),r.getString("senha_hash"),
                r.getString("perfil"),r.getObject("medico_id",Long.class),r.getBoolean("habilitado"),r.getBoolean("bloqueado"),r.getLong("versao")), email(email));
        return users.stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));
    }
    public List<Usuario> listar() {
        return jdbc.query("select * from usuario_acesso order by nome,id", (r,n) -> new Usuario(r.getLong("id"),r.getString("email"),r.getString("nome"),
                r.getString("perfil"),r.getObject("medico_id",Long.class),r.getBoolean("ativo"),r.getLong("versao")));
    }
    @Transactional public Usuario criar(Novo dados) {
        validarSenha(dados.senha());
        if (dados.nome().isBlank() || !Set.of("ADMIN", "MEDICO").contains(dados.perfil())) throw new IllegalArgumentException("Dados de acesso invalidos");
        if (dados.perfil().equals("MEDICO")) {
            if (dados.medicoId()==null || !medicos.buscar(dados.medicoId()).ativo()) throw new IllegalArgumentException("Selecione um medico ativo");
        } else if (dados.medicoId()!=null) throw new IllegalArgumentException("Administrador nao deve ser vinculado a medico");
        try {
            long id=jdbc.queryForObject("insert into usuario_acesso(email,nome,senha_hash,perfil,medico_id) values(?,?,?,?,?) returning id",
                    Long.class,email(dados.email()),dados.nome().trim(),encoder.encode(dados.senha()),dados.perfil(),dados.medicoId());
            return buscar(id);
        } catch(DuplicateKeyException e) { throw new ConflitoRevisaoException("Email ou medico ja possui acesso cadastrado"); }
    }
    public Usuario buscar(long id) { return listar().stream().filter(u->u.id()==id).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Usuario nao encontrado")); }
    @Transactional public Usuario situacao(long id, Situacao dados, long operador) {
        // Serialize changes to keep at least one enabled administrator.
        jdbc.execute("lock table usuario_acesso in share row exclusive mode");
        var user=buscar(id);
        if (!dados.ativo() && id==operador) throw new IllegalArgumentException("Nao e permitido desativar o proprio acesso");
        if (!dados.ativo() && user.perfil().equals("ADMIN") && jdbc.queryForObject("select count(*) from usuario_acesso where perfil='ADMIN' and ativo",Long.class)<=1)
            throw new IllegalArgumentException("Mantenha pelo menos um administrador ativo");
        if (dados.ativo() && user.medicoId()!=null && !medicos.buscar(user.medicoId()).ativo()) throw new IllegalArgumentException("Medico inativo");
        if(jdbc.update("update usuario_acesso set ativo=?,versao=versao+1 where id=? and versao=?",dados.ativo(),id,dados.versao())!=1)
            throw new ConflitoRevisaoException("Acesso alterado. Recarregue a lista");
        return buscar(id);
    }
    @Transactional public void redefinir(long id, NovaSenha dados) {
        buscar(id); validarSenha(dados.senha());
        if(jdbc.update("update usuario_acesso set senha_hash=?,versao=versao+1,falhas=0,bloqueado_ate=null where id=? and versao=?",encoder.encode(dados.senha()),id,dados.versao())!=1)
            throw new ConflitoRevisaoException("Acesso alterado. Recarregue a lista");
    }
    public void falha(String email) {
        jdbc.update("""
            update usuario_acesso set falhas=case when bloqueado_ate<=now() then 1 else falhas+1 end,
            bloqueado_ate=case when (case when bloqueado_ate<=now() then 1 else falhas+1 end)>=5 then now()+interval '15 minutes' else null end
            where email=? and (bloqueado_ate is null or bloqueado_ate<=now())
            """,email(email));
    }
    public void sucesso(long id) { jdbc.update("update usuario_acesso set falhas=0,bloqueado_ate=null where id=?",id); }
    public boolean sessaoValida(UsuarioAutenticado user) {
        try { var atual=loadUserByUsername(user.email()); return atual.isEnabled() && atual.versao()==user.versao(); }
        catch(UsernameNotFoundException e) { return false; }
    }
}
