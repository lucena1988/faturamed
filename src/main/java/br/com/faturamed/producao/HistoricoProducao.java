package br.com.faturamed.producao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Registro de quem fez o que nas folhas de producao e nos fechamentos. */
@Component
public class HistoricoProducao {
    public record Evento(String acao, String descricao, String usuario, LocalDateTime quando) {}

    private final JdbcTemplate jdbc;
    public HistoricoProducao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lote(long id, String acao, String descricao, String usuario) { registrar("LOTE", id, acao, descricao, usuario); }
    public void fechamento(long id, String acao, String descricao, String usuario) { registrar("FECHAMENTO", id, acao, descricao, usuario); }

    private void registrar(String entidade, long id, String acao, String descricao, String usuario) {
        jdbc.update("insert into producao_historico(entidade,entidade_id,acao,descricao,usuario) values(?,?,?,?,?)",
                entidade, id, acao, descricao.length() > 300 ? descricao.substring(0, 300) : descricao, usuario);
    }

    public List<Evento> listar(String entidade, long id) {
        // mostra o nome do usuario quando o registro guardou o e-mail de login
        return jdbc.query("""
                select h.*, coalesce((select u.nome from usuario u where lower(u.email)=lower(h.usuario) limit 1), h.usuario) quem
                from producao_historico h where h.entidade=? and h.entidade_id=? order by h.quando desc, h.id desc""",
                (r, n) -> new Evento(r.getString("acao"), r.getString("descricao"), r.getString("quem"),
                        r.getTimestamp("quando").toLocalDateTime()), entidade, id);
    }
}
