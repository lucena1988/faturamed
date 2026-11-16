package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.MedicoCadastroService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ADMIN_INITIAL_PASSWORD=TesteIntegracaoSomente123")
@EnabledIfEnvironmentVariable(named="BASE_HOSPITALAR_TEST_DB",matches="true")
@Transactional
class ConviteMedicoIntegracaoTest {
    @Autowired MedicoCadastroService medicos;
    @Autowired ConviteMedicoService convites;
    @Autowired UsuarioService usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    long admin;
    String sufixo;

    @BeforeEach void preparar() {
        admin=Objects.requireNonNull(jdbc.queryForObject("select id from usuario_acesso where perfil='ADMIN' limit 1",Long.class));
        sufixo=UUID.randomUUID().toString().substring(0,8);
    }
    MedicoCadastroService.Medico novo(String nome) {
        return medicos.salvar(null,new MedicoCadastroService.Dados(nome,"","",true,List.of(),0L,null,"convite."+sufixo+"@teste.local"));
    }
    ConviteMedicoService.Aceite aceite(String nome,String cpf,String emails) {
        return new ConviteMedicoService.Aceite(nome,"0"+Math.abs(sufixo.hashCode()%1000000),"RJ",cpf,"Clinica Convite "+sufixo,"11.222.333/0001-81",emails,"SenhaTesteSegura12");
    }

    @Test void faturamedCadastraSemCrmEMedicoCompletaPeloConvite() {
        var medico=novo("Luiz Convite");
        assertThat(medico.crm()).isNull();
        assertThat(medico.uf()).isNull();
        assertThat(medico.cadastroCompleto()).isFalse();
        assertThatThrownBy(()->medicos.salvar(null,new MedicoCadastroService.Dados("Sem Email","","SP",true,List.of(),0L,null,null)))
            .hasMessageContaining("e-mail");

        var convite=convites.gerar(medico.id(),admin);
        assertThat(convite.token()).hasSizeGreaterThan(40);
        assertThat(convites.consultar(convite.token()).email()).isEqualTo("convite."+sufixo+"@teste.local");
        // So o hash fica no banco: o link nao pode ser reconstruido a partir dos dados salvos.
        assertThat(jdbc.queryForObject("select count(*) from medico_convite where token_hash=?",Long.class,convite.token())).isZero();

        assertThatThrownBy(()->convites.aceitar(convite.token(),aceite("Luiz","529.982.247-25","login."+sufixo+"@teste.local")))
            .hasMessageContaining("nome completo");
        String login="login."+sufixo+"@teste.local";
        var completo=convites.aceitar(convite.token(),aceite("Luiz Carlos Convite","529.982.247-25",login+"; outro."+sufixo+"@teste.local"));

        assertThat(completo.cadastroCompleto()).isTrue();
        assertThat(completo.uf()).isEqualTo("RJ");
        assertThat(completo.cpf()).isEqualTo("52998224725");
        assertThat(completo.email()).isEqualTo(login+";outro."+sufixo+"@teste.local");
        var conta=usuarios.loadUserByUsername(login);
        assertThat(conta.medicoId()).isEqualTo(medico.id());
        assertThat(encoder.matches("SenhaTesteSegura12",conta.getPassword())).isTrue();
        long empresa=Objects.requireNonNull(jdbc.queryForObject("select id from empresa_cliente where documento_normalizado='11222333000181'",Long.class));
        assertThat(completo.empresas()).containsExactly(empresa);

        assertThatThrownBy(()->convites.aceitar(convite.token(),aceite("Luiz Carlos Convite","529.982.247-25",login)))
            .isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("ja foi usado");
        assertThatThrownBy(()->convites.gerar(medico.id(),admin)).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("ja possui acesso");
    }

    @Test void novoConviteInvalidaOAnteriorECnpjExistenteReusaAEmpresa() {
        var medico=novo("Ana Convite");
        var primeiro=convites.gerar(medico.id(),admin);
        var segundo=convites.gerar(medico.id(),admin);
        assertThatThrownBy(()->convites.consultar(primeiro.token())).hasMessageContaining("expirado");
        jdbc.update("insert into empresa_cliente(nome,documento,documento_normalizado) values('Clinica Existente','11.222.333/0001-81','11222333000181') on conflict do nothing");
        long existente=Objects.requireNonNull(jdbc.queryForObject("select id from empresa_cliente where documento_normalizado='11222333000181'",Long.class));
        var completo=convites.aceitar(segundo.token(),aceite("Ana Maria Convite","390.533.447-05","ana."+sufixo+"@teste.local"));
        assertThat(completo.empresas()).containsExactly(existente);
        assertThat(jdbc.queryForObject("select count(*) from empresa_cliente where documento_normalizado='11222333000181'",Long.class)).isEqualTo(1);
        assertThatThrownBy(()->convites.consultar("x".repeat(43))).hasMessageContaining("Convite invalido");
    }

    @Test void conviteExpiradoERecusadoEMedicoExistenteFicaPendente() {
        var medico=novo("Bruno Convite");
        var convite=convites.gerar(medico.id(),admin);
        jdbc.update("update medico_convite set expira_em=now()-interval '1 minute' where medico_id=?",medico.id());
        assertThatThrownBy(()->convites.aceitar(convite.token(),aceite("Bruno Souza Convite","529.982.247-25","b."+sufixo+"@teste.local")))
            .hasMessageContaining("expirado");
        assertThat(medicos.cadastroCompleto(medico.id())).isFalse();
    }
}
