package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.MedicoCadastroService;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Convite de uso unico do medico: geracao, validade, uso unico e criacao do acesso (sem banco). */
class ConviteMedicoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MedicoCadastroService medicos = mock(MedicoCadastroService.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final ConviteMedicoService service = new ConviteMedicoService(jdbc, medicos, usuarios);
    private static final String TOKEN = "TokenSeguroDeTesteComMaisDe20Caracteres";
    private static final String SENHA = "SenhaMuitoSegura12";

    private MedicoCadastroService.Medico medico(long id, boolean ativo, String email) {
        var m = mock(MedicoCadastroService.Medico.class);
        when(m.ativo()).thenReturn(ativo); when(m.email()).thenReturn(email);
        when(m.nome()).thenReturn("Dra Ana"); when(m.crm()).thenReturn("123"); when(m.uf()).thenReturn("SP");
        when(medicos.buscar(id)).thenReturn(m);
        return m;
    }
    private void convite(Object medicoId, Object usadoEm, boolean valido) {
        var linha = new HashMap<String, Object>(); linha.put("medico_id", medicoId); linha.put("usado_em", usadoEm); linha.put("valido", valido);
        when(jdbc.queryForList(contains("from medico_convite where token_hash=?"), eq(ConviteMedicoService.hash(TOKEN)))).thenReturn(List.of(linha));
    }

    @Test
    void geraConviteSoParaMedicoAtivoComEmailESemAcesso() {
        medico(1, false, "a@x");
        assertThatThrownBy(() -> service.gerar(1, 9)).hasMessageContaining("Ative o medico");
        medico(2, true, " ");
        assertThatThrownBy(() -> service.gerar(2, 9)).hasMessageContaining("e-mail do medico");
        medico(3, true, "a@x");
        when(jdbc.queryForList(contains("from usuario_acesso where medico_id"), eq(3L))).thenReturn(List.of(Map.of("id", 1L)));
        assertThatThrownBy(() -> service.gerar(3, 9)).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test
    void conviteNovoInvalidaOsAnterioresEGuardaSoOHashDoToken() {
        medico(4, true, "a@x");
        when(jdbc.queryForList(contains("from usuario_acesso where medico_id"), eq(4L))).thenReturn(List.of());
        var c = service.gerar(4, 9);
        assertThat(c.token()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(c.expiraEm()).isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(6)));
        verify(jdbc).update(contains("update medico_convite set expira_em=now()"), eq(4L));
        verify(jdbc).update(contains("insert into medico_convite"), eq(4L), eq(ConviteMedicoService.hash(c.token())), any(), eq(9L));
        assertThat(ConviteMedicoService.hash("a")).hasSize(64).isEqualTo(ConviteMedicoService.hash("a")).isNotEqualTo(ConviteMedicoService.hash("b"));
    }

    @Test
    void tokenInvalidoUsadoOuExpiradoERecusado() {
        assertThatThrownBy(() -> service.consultar(null)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.consultar("curto")).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.queryForList(contains("from medico_convite where token_hash=?"), anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> service.consultar(TOKEN)).isInstanceOf(RecursoNaoEncontradoException.class);
        convite(5L, java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), true);
        assertThatThrownBy(() -> service.consultar(TOKEN)).hasMessageContaining("ja foi usado");
        convite(5L, null, false);
        assertThatThrownBy(() -> service.consultar(TOKEN)).hasMessageContaining("expirado");
    }

    @Test
    void consultarMostraOsDadosDoMedico() {
        convite(6L, null, true);
        medico(6, true, "ana@x");
        assertThat(service.consultar(TOKEN)).isEqualTo(new ConviteMedicoService.Previa("Dra Ana", "123", "SP", "ana@x"));
    }

    @Test
    void aceitarValidaDadosSenhaECriaOAcessoComOPrimeiroEmail() {
        assertThatThrownBy(() -> service.aceitar(TOKEN, null)).hasMessageContaining("dados do cadastro");
        var fraca = new ConviteMedicoService.Aceite("Ana", "123", "SP", "", "", "", "ana@x", "curta");
        assertThatThrownBy(() -> service.aceitar(TOKEN, fraca)).hasMessageContaining("12 caracteres");

        convite(7L, null, true);
        var m = medico(7, true, "ana@x");
        when(medicos.completar(eq(7L), any())).thenReturn(m);
        when(jdbc.queryForObject(contains("select criado_por"), eq(Long.class), anyString())).thenReturn(9L);
        var dados = new ConviteMedicoService.Aceite("Ana", "123", "SP", "", "", "", "ana@hospital.com, ana2@hospital.com", SENHA);
        assertThat(service.aceitar(TOKEN, dados)).isSameAs(m);
        verify(usuarios).criar(argThat(n -> n.email().equals("ana@hospital.com") && n.perfil().equals("MEDICO") && n.medicoId() == 7L), eq(9L));
        verify(jdbc).update("update medico_convite set usado_em=now() where token_hash=?", ConviteMedicoService.hash(TOKEN));
    }

    @Test
    void emailJaUsadoNoLoginViraConflitoClaro() {
        convite(8L, null, true);
        var m = medico(8, true, "ana@x");
        when(medicos.completar(eq(8L), any())).thenReturn(m);
        when(jdbc.queryForObject(contains("select criado_por"), eq(Long.class), anyString())).thenReturn(9L);
        when(usuarios.criar(any(), eq(9L))).thenThrow(new ConflitoRevisaoException("dup"));
        var dados = new ConviteMedicoService.Aceite("Ana", "123", "SP", "", "", "", "ana@hospital.com", SENHA);
        assertThatThrownBy(() -> service.aceitar(TOKEN, dados)).isInstanceOf(ConflitoRevisaoException.class).hasMessageContaining("ana@hospital.com ja possui acesso");
        verify(jdbc, never()).update(contains("set usado_em=now()"), anyString());
    }
}
