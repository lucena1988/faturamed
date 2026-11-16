package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status de acompanhamento nas mensagens do chat (Pendente / Em analise / Resolvido) e a lista
 * "o que esta comigo": pendencias abertas nas conversas da pessoa (mencoes a ela, mensagens dela
 * ou que ela assumiu como responsavel).
 */
@Service
public class ChatStatusService {
    public static final Set<String> STATUS = Set.of("PENDENTE", "EM_ANALISE", "RESOLVIDO");
    public record StatusMensagem(long mensagemId, String status, Long responsavelId, String responsavelNome, String alteradoPorNome, Instant alteradoEm) {}
    public record Pendencia(long mensagemId, long conversaId, String conversa, String autorNome, String texto, String status,
                            String responsavelNome, Instant alteradoEm, boolean mencionado) {}

    private final JdbcTemplate jdbc;
    public ChatStatusService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private long conversaDaMensagem(long mensagemId, UsuarioAutenticado user) {
        var l = jdbc.queryForList("""
                select m.conversa_id from chat_mensagem m join chat_participante p on p.conversa_id = m.conversa_id and p.usuario_id = ?
                where m.id = ? and m.apagada_em is null""", Long.class, user.id(), mensagemId);
        if (l.isEmpty()) throw new AccessDeniedException("Mensagem fora das suas conversas");
        return l.getFirst();
    }

    /** Define (ou limpa, com status vazio) o status da mensagem. "assumir" coloca a pessoa como responsavel. */
    @Transactional
    public StatusMensagem definir(long mensagemId, String status, boolean assumir, UsuarioAutenticado user) {
        conversaDaMensagem(mensagemId, user);
        if (status == null || status.isBlank()) {
            jdbc.update("delete from chat_mensagem_status where mensagem_id = ?", mensagemId);
            return null;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS.contains(s)) throw new IllegalArgumentException("Status inválido");
        jdbc.update("""
                insert into chat_mensagem_status(mensagem_id, status, responsavel_id, alterado_por) values (?,?,?,?)
                on conflict (mensagem_id) do update set status = excluded.status, alterado_por = excluded.alterado_por, alterado_em = now(),
                  responsavel_id = case when ? then excluded.responsavel_id else chat_mensagem_status.responsavel_id end""",
                mensagemId, s, assumir ? user.id() : null, user.id(), assumir);
        return buscar(List.of(mensagemId)).getFirst();
    }

    /** Status das mensagens de uma conversa (so para quem participa). */
    @Transactional(readOnly = true)
    public List<StatusMensagem> daConversa(long conversaId, UsuarioAutenticado user) {
        Integer participa = jdbc.queryForObject("select count(*) from chat_participante where conversa_id = ? and usuario_id = ?", Integer.class, conversaId, user.id());
        if (participa == null || participa == 0) throw new AccessDeniedException("Conversa não encontrada");
        return jdbc.query(SELECT + " join chat_mensagem m on m.id = s.mensagem_id where m.conversa_id = ? order by s.mensagem_id", MAPA, conversaId);
    }

    private List<StatusMensagem> buscar(List<Long> ids) {
        return jdbc.query(SELECT + " where s.mensagem_id = any(?)", MAPA, (Object) ids.toArray(Long[]::new));
    }

    /** Pendencias abertas que estao "com" a pessoa: mencionada, autora da mensagem ou responsavel. */
    @Transactional(readOnly = true)
    public List<Pendencia> comigo(UsuarioAutenticado user) {
        return jdbc.query("""
                select s.mensagem_id, m.conversa_id, m.texto, s.status, s.alterado_em, a.nome as autor, r.nome as responsavel,
                  coalesce(c.nome, (select u.nome from chat_participante p2 join usuario_acesso u on u.id = p2.usuario_id
                                    where p2.conversa_id = c.id and p2.usuario_id <> ? limit 1)) as conversa,
                  exists (select 1 from chat_mencao x where x.mensagem_id = m.id and x.usuario_id = ?) as mencionado
                from chat_mensagem_status s
                join chat_mensagem m on m.id = s.mensagem_id and m.apagada_em is null
                join chat_conversa c on c.id = m.conversa_id
                join chat_participante p on p.conversa_id = m.conversa_id and p.usuario_id = ?
                join usuario_acesso a on a.id = m.autor_id
                left join usuario_acesso r on r.id = s.responsavel_id
                where s.status <> 'RESOLVIDO'
                  and (s.responsavel_id = ? or m.autor_id = ? or exists (select 1 from chat_mencao x where x.mensagem_id = m.id and x.usuario_id = ?))
                order by case s.status when 'PENDENTE' then 0 else 1 end, s.alterado_em desc limit 100""",
                (r, i) -> new Pendencia(r.getLong("mensagem_id"), r.getLong("conversa_id"), r.getString("conversa"), r.getString("autor"),
                        r.getString("texto"), r.getString("status"), r.getString("responsavel"), r.getTimestamp("alterado_em").toInstant(), r.getBoolean("mencionado")),
                user.id(), user.id(), user.id(), user.id(), user.id(), user.id());
    }

    private static final String SELECT = """
            select s.mensagem_id, s.status, s.responsavel_id, r.nome as responsavel, a.nome as alterado_por, s.alterado_em
            from chat_mensagem_status s left join usuario_acesso r on r.id = s.responsavel_id join usuario_acesso a on a.id = s.alterado_por""";
    private static final org.springframework.jdbc.core.RowMapper<StatusMensagem> MAPA = (r, i) -> new StatusMensagem(r.getLong("mensagem_id"),
            r.getString("status"), (Long) r.getObject("responsavel_id", Long.class), r.getString("responsavel"), r.getString("alterado_por"),
            r.getTimestamp("alterado_em").toInstant());
}
