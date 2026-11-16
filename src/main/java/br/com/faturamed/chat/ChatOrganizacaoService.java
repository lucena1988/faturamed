package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organizacao pessoal no chat: lembretes em mensagens, mensagens salvas e respostas rapidas. */
@Service
public class ChatOrganizacaoService {
    private static final Logger log = LoggerFactory.getLogger(ChatOrganizacaoService.class);
    public record Lembrete(long id, long mensagemId, long conversaId, String conversa, String autorNome, String texto, Instant quando) {}
    public record Salva(long mensagemId, long conversaId, String conversa, String autorNome, String texto, Instant criadoEm, Instant salvoEm) {}
    public record RespostaRapida(long id, String atalho, String texto) {}

    private final JdbcTemplate jdbc;
    private final ChatService chat;
    public ChatOrganizacaoService(JdbcTemplate jdbc, ChatService chat) { this.jdbc = jdbc; this.chat = chat; }

    private void participa(long mensagemId, UsuarioAutenticado user) {
        Integer n = jdbc.queryForObject("""
                select count(*) from chat_mensagem m join chat_participante p on p.conversa_id = m.conversa_id and p.usuario_id = ?
                where m.id = ? and m.apagada_em is null""", Integer.class, user.id(), mensagemId);
        if (n == null || n == 0) throw new AccessDeniedException("Mensagem fora das suas conversas");
    }

    /** Nome da conversa do ponto de vista da pessoa (grupo: nome; direta: o outro participante). */
    private static final String NOME_CONVERSA = """
            coalesce(c.nome, (select u.nome from chat_participante p2 join usuario_acesso u on u.id = p2.usuario_id
                              where p2.conversa_id = c.id and p2.usuario_id <> ? limit 1))""";

    // ------------------------------------------------------------------ lembretes
    @Transactional
    public Lembrete lembrar(long mensagemId, Instant quando, UsuarioAutenticado user) {
        participa(mensagemId, user);
        if (quando == null || quando.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES))) throw new IllegalArgumentException("Escolha um horário no futuro");
        if (quando.isAfter(Instant.now().plus(365, ChronoUnit.DAYS))) throw new IllegalArgumentException("Lembrete para no máximo um ano");
        Long id = jdbc.queryForObject("insert into chat_lembrete(usuario_id, mensagem_id, quando) values (?,?,?) returning id",
                Long.class, user.id(), mensagemId, java.sql.Timestamp.from(quando));
        return lembretes(user).stream().filter(l -> l.id() == id).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Lembrete> lembretes(UsuarioAutenticado user) {
        return jdbc.query("""
                select l.id, l.mensagem_id, m.conversa_id, %s as conversa, a.nome as autor, m.texto, l.quando
                from chat_lembrete l join chat_mensagem m on m.id = l.mensagem_id join chat_conversa c on c.id = m.conversa_id
                join usuario_acesso a on a.id = m.autor_id
                where l.usuario_id = ? and l.entregue_em is null order by l.quando""".formatted(NOME_CONVERSA),
                (r, i) -> new Lembrete(r.getLong("id"), r.getLong("mensagem_id"), r.getLong("conversa_id"), r.getString("conversa"),
                        r.getString("autor"), r.getString("texto"), r.getTimestamp("quando").toInstant()), user.id(), user.id());
    }

    @Transactional
    public void cancelarLembrete(long id, UsuarioAutenticado user) {
        jdbc.update("delete from chat_lembrete where id = ? and usuario_id = ? and entregue_em is null", id, user.id());
    }

    /** A cada minuto: entrega os lembretes vencidos pelo Robo FaturaMed (toca o aviso como mensagem nova). */
    @Scheduled(fixedDelayString = "${faturamed.chat.lembretes-ms:60000}", initialDelay = 30000)
    @Transactional
    public void entregarLembretes() {
        var vencidos = jdbc.queryForList("""
                select l.id, l.usuario_id, m.texto, a.nome as autor, m.conversa_id, %s as conversa
                from chat_lembrete l join chat_mensagem m on m.id = l.mensagem_id join chat_conversa c on c.id = m.conversa_id
                join usuario_acesso a on a.id = m.autor_id
                where l.entregue_em is null and l.quando <= now() order by l.quando limit 50
                for update of l skip locked""".formatted(NOME_CONVERSA.replace("p2.usuario_id <> ?", "p2.usuario_id <> l.usuario_id")));
        for (var v : vencidos) {
            String texto = Objects.toString(v.get("texto"), "");
            if (texto.length() > 300) texto = texto.substring(0, 300) + "…";
            try {
                chat.avisarSistema(((Number) v.get("usuario_id")).longValue(),
                        "⏰ Lembrete — " + v.get("autor") + " em " + v.get("conversa") + ": \"" + texto + "\"");
            } catch (RuntimeException e) { log.warn("Lembrete {} nao entregue: {}", v.get("id"), e.getMessage()); }
            jdbc.update("update chat_lembrete set entregue_em = now() where id = ?", v.get("id"));
        }
    }

    // ------------------------------------------------------------------ mensagens salvas
    @Transactional
    public boolean salvar(long mensagemId, boolean salvar, UsuarioAutenticado user) {
        participa(mensagemId, user);
        if (salvar) jdbc.update("insert into chat_mensagem_salva(usuario_id, mensagem_id) values (?,?) on conflict do nothing", user.id(), mensagemId);
        else jdbc.update("delete from chat_mensagem_salva where usuario_id = ? and mensagem_id = ?", user.id(), mensagemId);
        return salvar;
    }

    @Transactional(readOnly = true)
    public List<Salva> salvas(UsuarioAutenticado user) {
        return jdbc.query("""
                select s.mensagem_id, m.conversa_id, %s as conversa, a.nome as autor, m.texto, m.criado_em, s.salvo_em
                from chat_mensagem_salva s join chat_mensagem m on m.id = s.mensagem_id and m.apagada_em is null
                join chat_conversa c on c.id = m.conversa_id join usuario_acesso a on a.id = m.autor_id
                join chat_participante p on p.conversa_id = m.conversa_id and p.usuario_id = s.usuario_id
                where s.usuario_id = ? order by s.salvo_em desc limit 200""".formatted(NOME_CONVERSA),
                (r, i) -> new Salva(r.getLong("mensagem_id"), r.getLong("conversa_id"), r.getString("conversa"), r.getString("autor"),
                        r.getString("texto"), r.getTimestamp("criado_em").toInstant(), r.getTimestamp("salvo_em").toInstant()), user.id(), user.id());
    }

    // ------------------------------------------------------------------ respostas rapidas
    @Transactional(readOnly = true)
    public List<RespostaRapida> respostas(UsuarioAutenticado user) {
        return jdbc.query("select id, atalho, texto from chat_resposta_rapida where usuario_id = ? order by atalho",
                (r, i) -> new RespostaRapida(r.getLong("id"), r.getString("atalho"), r.getString("texto")), user.id());
    }

    @Transactional
    public RespostaRapida salvarResposta(String atalho, String texto, UsuarioAutenticado user) {
        String a = Objects.toString(atalho, "").trim().toLowerCase(Locale.ROOT).replaceFirst("^/", "").replaceAll("[^a-z0-9\\-_]", "");
        String t = Objects.toString(texto, "").strip();
        if (a.isEmpty() || a.length() > 30) throw new IllegalArgumentException("Atalho de 1 a 30 letras ou números");
        if (t.isEmpty() || t.length() > 1000) throw new IllegalArgumentException("Texto de 1 a 1000 caracteres");
        Long id = jdbc.queryForObject("""
                insert into chat_resposta_rapida(usuario_id, atalho, texto) values (?,?,?)
                on conflict (usuario_id, atalho) do update set texto = excluded.texto returning id""", Long.class, user.id(), a, t);
        return new RespostaRapida(id, a, t);
    }

    @Transactional
    public void apagarResposta(long id, UsuarioAutenticado user) {
        jdbc.update("delete from chat_resposta_rapida where id = ? and usuario_id = ?", id, user.id());
    }
}
