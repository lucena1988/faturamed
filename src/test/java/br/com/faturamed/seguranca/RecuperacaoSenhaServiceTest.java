package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Recuperacao de senha por codigo: nao revela contas, limita pedidos e tentativas (sem banco). */
@SuppressWarnings("unchecked")
class RecuperacaoSenhaServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ObjectProvider<JavaMailSender> provedor = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private static final String SENHA = "SenhaMuitoSegura12";
    private final UsuarioAutenticado ana = new UsuarioAutenticado(4, "ana@x", "Ana", "h", "OPERADOR", null, true, false, 7);

    private RecuperacaoSenhaService service(String smtp, String remetente, boolean noLog) {
        return new RecuperacaoSenhaService(jdbc, usuarios, encoder, provedor, smtp, remetente, noLog);
    }

    @Test
    void contaInexistenteOuInativaNaoRecebeNadaENaoERevelada() {
        when(usuarios.loadUserByUsername("nao@x")).thenThrow(new UsernameNotFoundException("x"));
        when(usuarios.loadUserByUsername("inativo@x")).thenReturn(new UsuarioAutenticado(5, "inativo@x", "I", "h", "OPERADOR", null, false, false, 1));
        var s = service("smtp", "", false);
        s.solicitar("nao@x");
        s.solicitar("inativo@x");
        verifyNoInteractions(jdbc, sender);
    }

    @Test
    void limiteDeTresPedidosEm15Minutos() {
        when(usuarios.loadUserByUsername("ana@x")).thenReturn(ana);
        when(jdbc.queryForObject(contains("from recuperacao_senha"), eq(Long.class), eq(4L))).thenReturn(3L);
        service("smtp", "", false).solicitar("ana@x");
        verify(jdbc, never()).update(contains("insert into recuperacao_senha"), any(), any(), any());
    }

    @Test
    void geraCodigoDeSeisDigitosInvalidaOsAnterioresEEnviaPorEmail() {
        when(usuarios.loadUserByUsername("ana@x")).thenReturn(ana);
        when(jdbc.queryForObject(contains("from recuperacao_senha"), eq(Long.class), eq(4L))).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(provedor.getIfAvailable()).thenReturn(sender);
        service("smtp.local", "nao-responda@faturamed", false).solicitar("ana@x");
        var codigo = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(codigo.capture());
        assertThat(codigo.getValue()).matches("[0-9]{6}");
        verify(jdbc).update(contains("set expira_em=now() where usuario_id=?"), eq(4L));
        verify(jdbc).update(contains("insert into recuperacao_senha"), eq(4L), eq("hash"), eq("15 minutes"));
        var email = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(email.capture());
        assertThat(email.getValue().getTo()).containsExactly("ana@x");
        assertThat(email.getValue().getFrom()).isEqualTo("nao-responda@faturamed");
        assertThat(email.getValue().getText()).contains(codigo.getValue()).contains("15 minutos");
    }

    @Test
    void semSmtpOuComFalhaNoEnvioNaoQuebraOPedido() {
        when(usuarios.loadUserByUsername("ana@x")).thenReturn(ana);
        when(jdbc.queryForObject(contains("from recuperacao_senha"), eq(Long.class), eq(4L))).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("hash");
        service("", "", true).solicitar("ana@x");   // sem SMTP, modo local: codigo vai para o log
        service("", "", false).solicitar("ana@x");  // sem SMTP: so registra o erro
        verify(provedor, never()).getIfAvailable();
        when(provedor.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("fora do ar")).when(sender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> service("smtp", "", false).solicitar("ana@x")).doesNotThrowAnyException();
    }

    @Test
    void redefinirValidaSenhaCodigoEPedidoValido() {
        var s = service("smtp", "", false);
        assertThatThrownBy(() -> s.redefinir("ana@x", "123456", "curta")).hasMessageContaining("12 caracteres");
        when(usuarios.loadUserByUsername("ana@x")).thenReturn(ana);
        assertThatThrownBy(() -> s.redefinir("ana@x", "12a456", SENHA)).hasMessageContaining("Codigo invalido");
        assertThatThrownBy(() -> s.redefinir("ana@x", null, SENHA)).hasMessageContaining("Codigo invalido");
        when(jdbc.queryForList(contains("from recuperacao_senha where usuario_id=?"), eq(4L))).thenReturn(List.of());
        assertThatThrownBy(() -> s.redefinir("ana@x", "123456", SENHA)).hasMessageContaining("Codigo invalido");
        verify(usuarios, never()).redefinir(anyLong(), any(), any());
    }

    @Test
    void codigoErradoContaTentativaECodigoCertoTrocaASenha() {
        var s = service("smtp", "", false);
        when(usuarios.loadUserByUsername("ana@x")).thenReturn(ana);
        when(jdbc.queryForList(contains("from recuperacao_senha where usuario_id=?"), eq(4L)))
                .thenReturn(List.of(Map.of("id", 30L, "codigo_hash", "hash", "tentativas", 0)));
        when(encoder.matches("111111", "hash")).thenReturn(false);
        assertThatThrownBy(() -> s.redefinir("ana@x", "111111", SENHA)).hasMessageContaining("Codigo invalido");
        verify(jdbc).update(contains("tentativas=tentativas+1"), eq(5), eq(30L));

        when(encoder.matches("222222", "hash")).thenReturn(true);
        s.redefinir("ana@x", " 222222 ", SENHA);
        verify(jdbc).update("update recuperacao_senha set usado_em=now() where id=?", 30L);
        verify(jdbc).update("update recuperacao_senha set expira_em=now() where usuario_id=? and usado_em is null", 4L);
        verify(usuarios).redefinir(eq(4L), argThat(n -> n.senha().equals(SENHA) && n.versao() == 7L), isNull());
    }
}
