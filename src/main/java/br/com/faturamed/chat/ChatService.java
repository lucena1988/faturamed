package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chat interno da equipe (ADMIN, DEV e OPERADOR): conversas 1:1 e em grupo, historico, nao lidas, anexos,
 * respostas, reacoes, edicao, mensagem fixada, busca, status, grupos oficiais, retencao (LGPD) e avisos do sistema.
 */
@Service
public class ChatService {
    static final int LIMITE_TEXTO = 4000;
    static final int LIMITE_NOME_GRUPO = 80;
    static final int PAGINA = 50;
    static final Duration PRAZO_EDICAO = Duration.ofMinutes(15);
    static final String EMAIL_ROBO = "robo@faturamed.sistema";
    static final Set<String> REACOES = Set.of("👍", "✅", "👀", "❤️", "😂", "🙏", "🎉", "😮");
    static final Set<String> STATUS = Set.of("DISPONIVEL", "OCUPADO", "REUNIAO", "AUSENTE");
    private static final String PERFIS_INTERNOS = "('ADMIN','DEV','OPERADOR')";
    private static final java.security.SecureRandom SORTEIO = new java.security.SecureRandom();
    private static final String FOTO = "(select extract(epoch from f.atualizado_em)::bigint from usuario_foto f where f.usuario_id=%s)";
    private static final String STATUS_SQL = "(select s.status from chat_status s where s.usuario_id=%1$s) status, (select s.recado from chat_status s where s.usuario_id=%1$s) recado";
    private static final String SELECT_MENSAGEM = """
        select m.*, u.nome, u.email, %s foto,
               r.autor_id resp_autor, ru.nome resp_nome, r.texto resp_texto, r.apagada_em resp_apagada,
               (select count(*) from chat_anexo ra where ra.mensagem_id=r.id) resp_anexos
        from chat_mensagem m join usuario_acesso u on u.id=m.autor_id
        left join chat_mensagem r on r.id=m.responde_a
        left join usuario_acesso ru on ru.id=r.autor_id
        """.formatted(FOTO.formatted("m.autor_id"));

    /** foto: versao da foto (null sem foto), imagem em /api/usuarios/{id}/foto?v={foto}; status/recado escolhidos pela pessoa. */
    public record Contato(long id, String nome, String perfil, boolean online, Long foto, String status, String recado) {}
    public record Fixada(long id, String autorNome, String texto) {}
    /** Em conversa DIRETA, contato e a outra pessoa; em GRUPO, contato e null e nome traz o nome do grupo. sistema: conversa com o Robo. */
    public record Conversa(long id, String tipo, String nome, Contato contato, int participantes, boolean souCriador,
                           String ultimaMensagem, Long ultimoAutorId, String ultimoAutorNome,
                           Instant ultimaMensagemEm, long naoLidas, long leituraContato, boolean silenciado,
                           boolean oficial, boolean sistema, Fixada fixada, long mencoesNaoLidas) {}
    public record Silencio(boolean silenciado) {}
    public record UltimaNaoLida(long conversaId, long mensagemId, String autorNome, String texto) {}
    /** total: todas as nao lidas; comSom: so das conversas nao silenciadas; ultima: para a notificacao na area de trabalho. */
    public record NaoLidas(long total, long comSom, UltimaNaoLida ultima, long mencoes) {}
    public record Anexo(long id, String nome, String tipo, int tamanho) {}
    public record Resposta(long id, String autorNome, String texto) {}
    public record Reacao(String emoji, List<Long> usuarios, List<String> nomes) {}
    public record Mensagem(long id, long conversaId, long autorId, String autorNome, String texto, Instant criadoEm,
                           List<Anexo> anexos, Long autorFoto, Resposta resposta, List<Reacao> reacoes,
                           boolean editada, boolean apagada, boolean sistema, List<Long> mencionados) {}
    public record ArquivoBaixado(String nome, String tipo, byte[] conteudo) {}
    public record Pagina(List<Mensagem> mensagens, boolean temMais, long leituraContato) {}
    public record NovaConversa(long usuarioId) {}
    public record NovoGrupo(String nome, List<Long> usuarioIds, Boolean oficial) {
        public NovoGrupo(String nome, List<Long> usuarioIds) { this(nome, usuarioIds, false); }
    }
    public record NovoParticipante(long usuarioId) {}
    public record Participante(long id, String nome, String perfil, boolean online, boolean criador, Long foto) {}
    public record NovaMensagem(String texto, Long respondeA) {}
    public record Edicao(String texto) {}
    public record NovaReacao(String emoji) {}
    public record NovaFixada(Long mensagemId) {}
    public record Leitura(long ateId) {}
    public record Chamada(long conversaId, String sala, String dominio, long deId, String deNome, String conversaNome) {}
    public record ResultadoBusca(long conversaId, String conversaNome, long mensagemId, String autorNome, String texto, Instant criadoEm) {}
    public record MeuStatus(String status, String recado) {}
    public record Configuracao(Integer retencaoMeses) {}

    private final JdbcTemplate jdbc;
    private final ChatEventos eventos;

    public ChatService(JdbcTemplate jdbc, ChatEventos eventos) {
        this.jdbc = jdbc;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------ contatos e conversas

    @Transactional(readOnly = true)
    public List<Contato> contatos(UsuarioAutenticado user) {
        return jdbc.query("select id,nome,perfil," + FOTO.formatted("usuario_acesso.id") + " foto," + STATUS_SQL.formatted("usuario_acesso.id")
                + " from usuario_acesso where ativo=true and perfil in " + PERFIS_INTERNOS + " and id<>? order by lower(nome)",
                (r, n) -> contato(r, "id"), user.id());
    }

    @Transactional(readOnly = true)
    public List<Conversa> conversas(UsuarioAutenticado user) {
        long robo = idRobo();
        return jdbc.query("""
            select c.id, c.tipo, c.nome nome_grupo, c.criado_por, c.oficial, p.silenciado, c.ultima_mensagem_em, c.criado_em,
                   u.id contato_id, u.nome, u.perfil, %s foto, %s,
                   (select count(*) from chat_participante t where t.conversa_id=c.id) participantes,
                   (select coalesce(min(t.ultima_leitura_id),0) from chat_participante t where t.conversa_id=c.id and t.usuario_id<>p.usuario_id) leitura_contato,
                   m.texto ultima_texto, m.autor_id ultimo_autor, ma.nome ultimo_autor_nome,
                   (select count(*) from chat_mensagem x where x.conversa_id=c.id and x.id>p.ultima_leitura_id and x.autor_id<>? and x.apagada_em is null) nao_lidas,
                   (select count(*) from chat_mencao mc join chat_mensagem x on x.id=mc.mensagem_id where mc.usuario_id=p.usuario_id and x.conversa_id=c.id and x.id>p.ultima_leitura_id and x.apagada_em is null) mencoes_nao_lidas,
                   fx.id fixada_id, fx.texto fixada_texto, fxu.nome fixada_autor
            from chat_participante p
            join chat_conversa c on c.id=p.conversa_id
            left join lateral (select o.usuario_id from chat_participante o
                               where c.tipo='DIRETA' and o.conversa_id=c.id and o.usuario_id<>p.usuario_id limit 1) o on true
            left join usuario_acesso u on u.id=o.usuario_id
            left join lateral (select case when cm.apagada_em is not null then '🚫 Mensagem apagada'
                                           when cm.texto='' then coalesce((select case when a.tipo like 'audio/%%' then '🎤 Mensagem de voz' else '📎 ' || a.nome end
                                                                          from chat_anexo a where a.mensagem_id=cm.id order by a.id limit 1),'📎 Anexo')
                                           else cm.texto end texto, cm.autor_id
                               from chat_mensagem cm where cm.conversa_id=c.id order by cm.id desc limit 1) m on true
            left join usuario_acesso ma on ma.id=m.autor_id
            left join chat_mensagem fx on fx.id=c.fixada_mensagem_id and fx.apagada_em is null
            left join usuario_acesso fxu on fxu.id=fx.autor_id
            where p.usuario_id=?
            order by coalesce(c.ultima_mensagem_em, c.criado_em) desc, c.id desc
            """.formatted(FOTO.formatted("u.id"), STATUS_SQL.formatted("u.id")), (r, n) -> {
                boolean grupo = "GRUPO".equals(r.getString("tipo"));
                Contato contato = grupo || r.getObject("contato_id") == null ? null : contato(r, "contato_id");
                Long criador = (Long) r.getObject("criado_por");
                Fixada fixada = r.getObject("fixada_id") == null ? null
                        : new Fixada(r.getLong("fixada_id"), r.getString("fixada_autor"), resumo(r.getString("fixada_texto")));
                return new Conversa(r.getLong("id"), r.getString("tipo"), grupo ? r.getString("nome_grupo") : contato == null ? "" : contato.nome(),
                    contato, r.getInt("participantes"), criador != null && criador == user.id(),
                    r.getString("ultima_texto"), (Long) r.getObject("ultimo_autor"), r.getString("ultimo_autor_nome"),
                    instante(r, "ultima_mensagem_em"), r.getLong("nao_lidas"), r.getLong("leitura_contato"), r.getBoolean("silenciado"),
                    r.getBoolean("oficial"), contato != null && contato.id() == robo, fixada, r.getLong("mencoes_nao_lidas"));
            }, user.id(), user.id());
    }

    private Conversa conversa(long id, UsuarioAutenticado user) {
        return conversas(user).stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public long naoLidas(UsuarioAutenticado user) {
        return resumoNaoLidas(user).total();
    }

    @Transactional(readOnly = true)
    public NaoLidas resumoNaoLidas(UsuarioAutenticado user) {
        var totais = jdbc.queryForObject("""
            select count(*) total, count(*) filter (where not p.silenciado) com_som
            from chat_participante p join chat_mensagem m on m.conversa_id=p.conversa_id
            where p.usuario_id=? and m.id>p.ultima_leitura_id and m.autor_id<>? and m.apagada_em is null
            """, (r, n) -> new long[]{r.getLong("total"), r.getLong("com_som")}, user.id(), user.id());
        var ultima = jdbc.query("""
            select m.id, m.conversa_id, u.nome, m.texto, (select count(*) from chat_anexo a where a.mensagem_id=m.id) anexos
            from chat_participante p join chat_mensagem m on m.conversa_id=p.conversa_id join usuario_acesso u on u.id=m.autor_id
            where p.usuario_id=? and not p.silenciado and m.id>p.ultima_leitura_id and m.autor_id<>? and m.apagada_em is null
            order by m.id desc limit 1
            """, (r, n) -> new UltimaNaoLida(r.getLong("conversa_id"), r.getLong("id"), r.getString("nome"),
                r.getString("texto").isEmpty() ? (r.getInt("anexos") > 0 ? "📎 enviou um arquivo" : "") : resumo(r.getString("texto"))),
            user.id(), user.id()).stream().findFirst().orElse(null);
        long mencoes = jdbc.queryForObject("""
            select count(*) from chat_mencao mc join chat_mensagem m on m.id=mc.mensagem_id
            join chat_participante p on p.conversa_id=m.conversa_id and p.usuario_id=mc.usuario_id
            where mc.usuario_id=? and m.id>p.ultima_leitura_id and m.apagada_em is null
            """, Long.class, user.id());
        return new NaoLidas(totais[0], totais[1], ultima, mencoes);
    }

    @Transactional
    public void silenciar(long conversaId, boolean silenciado, UsuarioAutenticado user) {
        participantes(conversaId, user);
        jdbc.update("update chat_participante set silenciado=? where conversa_id=? and usuario_id=?", silenciado, conversaId, user.id());
    }

    @Transactional
    public Conversa abrirDireta(long outroId, UsuarioAutenticado user) {
        if (outroId == user.id()) throw new IllegalArgumentException("Escolha outra pessoa para conversar");
        exigirInterno(outroId);
        return conversa(garantirDireta(user.id(), outroId), user);
    }

    private long garantirDireta(long a, long b) {
        String chave = Math.min(a, b) + ":" + Math.max(a, b);
        jdbc.update("insert into chat_conversa(tipo,chave_direta) values ('DIRETA',?) on conflict (chave_direta) do nothing", chave);
        long id = jdbc.queryForObject("select id from chat_conversa where chave_direta=?", Long.class, chave);
        jdbc.update("insert into chat_participante(conversa_id,usuario_id) values (?,?),(?,?) on conflict do nothing", id, a, id, b);
        return id;
    }

    // ------------------------------------------------------------------ grupos

    @Transactional
    public Conversa criarGrupo(NovoGrupo dados, UsuarioAutenticado user) {
        String nome = dados.nome() == null ? "" : dados.nome().strip();
        if (nome.isEmpty()) throw new IllegalArgumentException("Dê um nome ao grupo");
        if (nome.length() > LIMITE_NOME_GRUPO) throw new IllegalArgumentException("Nome do grupo acima de " + LIMITE_NOME_GRUPO + " caracteres");
        boolean oficial = Boolean.TRUE.equals(dados.oficial());
        if (oficial && !admin(user)) throw new AccessDeniedException("Só o ADMIN cria grupos oficiais");
        Set<Long> membros = new LinkedHashSet<>(dados.usuarioIds() == null ? List.of() : dados.usuarioIds());
        membros.remove(user.id());
        if (membros.isEmpty()) throw new IllegalArgumentException("Escolha ao menos uma pessoa para o grupo");
        membros.forEach(this::exigirInterno);
        long id = jdbc.queryForObject("insert into chat_conversa(tipo,nome,criado_por,oficial) values ('GRUPO',?,?,?) returning id",
                Long.class, nome, user.id(), oficial);
        jdbc.update("insert into chat_participante(conversa_id,usuario_id) values (?,?)", id, user.id());
        membros.forEach(m -> jdbc.update("insert into chat_participante(conversa_id,usuario_id) values (?,?)", id, m));
        List<Long> todos = new ArrayList<>(membros);
        depoisDoCommit(() -> eventos.enviar(todos, "conversa", Map.of("conversaId", id)));
        return conversa(id, user);
    }

    @Transactional(readOnly = true)
    public List<Participante> participantesDoGrupo(long conversaId, UsuarioAutenticado user) {
        participantes(conversaId, user);
        return jdbc.query("""
            select u.id, u.nome, u.perfil, coalesce(c.criado_por=u.id,false) criador, %s foto
            from chat_participante p join usuario_acesso u on u.id=p.usuario_id join chat_conversa c on c.id=p.conversa_id
            where p.conversa_id=? order by coalesce(c.criado_por=u.id,false) desc, lower(u.nome)
            """.formatted(FOTO.formatted("u.id")), (r, n) -> new Participante(r.getLong("id"), r.getString("nome"), r.getString("perfil"),
                eventos.online(r.getLong("id")), r.getBoolean("criador"), (Long) r.getObject("foto")), conversaId);
    }

    /** Grupo comum: qualquer participante inclui colegas. Grupo oficial: so o ADMIN. */
    @Transactional
    public void adicionarParticipante(long conversaId, long usuarioId, UsuarioAutenticado user) {
        List<Long> atuais = participantes(conversaId, user);
        exigirGrupo(conversaId);
        if (oficial(conversaId) && !admin(user)) throw new AccessDeniedException("Neste grupo oficial só o ADMIN muda os participantes");
        exigirInterno(usuarioId);
        if (atuais.contains(usuarioId)) return;
        long ultima = jdbc.queryForObject("select coalesce(max(id),0) from chat_mensagem where conversa_id=?", Long.class, conversaId);
        // quem entra nao herda o historico como nao lido
        jdbc.update("insert into chat_participante(conversa_id,usuario_id,ultima_leitura_id) values (?,?,?)", conversaId, usuarioId, ultima);
        List<Long> avisar = new ArrayList<>(atuais); avisar.add(usuarioId);
        depoisDoCommit(() -> eventos.enviar(avisar, "conversa", Map.of("conversaId", conversaId)));
    }

    /** Grupo comum: cada um sai quando quiser e so quem criou remove outros. Grupo oficial: so o ADMIN remove (e ninguem sai sozinho). */
    @Transactional
    public void removerParticipante(long conversaId, long usuarioId, UsuarioAutenticado user) {
        List<Long> atuais = participantes(conversaId, user);
        Long criador = exigirGrupo(conversaId);
        if (oficial(conversaId)) {
            if (!admin(user)) throw new AccessDeniedException("Neste grupo oficial só o ADMIN muda os participantes");
        } else if (usuarioId != user.id() && (criador == null || criador != user.id())) {
            throw new AccessDeniedException("Só quem criou o grupo remove participantes");
        }
        if (!atuais.contains(usuarioId)) throw new RecursoNaoEncontradoException("Participante nao encontrado");
        jdbc.update("delete from chat_participante where conversa_id=? and usuario_id=?", conversaId, usuarioId);
        if (atuais.size() == 1) jdbc.update("delete from chat_conversa where id=?", conversaId);
        depoisDoCommit(() -> eventos.enviar(atuais, "conversa", Map.of("conversaId", conversaId)));
    }

    // ------------------------------------------------------------------ mensagens

    /** Pagina de mensagens; antesDe pagina para tras. Para abrir num resultado de busca, use antesDe = id + 1. */
    @Transactional(readOnly = true)
    public Pagina mensagens(long conversaId, Long antesDe, UsuarioAutenticado user) {
        participantes(conversaId, user);
        List<Mensagem> lista = new ArrayList<>(jdbc.query(SELECT_MENSAGEM
                + " where m.conversa_id=? and (?::bigint is null or m.id<?) order by m.id desc limit ?",
                (r, n) -> mensagem(r), conversaId, antesDe, antesDe, PAGINA + 1));
        boolean temMais = lista.size() > PAGINA;
        if (temMais) lista.removeLast();
        Collections.reverse(lista);
        lista = comDetalhes(lista);
        Long leitura = jdbc.queryForObject("select coalesce(min(ultima_leitura_id),0) from chat_participante where conversa_id=? and usuario_id<>?",
                Long.class, conversaId, user.id());
        return new Pagina(lista, temMais, leitura == null ? 0 : leitura);
    }

    @Transactional
    public Mensagem enviar(long conversaId, String texto, UsuarioAutenticado user) {
        return enviar(conversaId, texto, List.of(), null, user);
    }

    @Transactional
    public Mensagem enviar(long conversaId, String texto, List<ChatAnexos.Arquivo> anexos, UsuarioAutenticado user) {
        return enviar(conversaId, texto, anexos, null, user);
    }

    @Transactional
    public Mensagem enviar(long conversaId, String texto, List<ChatAnexos.Arquivo> anexos, Long respondeA, UsuarioAutenticado user) {
        List<Long> participantes = participantes(conversaId, user);
        if (conversaDoRobo(conversaId)) throw new IllegalArgumentException("A conversa com o Robô FaturaMed só recebe avisos do sistema");
        return gravar(conversaId, user.id(), texto, anexos, respondeA, participantes);
    }

    private Mensagem gravar(long conversaId, long autorId, String texto, List<ChatAnexos.Arquivo> anexos, Long respondeA, List<Long> participantes) {
        String limpo = texto == null ? "" : texto.strip();
        if (limpo.isEmpty() && anexos.isEmpty()) throw new IllegalArgumentException("Escreva uma mensagem");
        if (limpo.length() > LIMITE_TEXTO) throw new IllegalArgumentException("Mensagem acima de " + LIMITE_TEXTO + " caracteres");
        if (respondeA != null) {
            Integer mesma = jdbc.queryForObject("select count(*) from chat_mensagem where id=? and conversa_id=?", Integer.class, respondeA, conversaId);
            if (mesma == null || mesma == 0) throw new IllegalArgumentException("A mensagem respondida não é desta conversa");
        }
        long id = jdbc.queryForObject("insert into chat_mensagem(conversa_id,autor_id,texto,responde_a) values (?,?,?,?) returning id",
                Long.class, conversaId, autorId, limpo, respondeA);
        for (ChatAnexos.Arquivo a : anexos)
            jdbc.update("insert into chat_anexo(mensagem_id,nome,tipo,tamanho,conteudo) values (?,?,?,?,?)",
                    id, a.nome(), a.tipo(), a.conteudo().length, a.conteudo());
        jdbc.update("update chat_conversa set ultima_mensagem_em=now() where id=?", conversaId);
        jdbc.update("update chat_participante set ultima_leitura_id=? where conversa_id=? and usuario_id=?", id, conversaId, autorId);
        registrarMencoes(id, limpo, autorId, participantes);
        Mensagem msg = buscarMensagem(id);
        depoisDoCommit(() -> eventos.enviar(participantes, "mensagem", msg));
        return msg;
    }

    /** Edita a propria mensagem nos primeiros 15 minutos. */
    @Transactional
    public Mensagem editar(long mensagemId, String texto, UsuarioAutenticado user) {
        var dono = propria(mensagemId, user);
        String limpo = texto == null ? "" : texto.strip();
        if (limpo.isEmpty()) throw new IllegalArgumentException("Escreva o novo texto ou apague a mensagem");
        if (limpo.length() > LIMITE_TEXTO) throw new IllegalArgumentException("Mensagem acima de " + LIMITE_TEXTO + " caracteres");
        jdbc.update("update chat_mensagem set texto=?, editada_em=now() where id=?", limpo, mensagemId);
        jdbc.update("delete from chat_mencao where mensagem_id=?", mensagemId);
        registrarMencoes(mensagemId, limpo, user.id(),
                jdbc.queryForList("select usuario_id from chat_participante where conversa_id=?", Long.class, dono));
        return alterada(mensagemId, dono);
    }

    /** Apaga a propria mensagem nos primeiros 15 minutos: fica "Mensagem apagada" e os anexos sao removidos. */
    @Transactional
    public Mensagem apagar(long mensagemId, UsuarioAutenticado user) {
        long conversaId = propria(mensagemId, user);
        jdbc.update("update chat_mensagem set texto='', apagada_em=now() where id=?", mensagemId);
        jdbc.update("delete from chat_anexo where mensagem_id=?", mensagemId);
        jdbc.update("delete from chat_reacao where mensagem_id=?", mensagemId);
        jdbc.update("update chat_conversa set fixada_mensagem_id=null where fixada_mensagem_id=?", mensagemId);
        return alterada(mensagemId, conversaId);
    }

    /** Liga/desliga uma reacao da pessoa na mensagem. */
    @Transactional
    public Mensagem reagir(long mensagemId, String emoji, UsuarioAutenticado user) {
        if (emoji == null || !REACOES.contains(emoji)) throw new IllegalArgumentException("Reação não permitida");
        long conversaId = conversaDaMensagem(mensagemId, user);
        int removidas = jdbc.update("delete from chat_reacao where mensagem_id=? and usuario_id=? and emoji=?", mensagemId, user.id(), emoji);
        if (removidas == 0) jdbc.update("insert into chat_reacao(mensagem_id,usuario_id,emoji) values (?,?,?)", mensagemId, user.id(), emoji);
        return alterada(mensagemId, conversaId);
    }

    /** Fixa (ou solta, com null) uma mensagem no topo da conversa. Em grupo oficial, so o ADMIN. */
    @Transactional
    public Conversa fixar(long conversaId, Long mensagemId, UsuarioAutenticado user) {
        List<Long> participantes = participantes(conversaId, user);
        if (oficial(conversaId) && !admin(user)) throw new AccessDeniedException("Neste grupo oficial só o ADMIN fixa mensagens");
        if (mensagemId != null) {
            Integer ok = jdbc.queryForObject("select count(*) from chat_mensagem where id=? and conversa_id=? and apagada_em is null",
                    Integer.class, mensagemId, conversaId);
            if (ok == null || ok == 0) throw new IllegalArgumentException("Mensagem não encontrada nesta conversa");
        }
        jdbc.update("update chat_conversa set fixada_mensagem_id=? where id=?", mensagemId, conversaId);
        depoisDoCommit(() -> eventos.enviar(participantes, "conversa", Map.of("conversaId", conversaId)));
        return conversa(conversaId, user);
    }

    /** Busca texto nas conversas da pessoa (no minimo 2 letras). */
    @Transactional(readOnly = true)
    public List<ResultadoBusca> buscar(String termo, UsuarioAutenticado user) {
        String t = termo == null ? "" : termo.strip();
        if (t.length() < 2) return List.of();
        String padrao = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
            select m.id, m.conversa_id, m.texto, m.criado_em, u.nome autor,
                   coalesce(c.nome, (select ou.nome from chat_participante o join usuario_acesso ou on ou.id=o.usuario_id
                                     where o.conversa_id=c.id and o.usuario_id<>? limit 1)) conversa_nome
            from chat_participante p
            join chat_conversa c on c.id=p.conversa_id
            join chat_mensagem m on m.conversa_id=c.id and m.apagada_em is null
            join usuario_acesso u on u.id=m.autor_id
            where p.usuario_id=? and m.texto ilike ? escape '\\'
            order by m.id desc limit 50
            """, (r, n) -> new ResultadoBusca(r.getLong("conversa_id"), r.getString("conversa_nome"), r.getLong("id"),
                r.getString("autor"), resumo(r.getString("texto")), r.getTimestamp("criado_em").toInstant()),
            user.id(), user.id(), padrao);
    }

    /** Inicia uma videochamada: sala com nome impossivel de adivinhar, registro no historico e "toque" para os demais. */
    @Transactional
    public Chamada iniciarChamada(long conversaId, String dominio, UsuarioAutenticado user) {
        if (dominio.isEmpty()) throw new IllegalArgumentException("Videochamada ainda nao configurada no servidor");
        List<Long> participantes = participantes(conversaId, user);
        byte[] bytes = new byte[12];
        SORTEIO.nextBytes(bytes);
        String sala = "faturamed-" + HexFormat.of().formatHex(bytes);
        String nome = jdbc.queryForObject("""
            select coalesce(c.nome, u.nome) from chat_conversa c
            left join chat_participante o on c.tipo='DIRETA' and o.conversa_id=c.id and o.usuario_id<>?
            left join usuario_acesso u on u.id=o.usuario_id where c.id=? limit 1
            """, String.class, user.id(), conversaId);
        enviar(conversaId, "📹 Iniciou uma chamada de vídeo", user);
        var chamada = new Chamada(conversaId, sala, dominio, user.id(), user.nome(), nome);
        List<Long> outros = participantes.stream().filter(id -> id != user.id()).toList();
        depoisDoCommit(() -> eventos.enviar(outros, "chamada", chamada));
        return chamada;
    }

    @Transactional
    public void marcarLida(long conversaId, long ateId, UsuarioAutenticado user) {
        List<Long> participantes = participantes(conversaId, user);
        long ultima = jdbc.queryForObject("select coalesce(max(id),0) from chat_mensagem where conversa_id=?", Long.class, conversaId);
        long ate = Math.min(ateId, ultima);
        int alterou = jdbc.update("update chat_participante set ultima_leitura_id=? where conversa_id=? and usuario_id=? and ultima_leitura_id<?",
                ate, conversaId, user.id(), ate);
        if (alterou > 0) depoisDoCommit(() -> eventos.enviar(participantes, "leitura",
                Map.of("conversaId", conversaId, "usuarioId", user.id(), "ateId", ate)));
    }

    @Transactional(readOnly = true)
    public void digitando(long conversaId, UsuarioAutenticado user) {
        List<Long> outros = participantes(conversaId, user).stream().filter(id -> id != user.id()).toList();
        eventos.enviar(outros, "digitando", Map.of("conversaId", conversaId, "usuarioId", user.id(), "nome", user.nome()));
    }

    /** Arquivo anexado a uma mensagem; so participantes da conversa conseguem baixar. */
    @Transactional(readOnly = true)
    public ArquivoBaixado baixarAnexo(long anexoId, UsuarioAutenticado user) {
        var linhas = jdbc.query("""
            select a.nome, a.tipo, a.conteudo from chat_anexo a
            join chat_mensagem m on m.id=a.mensagem_id
            join chat_participante p on p.conversa_id=m.conversa_id and p.usuario_id=?
            where a.id=?
            """, (r, n) -> new ArquivoBaixado(r.getString("nome"), r.getString("tipo"), r.getBytes("conteudo")), user.id(), anexoId);
        if (linhas.isEmpty()) throw new RecursoNaoEncontradoException("Anexo nao encontrado");
        return linhas.getFirst();
    }

    /** Todas as mensagens da conversa (para exportar em PDF), em ordem. */
    @Transactional(readOnly = true)
    public List<Mensagem> historicoCompleto(long conversaId, UsuarioAutenticado user) {
        participantes(conversaId, user);
        return comDetalhes(jdbc.query(SELECT_MENSAGEM + " where m.conversa_id=? order by m.id", (r, n) -> mensagem(r), conversaId));
    }

    public String nomeDaConversa(long conversaId, UsuarioAutenticado user) {
        return conversa(conversaId, user).nome();
    }

    // ------------------------------------------------------------------ status

    @Transactional(readOnly = true)
    public MeuStatus meuStatus(UsuarioAutenticado user) {
        return jdbc.query("select status, recado from chat_status where usuario_id=?",
                (r, n) -> new MeuStatus(r.getString("status"), r.getString("recado")), user.id())
                .stream().findFirst().orElse(new MeuStatus("DISPONIVEL", null));
    }

    @Transactional
    public MeuStatus definirStatus(MeuStatus dados, UsuarioAutenticado user) {
        String status = dados.status() == null ? "DISPONIVEL" : dados.status();
        if (!STATUS.contains(status)) throw new IllegalArgumentException("Status inválido");
        String recado = dados.recado() == null ? null : dados.recado().strip();
        if (recado != null && recado.isEmpty()) recado = null;
        if (recado != null && recado.length() > 80) throw new IllegalArgumentException("Recado acima de 80 caracteres");
        jdbc.update("""
            insert into chat_status(usuario_id,status,recado,atualizado_em) values (?,?,?,now())
            on conflict (usuario_id) do update set status=excluded.status, recado=excluded.recado, atualizado_em=now()
            """, user.id(), status, recado);
        var novo = new MeuStatus(status, recado);
        Map<String, Object> evento = new HashMap<>();
        evento.put("usuarioId", user.id()); evento.put("status", status); evento.put("recado", recado);
        depoisDoCommit(() -> eventos.difundirTodos("status", evento));
        return novo;
    }

    // ------------------------------------------------------------------ retencao (LGPD)

    @Transactional(readOnly = true)
    public Configuracao configuracao() {
        return jdbc.queryForObject("select retencao_meses from chat_configuracao where id=1",
                (r, n) -> new Configuracao((Integer) r.getObject("retencao_meses")));
    }

    @Transactional
    public Configuracao definirConfiguracao(Configuracao dados, UsuarioAutenticado user) {
        if (!admin(user)) throw new AccessDeniedException("Só o ADMIN define o prazo de guarda das mensagens");
        Integer meses = dados.retencaoMeses();
        if (meses != null && (meses < 1 || meses > 120)) throw new IllegalArgumentException("Prazo entre 1 e 120 meses (ou sem prazo)");
        jdbc.update("update chat_configuracao set retencao_meses=?, atualizado_por=?, atualizado_em=now() where id=1", meses, user.id());
        return configuracao();
    }

    /** Todo dia de madrugada apaga mensagens (e anexos) mais antigas que o prazo definido pelo ADMIN. */
    @Scheduled(cron = "${faturamed.chat.limpeza-cron:0 30 3 * * *}")
    @Transactional
    public int aplicarRetencao() {
        Integer meses = configuracao().retencaoMeses();
        if (meses == null) return 0;
        return jdbc.update("delete from chat_mensagem where criado_em < now() - make_interval(months => ?)", meses);
    }

    // ------------------------------------------------------------------ avisos do sistema (Robo FaturaMed)

    /** Mensagem automatica do sistema para uma pessoa, na conversa dela com o "Robô FaturaMed". */
    @Transactional
    public void avisarSistema(long usuarioId, String texto) {
        long robo = idRobo();
        if (robo == 0 || usuarioId == robo) return;
        long conversaId = garantirDireta(robo, usuarioId);
        gravar(conversaId, robo, texto, List.of(), null, List.of(usuarioId, robo));
    }

    // ------------------------------------------------------------------ apoio

    private Mensagem buscarMensagem(long id) {
        Mensagem m = jdbc.queryForObject(SELECT_MENSAGEM + " where m.id=?", (r, n) -> mensagem(r), id);
        return comDetalhes(List.of(m)).getFirst();
    }

    private static final java.util.regex.Pattern MENCAO = java.util.regex.Pattern.compile("@([\\p{L}][\\p{L}.\\-]{1,40})");

    /**
     * Quem foi mencionado: "@Ana" (primeiro nome, se for unico na conversa), "@Ana.Souza" (nome completo com pontos)
     * ou "@todos" (todos os participantes). So vale para quem participa da conversa; o autor nao conta.
     */
    static Set<Long> detectarMencoes(String texto, Map<Long, String> participantes, long autorId) {
        Set<Long> achados = new LinkedHashSet<>();
        if (texto == null || texto.indexOf('@') < 0) return achados;
        Map<String, List<Long>> porPrimeiroNome = new HashMap<>();
        Map<String, Long> porNomeCompleto = new HashMap<>();
        participantes.forEach((id, nome) -> {
            String n = normalizar(nome);
            porNomeCompleto.put(n.replace(' ', '.'), id);
            porPrimeiroNome.computeIfAbsent(n.split(" ")[0], k -> new ArrayList<>()).add(id);
        });
        var m = MENCAO.matcher(texto);
        while (m.find()) {
            String token = normalizar(m.group(1)).replaceAll("[.\\-]+$", "");
            if (token.equals("todos") || token.equals("all")) { achados.addAll(participantes.keySet()); continue; }
            Long completo = porNomeCompleto.get(token.replace('-', '.'));
            if (completo != null) { achados.add(completo); continue; }
            List<Long> primeiro = porPrimeiroNome.get(token);
            if (primeiro != null && primeiro.size() == 1) achados.add(primeiro.getFirst());
        }
        achados.remove(autorId);
        return achados;
    }

    private static String normalizar(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    /** Papeis mencionaveis: "@admin", "@dev", "@operador" (e plurais) e "@equipe" (toda a equipe interna da conversa). */
    static final Map<String, Set<String>> PAPEIS = Map.of(
            "admin", Set.of("ADMIN"), "admins", Set.of("ADMIN"), "administradores", Set.of("ADMIN"),
            "dev", Set.of("DEV"), "devs", Set.of("DEV"), "desenvolvedores", Set.of("DEV"),
            "operador", Set.of("OPERADOR"), "operadores", Set.of("OPERADOR"),
            "equipe", Set.of("ADMIN", "DEV", "OPERADOR"));

    static Set<Long> detectarPapeis(String texto, Map<Long, String> perfis, long autorId) {
        Set<Long> achados = new LinkedHashSet<>();
        if (texto == null || texto.indexOf('@') < 0) return achados;
        var m = MENCAO.matcher(texto);
        while (m.find()) {
            Set<String> alvo = PAPEIS.get(normalizar(m.group(1)).replaceAll("[.\\-]+$", ""));
            if (alvo != null) perfis.forEach((id, perfil) -> { if (alvo.contains(perfil)) achados.add(id); });
        }
        achados.remove(autorId);
        return achados;
    }

    private void registrarMencoes(long mensagemId, String texto, long autorId, List<Long> participantes) {
        if (texto == null || texto.indexOf('@') < 0 || participantes.isEmpty()) return;
        Map<Long, String> nomes = new HashMap<>();
        String ids = String.join(",", participantes.stream().map(String::valueOf).toList());
        Map<Long, String> perfis = new HashMap<>();
        jdbc.query("select id, nome, perfil from usuario_acesso where id in (" + ids + ")", r -> { nomes.put(r.getLong("id"), r.getString("nome")); perfis.put(r.getLong("id"), r.getString("perfil")); });
        Set<Long> mencionados = new LinkedHashSet<>(detectarMencoes(texto, nomes, autorId));
        mencionados.addAll(detectarPapeis(texto, perfis, autorId));
        for (Long id : mencionados)
            jdbc.update("insert into chat_mencao(mensagem_id,usuario_id) values (?,?) on conflict do nothing", mensagemId, id);
    }

    private Mensagem alterada(long mensagemId, long conversaId) {
        Mensagem msg = buscarMensagem(mensagemId);
        List<Long> participantes = jdbc.queryForList("select usuario_id from chat_participante where conversa_id=?", Long.class, conversaId);
        depoisDoCommit(() -> eventos.enviar(participantes, "mensagem-alterada", msg));
        return msg;
    }

    /** Confere que a mensagem e da pessoa, nao foi apagada e esta no prazo; devolve a conversa. */
    private long propria(long mensagemId, UsuarioAutenticado user) {
        var linhas = jdbc.query("select conversa_id, autor_id, criado_em, apagada_em from chat_mensagem where id=?",
                (r, n) -> new Object[]{r.getLong("conversa_id"), r.getLong("autor_id"), r.getTimestamp("criado_em").toInstant(), r.getTimestamp("apagada_em")}, mensagemId);
        if (linhas.isEmpty()) throw new RecursoNaoEncontradoException("Mensagem nao encontrada");
        var m = linhas.getFirst();
        participantes((long) m[0], user);
        if ((long) m[1] != user.id()) throw new AccessDeniedException("Só quem enviou pode mudar a mensagem");
        if (m[3] != null) throw new IllegalArgumentException("Esta mensagem já foi apagada");
        if (((Instant) m[2]).plus(PRAZO_EDICAO).isBefore(Instant.now()))
            throw new IllegalArgumentException("Só dá para editar ou apagar nos primeiros 15 minutos");
        return (long) m[0];
    }

    private long conversaDaMensagem(long mensagemId, UsuarioAutenticado user) {
        var ids = jdbc.queryForList("select conversa_id from chat_mensagem where id=? and apagada_em is null", Long.class, mensagemId);
        if (ids.isEmpty()) throw new RecursoNaoEncontradoException("Mensagem nao encontrada");
        participantes(ids.getFirst(), user);
        return ids.getFirst();
    }

    private List<Mensagem> comDetalhes(List<Mensagem> mensagens) {
        if (mensagens.isEmpty()) return mensagens;
        Map<Long, List<Anexo>> anexos = new HashMap<>();
        Map<Long, Map<String, List<Object[]>>> reacoes = new HashMap<>();
        Map<Long, List<Long>> mencoes = new HashMap<>();
        String ids = String.join(",", mensagens.stream().map(m -> String.valueOf(m.id())).toList());
        jdbc.query("select id, mensagem_id, nome, tipo, tamanho from chat_anexo where mensagem_id in (" + ids + ") order by id", r -> {
            anexos.computeIfAbsent(r.getLong("mensagem_id"), k -> new ArrayList<>())
                    .add(new Anexo(r.getLong("id"), r.getString("nome"), r.getString("tipo"), r.getInt("tamanho")));
        });
        jdbc.query("select mensagem_id, usuario_id from chat_mencao where mensagem_id in (" + ids + ")", r -> {
            mencoes.computeIfAbsent(r.getLong("mensagem_id"), k -> new ArrayList<>()).add(r.getLong("usuario_id"));
        });
        jdbc.query("select r.mensagem_id, r.emoji, r.usuario_id, u.nome from chat_reacao r join usuario_acesso u on u.id=r.usuario_id"
                + " where r.mensagem_id in (" + ids + ") order by r.criado_em", r -> {
            reacoes.computeIfAbsent(r.getLong("mensagem_id"), k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getString("emoji"), k -> new ArrayList<>()).add(new Object[]{r.getLong("usuario_id"), r.getString("nome")});
        });
        return mensagens.stream().map(m -> {
            List<Reacao> lista = reacoes.getOrDefault(m.id(), Map.of()).entrySet().stream()
                    .map(e -> new Reacao(e.getKey(), e.getValue().stream().map(x -> (Long) x[0]).toList(), e.getValue().stream().map(x -> (String) x[1]).toList()))
                    .toList();
            return new Mensagem(m.id(), m.conversaId(), m.autorId(), m.autorNome(), m.texto(), m.criadoEm(),
                    anexos.getOrDefault(m.id(), List.of()), m.autorFoto(), m.resposta(), lista, m.editada(), m.apagada(), m.sistema(),
                    mencoes.getOrDefault(m.id(), List.of()));
        }).toList();
    }

    /** Participantes da conversa; recusa quem nao faz parte dela. */
    private List<Long> participantes(long conversaId, UsuarioAutenticado user) {
        List<Long> ids = jdbc.queryForList("select usuario_id from chat_participante where conversa_id=?", Long.class, conversaId);
        if (!ids.contains(user.id())) throw new RecursoNaoEncontradoException("Conversa nao encontrada");
        return ids;
    }

    private Long exigirGrupo(long conversaId) {
        var linhas = jdbc.queryForList("select criado_por from chat_conversa where id=? and tipo='GRUPO'", Long.class, conversaId);
        if (linhas.isEmpty()) throw new IllegalArgumentException("Só é possível mudar participantes de um grupo");
        return linhas.getFirst();
    }

    private boolean oficial(long conversaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select oficial from chat_conversa where id=?", Boolean.class, conversaId));
    }

    private boolean conversaDoRobo(long conversaId) {
        long robo = idRobo();
        if (robo == 0) return false;
        Integer n = jdbc.queryForObject("select count(*) from chat_participante where conversa_id=? and usuario_id=?", Integer.class, conversaId, robo);
        return n != null && n > 0;
    }

    private long idRobo() {
        return jdbc.queryForList("select id from usuario_acesso where email=?", Long.class, EMAIL_ROBO).stream().findFirst().orElse(0L);
    }

    private static boolean admin(UsuarioAutenticado user) {
        return "ADMIN".equals(user.perfil());
    }

    private void exigirInterno(long usuarioId) {
        Integer existe = jdbc.queryForObject("select count(*) from usuario_acesso where id=? and ativo=true and perfil in "
                + PERFIS_INTERNOS, Integer.class, usuarioId);
        if (existe == null || existe == 0) throw new RecursoNaoEncontradoException("Usuario nao encontrado");
    }

    private void depoisDoCommit(Runnable acao) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { acao.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { acao.run(); }
        });
    }

    private Contato contato(ResultSet r, String coluna) throws SQLException {
        long id = r.getLong(coluna);
        return new Contato(id, r.getString("nome"), r.getString("perfil"), eventos.online(id), (Long) r.getObject("foto"),
                Optional.ofNullable(r.getString("status")).orElse("DISPONIVEL"), r.getString("recado"));
    }

    private static Mensagem mensagem(ResultSet r) throws SQLException {
        boolean apagada = r.getTimestamp("apagada_em") != null;
        Resposta resposta = null;
        if (r.getObject("responde_a") != null && r.getObject("resp_autor") != null) {
            String t = r.getTimestamp("resp_apagada") != null ? "🚫 Mensagem apagada"
                    : r.getString("resp_texto").isEmpty() ? (r.getInt("resp_anexos") > 0 ? "📎 Anexo" : "") : resumo(r.getString("resp_texto"));
            resposta = new Resposta(r.getLong("responde_a"), r.getString("resp_nome"), t);
        }
        return new Mensagem(r.getLong("id"), r.getLong("conversa_id"), r.getLong("autor_id"), r.getString("nome"),
                apagada ? "" : r.getString("texto"), r.getTimestamp("criado_em").toInstant(), List.of(), (Long) r.getObject("foto"),
                resposta, List.of(), r.getTimestamp("editada_em") != null, apagada, EMAIL_ROBO.equals(r.getString("email")), List.of());
    }

    private static String resumo(String texto) {
        if (texto == null) return "";
        String t = texto.replaceAll("\\s+", " ").strip();
        return t.length() > 140 ? t.substring(0, 137) + "…" : t;
    }

    private static Instant instante(ResultSet r, String coluna) throws SQLException {
        var t = r.getTimestamp(coluna);
        return t == null ? null : t.toInstant();
    }
}
