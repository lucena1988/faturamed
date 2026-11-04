package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123","spring.mail.host=smtp.teste.local","faturamed.recuperacao-senha.remetente=nao-responda@faturamed.local"})
@EnabledIfEnvironmentVariable(named="BASE_HOSPITALAR_TEST_DB",matches="true")
@Transactional
class RecuperacaoSenhaIntegracaoTest {
    @Autowired RecuperacaoSenhaService recuperacao;
    @Autowired UsuarioService usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender email;
    String login;
    long id;

    @BeforeEach void preparar() {
        login="recupera."+UUID.randomUUID().toString().substring(0,8)+"@teste.local";
        id=usuarios.criar(new UsuarioService.Novo(login,"Conta Recuperacao","SenhaAntigaSegura12","OPERADOR",null)).id();
    }
    String codigoEnviado() {
        var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(email,atLeastOnce()).send(captor.capture());
        var mensagem=captor.getValue();
        assertThat(mensagem.getTo()).containsExactly(login);
        assertThat(mensagem.getFrom()).isEqualTo("nao-responda@faturamed.local");
        var achado=Pattern.compile("codigo e: (\\d{6})").matcher(Objects.requireNonNull(mensagem.getText()));
        assertThat(achado.find()).isTrue();
        return achado.group(1);
    }
    String errado(String codigo) { return codigo.equals("000000")?"111111":"000000"; }

    @Test void codigoCorretoTrocaASenhaUmaVezEEncerraSessoes() {
        long versao=usuarios.buscar(id).versao();
        recuperacao.solicitar(login.toUpperCase());
        String codigo=codigoEnviado();
        assertThat(jdbc.queryForObject("select codigo_hash from recuperacao_senha where usuario_id=?",String.class,id)).doesNotContain(codigo);
        assertThatThrownBy(()->recuperacao.redefinir(login,errado(codigo),"SenhaNovaSegura123")).hasMessageContaining("Codigo invalido");
        assertThatThrownBy(()->recuperacao.redefinir(login,codigo,"curta")).hasMessageContaining("12 caracteres");
        recuperacao.redefinir(login,codigo,"SenhaNovaSegura123");
        assertThat(encoder.matches("SenhaNovaSegura123",usuarios.loadUserByUsername(login).getPassword())).isTrue();
        assertThat(usuarios.buscar(id).versao()).as("versao nova derruba as sessoes abertas").isGreaterThan(versao);
        assertThat(jdbc.queryForObject("select count(*) from usuario_acesso_auditoria where usuario_id=? and acao='REDEFINICAO_SENHA'",Long.class,id)).isEqualTo(1);
        assertThatThrownBy(()->recuperacao.redefinir(login,codigo,"OutraSenhaSegura12")).hasMessageContaining("Codigo invalido");
    }

    @Test void cincoErrosInvalidamOCodigo() {
        recuperacao.solicitar(login);
        String codigo=codigoEnviado();
        for(int i=0;i<RecuperacaoSenhaService.MAX_TENTATIVAS;i++) {
            String tentativa=String.format("%06d",(Integer.parseInt(codigo)+1+i)%1_000_000);
            assertThatThrownBy(()->recuperacao.redefinir(login,tentativa,"SenhaNovaSegura123")).hasMessageContaining("Codigo invalido");
        }
        assertThatThrownBy(()->recuperacao.redefinir(login,codigo,"SenhaNovaSegura123")).hasMessageContaining("Codigo invalido");
        assertThat(encoder.matches("SenhaAntigaSegura12",usuarios.loadUserByUsername(login).getPassword())).isTrue();
    }

    @Test void novoPedidoInvalidaOAnteriorERespeitaOLimite() {
        recuperacao.solicitar(login);
        String primeiro=codigoEnviado();
        recuperacao.solicitar(login);
        recuperacao.solicitar(login);
        recuperacao.solicitar(login);
        verify(email,times(RecuperacaoSenhaService.MAX_SOLICITACOES)).send(any(SimpleMailMessage.class));
        String ultimo=codigoEnviado();
        if(!ultimo.equals(primeiro))
            assertThatThrownBy(()->recuperacao.redefinir(login,primeiro,"SenhaNovaSegura123")).hasMessageContaining("Codigo invalido");
        recuperacao.redefinir(login,ultimo,"SenhaNovaSegura123");
    }

    @Test void emailInexistenteOuContaInativaNaoEnviaNada() {
        recuperacao.solicitar("ninguem@teste.local");
        recuperacao.solicitar(null);
        jdbc.update("update usuario_acesso set ativo=false where id=?",id);
        recuperacao.solicitar(login);
        verify(email,never()).send(any(SimpleMailMessage.class));
        assertThatThrownBy(()->recuperacao.redefinir("ninguem@teste.local","123456","SenhaNovaSegura123")).hasMessageContaining("Codigo invalido");
    }
}
