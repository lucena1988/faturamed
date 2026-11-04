package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.MedicoCadastroService;
import br.com.faturamed.conciliacao.ValidacaoCadastroMedico;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Convite de uso unico para o medico completar o cadastro e criar o proprio acesso. */
@Service
public class ConviteMedicoService {
    public record Convite(String token, Instant expiraEm) {}
    public record Previa(String nome, String crm, String uf, String email) {}
    public record Aceite(String nome, String crm, String uf, String cpf, String empresaNome, String empresaCnpj, String emails, String senha) {}
    static final Duration VALIDADE = Duration.ofDays(7);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final MedicoCadastroService medicos;
    private final UsuarioService usuarios;

    public ConviteMedicoService(JdbcTemplate jdbc, MedicoCadastroService medicos, UsuarioService usuarios) {
        this.jdbc = jdbc; this.medicos = medicos; this.usuarios = usuarios;
    }

    @Transactional
    public Convite gerar(long medicoId, long operador) {
        var medico = medicos.buscar(medicoId);
        if (!medico.ativo()) throw new IllegalArgumentException("Ative o medico antes de enviar o convite");
        if (medico.email() == null || medico.email().isBlank()) throw new IllegalArgumentException("Informe o e-mail do medico antes de gerar o convite");
        if (!jdbc.queryForList("select id from usuario_acesso where medico_id=?", medicoId).isEmpty())
            throw new ConflitoRevisaoException("O medico ja possui acesso. Ao entrar, ele sera levado a completar o cadastro");
        // Um convite novo invalida os anteriores ainda nao usados.
        jdbc.update("update medico_convite set expira_em=now() where medico_id=? and usado_em is null and expira_em>now()", medicoId);
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = Instant.now().plus(VALIDADE);
        jdbc.update("insert into medico_convite(medico_id,token_hash,expira_em,criado_por) values(?,?,?,?)",
                medicoId, hash(token), java.sql.Timestamp.from(expira), operador);
        return new Convite(token, expira);
    }

    @Transactional(readOnly = true)
    public Previa consultar(String token) {
        var medico = medicos.buscar(convite(token, false));
        return new Previa(medico.nome(), medico.crm(), medico.uf(), medico.email());
    }

    @Transactional
    public MedicoCadastroService.Medico aceitar(String token, Aceite dados) {
        if (dados == null) throw new IllegalArgumentException("Informe os dados do cadastro");
        UsuarioService.validarSenha(dados.senha());
        long medicoId = convite(token, true);
        var medico = medicos.completar(medicoId, new MedicoCadastroService.Complemento(dados.nome(), dados.crm(), dados.uf(),
                dados.cpf(), dados.empresaNome(), dados.empresaCnpj(), dados.emails()));
        // O primeiro e-mail da lista vira o login do medico.
        String login = ValidacaoCadastroMedico.emails(dados.emails()).getFirst();
        long criadoPor = Objects.requireNonNull(jdbc.queryForObject(
                "select criado_por from medico_convite where token_hash=?", Long.class, hash(token)));
        try {
            usuarios.criar(new UsuarioService.Novo(login, medico.nome(), dados.senha(), "MEDICO", medicoId), criadoPor);
        } catch (ConflitoRevisaoException | DuplicateKeyException e) {
            throw new ConflitoRevisaoException("O e-mail " + login + " ja possui acesso. Use outro e-mail como o primeiro da lista");
        }
        jdbc.update("update medico_convite set usado_em=now() where token_hash=?", hash(token));
        return medico;
    }

    /** Devolve o medico do convite valido; com bloqueio, impede dois aceites simultaneos. */
    private long convite(String token, boolean bloquear) {
        if (token == null || token.length() < 20 || token.length() > 100) throw new RecursoNaoEncontradoException("Convite invalido");
        var linhas = jdbc.queryForList("select medico_id,usado_em,expira_em>now() as valido from medico_convite where token_hash=?"
                + (bloquear ? " for update" : ""), hash(token));
        if (linhas.isEmpty()) throw new RecursoNaoEncontradoException("Convite invalido");
        var linha = linhas.getFirst();
        if (linha.get("usado_em") != null) throw new ConflitoRevisaoException("Este convite ja foi usado. Entre com seu e-mail e senha");
        if (!Boolean.TRUE.equals(linha.get("valido"))) throw new ConflitoRevisaoException("Convite expirado. Peca um novo convite a FaturaMed");
        return ((Number) linha.get("medico_id")).longValue();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
