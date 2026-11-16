package br.com.faturamed.producao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Registro de acesso a dados de pacientes (LGPD): quem abriu, baixou ou revelou dados sensiveis, e quando. */
@Component
public class RegistroAcesso {
    public record Acesso(String usuario, String perfil, String acao, String detalhe, LocalDateTime quando) {}

    private final JdbcTemplate jdbc;
    public RegistroAcesso(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void registrar(String acao, String entidade, Long id, String detalhe) {
        String usuario = null, perfil = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) { usuario = u.email(); perfil = u.perfil(); }
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest r = a.getRequest();
            String encaminhado = r.getHeader("X-Forwarded-For");
            ip = encaminhado != null && !encaminhado.isBlank() ? encaminhado.split(",")[0].trim() : r.getRemoteAddr();
        }
        jdbc.update("insert into acesso_dados(usuario,perfil,acao,entidade,entidade_id,detalhe,ip) values(?,?,?,?,?,?,?)",
                usuario, perfil, acao, entidade, id, detalhe == null ? "" : detalhe.length() > 300 ? detalhe.substring(0, 300) : detalhe,
                ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip);
    }

    public List<Acesso> listar(String entidade, long id) {
        return jdbc.query("""
                select coalesce((select u.nome from usuario_acesso u where lower(u.email)=lower(a.usuario) limit 1), a.usuario) quem,
                       a.perfil, a.acao, a.detalhe, a.quando
                from acesso_dados a where a.entidade=? and a.entidade_id=? order by a.quando desc limit 200""",
                (r, n) -> new Acesso(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getTimestamp(5).toLocalDateTime()), entidade, id);
    }
}
