package br.com.faturamed.seguranca;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recuperacao de senha por codigo de 6 digitos enviado ao e-mail de login. */
@Service
public class RecuperacaoSenhaService {
    static final Duration VALIDADE = Duration.ofMinutes(15);
    static final int MAX_TENTATIVAS = 5;
    static final int MAX_SOLICITACOES = 3;
    private static final Logger LOG = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String INVALIDO = "Codigo invalido ou expirado. Solicite um novo codigo";

    private final JdbcTemplate jdbc;
    private final UsuarioService usuarios;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> email;
    private final String servidor;
    private final String remetente;
    private final boolean codigoNoLog;

    public RecuperacaoSenhaService(JdbcTemplate jdbc, UsuarioService usuarios, PasswordEncoder encoder, ObjectProvider<JavaMailSender> email,
            @Value("${spring.mail.host:}") String servidor, @Value("${faturamed.recuperacao-senha.remetente:}") String remetente,
            @Value("${faturamed.recuperacao-senha.codigo-no-log:false}") boolean codigoNoLog) {
        this.jdbc = jdbc; this.usuarios = usuarios; this.encoder = encoder; this.email = email;
        this.servidor = servidor; this.remetente = remetente; this.codigoNoLog = codigoNoLog;
    }

    /**
     * Gera e envia o codigo. Nao informa se o e-mail existe: quem chama recebe sempre a mesma resposta.
     */
    @Transactional
    public void solicitar(String login) {
        var conta = conta(login);
        if (conta == null) return;
        // Serializa pedidos da mesma conta para o limite de solicitacoes valer tambem em paralelo.
        jdbc.queryForList("select id from usuario_acesso where id=? for update", conta.id());
        Long recentes = jdbc.queryForObject("select count(*) from recuperacao_senha where usuario_id=? and criado_em>now()-interval '15 minutes'",
                Long.class, conta.id());
        if (recentes != null && recentes >= MAX_SOLICITACOES) {
            LOG.warn("Recuperacao de senha: limite de solicitacoes atingido para o usuario {}", conta.id());
            return;
        }
        jdbc.update("update recuperacao_senha set expira_em=now() where usuario_id=? and usado_em is null and expira_em>now()", conta.id());
        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        jdbc.update("insert into recuperacao_senha(usuario_id,codigo_hash,expira_em) values(?,?,now()+?::interval)",
                conta.id(), encoder.encode(codigo), VALIDADE.toMinutes() + " minutes");
        enviar(conta, codigo);
    }

    /** Confere o codigo e troca a senha. Tentativas erradas ficam gravadas mesmo com o erro. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void redefinir(String login, String codigo, String novaSenha) {
        UsuarioService.validarSenha(novaSenha);
        var conta = conta(login);
        if (conta == null || codigo == null || !codigo.trim().matches("[0-9]{6}")) throw new IllegalArgumentException(INVALIDO);
        var pedidos = jdbc.queryForList("""
            select id,codigo_hash,tentativas from recuperacao_senha where usuario_id=? and usado_em is null and expira_em>now()
            order by id desc limit 1 for update
            """, conta.id());
        if (pedidos.isEmpty()) throw new IllegalArgumentException(INVALIDO);
        var pedido = pedidos.getFirst();
        long id = ((Number) Objects.requireNonNull(pedido.get("id"))).longValue();
        if (!encoder.matches(codigo.trim(), String.valueOf(pedido.get("codigo_hash")))) {
            // Esgotadas as tentativas, o codigo deixa de valer (protege contra adivinhar os 6 digitos).
            jdbc.update("update recuperacao_senha set tentativas=tentativas+1, expira_em=case when tentativas+1>=? then now() else expira_em end where id=?",
                    MAX_TENTATIVAS, id);
            throw new IllegalArgumentException(INVALIDO);
        }
        jdbc.update("update recuperacao_senha set usado_em=now() where id=?", id);
        jdbc.update("update recuperacao_senha set expira_em=now() where usuario_id=? and usado_em is null", conta.id());
        // Mesmo caminho da redefinicao pelo administrador: encerra as sessoes, desbloqueia e audita.
        usuarios.redefinir(conta.id(), new UsuarioService.NovaSenha(novaSenha, conta.versao()), null);
    }

    private UsuarioAutenticado conta(String login) {
        try {
            var conta = usuarios.loadUserByUsername(login);
            return conta.isEnabled() ? conta : null;
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private void enviar(UsuarioAutenticado conta, String codigo) {
        var sender = servidor.isBlank() ? null : email.getIfAvailable();
        if (sender == null) {
            if (codigoNoLog) LOG.warn("Recuperacao de senha (SMTP nao configurado, modo local): codigo {} para {}", codigo, conta.email());
            else LOG.error("Recuperacao de senha: SMTP nao configurado (MAIL_HOST); o codigo para o usuario {} nao foi enviado", conta.id());
            return;
        }
        var mensagem = new SimpleMailMessage();
        if (!remetente.isBlank()) mensagem.setFrom(remetente);
        mensagem.setTo(conta.email());
        mensagem.setSubject("FaturaMed - codigo para redefinir sua senha");
        mensagem.setText("""
                Ola, %s.

                Recebemos um pedido para redefinir a senha do seu acesso a FaturaMed.
                Seu codigo e: %s

                Ele vale por %d minutos e so pode ser usado uma vez.
                Se voce nao pediu a redefinicao, ignore este e-mail; sua senha continua a mesma.
                """.formatted(conta.nome(), codigo, VALIDADE.toMinutes()));
        try {
            sender.send(mensagem);
        } catch (MailException e) {
            LOG.error("Recuperacao de senha: falha ao enviar e-mail para o usuario {}: {}", conta.id(), e.getMessage());
        }
    }
}
