package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.EmpresaClienteService;
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
    public record Usuario(long id, String email, String nome, String perfil, Long medicoId, boolean ativo, long versao, Long empresaId, Long hospitalId) {
        public Usuario(long id, String email, String nome, String perfil, Long medicoId, boolean ativo, long versao) { this(id,email,nome,perfil,medicoId,ativo,versao,null,null); }
        public Usuario(long id, String email, String nome, String perfil, Long medicoId, boolean ativo, long versao, Long empresaId) { this(id,email,nome,perfil,medicoId,ativo,versao,empresaId,null); }
    }
    public record Novo(@NotBlank @Email @Size(max=180) String email, @NotBlank @Size(max=180) String nome,
            @NotBlank String senha, @Pattern(regexp="ADMIN|MEDICO|DEV|OPERADOR|EMPRESA|HOSPITAL") @NotNull String perfil, Long medicoId, Long empresaId, Long hospitalId) {
        public Novo(String email, String nome, String senha, String perfil, Long medicoId) { this(email,nome,senha,perfil,medicoId,null,null); }
        public Novo(String email, String nome, String senha, String perfil, Long medicoId, Long empresaId) { this(email,nome,senha,perfil,medicoId,empresaId,null); }
    }
    public record Situacao(boolean ativo, @NotNull @PositiveOrZero Long versao) {}
    public record NovaSenha(@NotBlank String senha, @NotNull @PositiveOrZero Long versao) {}
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final MedicoCadastroService medicos;
    private final EmpresaClienteService empresas;
    public UsuarioService(JdbcTemplate jdbc, PasswordEncoder encoder, MedicoCadastroService medicos, EmpresaClienteService empresas) {
        this.jdbc = jdbc; this.encoder = encoder; this.medicos = medicos; this.empresas = empresas;
    }
    public static String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("A senha deve ter no minimo 12 caracteres e no maximo 72 bytes");
    }
    @Override public UsuarioAutenticado loadUserByUsername(String email) {
        var users = jdbc.query("""
                select u.*, (u.ativo and coalesce(m.ativo,true) and coalesce(e.ativo,true) and coalesce(h.ativo,true)) as habilitado,
                (u.bloqueado_ate > now()) as bloqueado
                from usuario_acesso u left join medico_cadastro m on m.id=u.medico_id
                left join empresa_cliente e on e.id=u.empresa_id left join hospital_cadastro h on h.id=u.hospital_id where u.email=?
                """, (r,n) -> new UsuarioAutenticado(r.getLong("id"),r.getString("email"),r.getString("nome"),r.getString("senha_hash"),
                r.getString("perfil"),r.getObject("medico_id",Long.class),r.getBoolean("habilitado"),r.getBoolean("bloqueado"),r.getLong("versao"),
                r.getObject("empresa_id",Long.class)), email(email));
        return users.stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));
    }
    public List<Usuario> listar() {
        return jdbc.query("select * from usuario_acesso where email<>'robo@faturamed.sistema' order by nome,id", (r,n) -> new Usuario(r.getLong("id"),r.getString("email"),r.getString("nome"),
                r.getString("perfil"),r.getObject("medico_id",Long.class),r.getBoolean("ativo"),r.getLong("versao"),r.getObject("empresa_id",Long.class),
                r.getObject("hospital_id",Long.class)));
    }
    @Transactional public Usuario criar(Novo dados) { return criar(dados, null); }
    @Transactional public Usuario criar(Novo dados, Long operador) {
        validarSenha(dados.senha());
        if (dados.nome().isBlank() || !Set.of("ADMIN", "MEDICO", "DEV", "OPERADOR", "EMPRESA", "HOSPITAL").contains(dados.perfil())) throw new IllegalArgumentException("Dados de acesso invalidos");
        if (dados.perfil().equals("HOSPITAL")) {
            Boolean ativo = dados.hospitalId() == null ? null : jdbc.query("select ativo from hospital_cadastro where id=?", rs -> rs.next() ? rs.getBoolean(1) : null, dados.hospitalId());
            if (!Boolean.TRUE.equals(ativo)) throw new IllegalArgumentException("Selecione um hospital ativo");
        } else if (dados.hospitalId()!=null) throw new IllegalArgumentException("Somente o perfil Hospital pode ser vinculado a um hospital");
        if (dados.perfil().equals("MEDICO")) {
            if (dados.medicoId()==null || !medicos.buscar(dados.medicoId()).ativo()) throw new IllegalArgumentException("Selecione um medico ativo");
        } else if (dados.medicoId()!=null) throw new IllegalArgumentException("Somente o perfil Medico pode ser vinculado a um medico");
        if (dados.perfil().equals("EMPRESA")) {
            if (dados.empresaId()==null || !empresas.buscar(dados.empresaId()).ativo()) throw new IllegalArgumentException("Selecione uma empresa ativa");
        } else if (dados.empresaId()!=null) throw new IllegalArgumentException("Somente o perfil Empresa pode ser vinculado a uma empresa");
        try {
            long id=jdbc.queryForObject("insert into usuario_acesso(email,nome,senha_hash,perfil,medico_id,empresa_id,hospital_id) values(?,?,?,?,?,?,?) returning id",
                    Long.class,email(dados.email()),dados.nome().trim(),encoder.encode(dados.senha()),dados.perfil(),dados.medicoId(),dados.empresaId(),dados.hospitalId());
            auditar(id, operador, "CRIACAO");
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
        if (dados.ativo() && user.empresaId()!=null && !empresas.buscar(user.empresaId()).ativo()) throw new IllegalArgumentException("Empresa inativa");
        if(jdbc.update("update usuario_acesso set ativo=?,versao=versao+1 where id=? and versao=?",dados.ativo(),id,dados.versao())!=1)
            throw new ConflitoRevisaoException("Acesso alterado. Recarregue a lista");
        auditar(id, operador, dados.ativo() ? "ATIVACAO" : "DESATIVACAO");
        return buscar(id);
    }
    @Transactional public void redefinir(long id, NovaSenha dados) { redefinir(id, dados, null); }
    @Transactional public void redefinir(long id, NovaSenha dados, Long operador) {
        buscar(id); validarSenha(dados.senha());
        if(jdbc.update("update usuario_acesso set senha_hash=?,versao=versao+1,falhas=0,bloqueado_ate=null where id=? and versao=?",encoder.encode(dados.senha()),id,dados.versao())!=1)
            throw new ConflitoRevisaoException("Acesso alterado. Recarregue a lista");
        auditar(id, operador, "REDEFINICAO_SENHA");
    }
    private void auditar(long id, Long operador, String acao) {
        jdbc.update("insert into usuario_acesso_auditoria(usuario_id,operador_id,acao) values(?,?,?)", id, operador, acao);
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
