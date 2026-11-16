package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.conciliacao.EmpresaClienteService;
import br.com.faturamed.conciliacao.MedicoCadastroService;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Regras de acesso: vinculos por perfil, ultimo administrador, senha forte e concorrencia (sem banco). */
@SuppressWarnings("unchecked")
class UsuarioServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MedicoCadastroService medicos = mock(MedicoCadastroService.class);
    private final EmpresaClienteService empresas = mock(EmpresaClienteService.class);
    private final UsuarioService service = spy(new UsuarioService(jdbc, encoder, medicos, empresas));
    private static final String SENHA = "SenhaMuitoSegura12";

    private UsuarioService.Usuario usuario(long id, String perfil, Long medico, Long empresa) {
        var u = new UsuarioService.Usuario(id, "u" + id + "@x", "Usuario " + id, perfil, medico, true, 3, empresa, null);
        doReturn(u).when(service).buscar(id);
        return u;
    }
    private void medicoAtivo(long id, boolean ativo) {
        var m = mock(MedicoCadastroService.Medico.class); when(m.ativo()).thenReturn(ativo); when(medicos.buscar(id)).thenReturn(m);
    }
    private void empresaAtiva(long id, boolean ativa) {
        var e = mock(EmpresaClienteService.Empresa.class); when(e.ativo()).thenReturn(ativa); when(empresas.buscar(id)).thenReturn(e);
    }

    @Test
    void senhaPrecisaDe12CaracteresEAte72Bytes() {
        assertThatThrownBy(() -> UsuarioService.validarSenha(null)).hasMessageContaining("12 caracteres");
        assertThatThrownBy(() -> UsuarioService.validarSenha("curta")).hasMessageContaining("12 caracteres");
        assertThatThrownBy(() -> UsuarioService.validarSenha("é".repeat(40))).hasMessageContaining("72 bytes"); // 80 bytes
        UsuarioService.validarSenha(SENHA);
        assertThat(UsuarioService.email("  Ana@Hospital.COM ")).isEqualTo("ana@hospital.com");
        assertThat(UsuarioService.email(null)).isEmpty();
    }

    @Test
    void cadaPerfilSoAceitaOProprioVinculo() {
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", " ", SENHA, "ADMIN", null))).hasMessageContaining("Dados de acesso");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "ROOT", null))).hasMessageContaining("Dados de acesso");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "OPERADOR", null, null, 3L))).hasMessageContaining("Somente o perfil Hospital");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "OPERADOR", 5L))).hasMessageContaining("Somente o perfil Medico");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "OPERADOR", null, 6L))).hasMessageContaining("Somente o perfil Empresa");
    }

    @Test
    void vinculoPrecisaEstarAtivo() {
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "HOSPITAL", null, null, null))).hasMessageContaining("hospital ativo");
        doReturn(false).when(jdbc).query(contains("from hospital_cadastro"), any(ResultSetExtractor.class), eq(3L));
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "HOSPITAL", null, null, 3L))).hasMessageContaining("hospital ativo");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "MEDICO", null))).hasMessageContaining("medico ativo");
        medicoAtivo(5, false);
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "MEDICO", 5L))).hasMessageContaining("medico ativo");
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "EMPRESA", null))).hasMessageContaining("empresa ativa");
        empresaAtiva(6, false);
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "EMPRESA", null, 6L))).hasMessageContaining("empresa ativa");
    }

    @Test
    void criaComEmailNormalizadoSenhaCifradaEAuditoria() {
        when(encoder.encode(SENHA)).thenReturn("hash");
        when(jdbc.queryForObject(contains("insert into usuario_acesso"), eq(Long.class), any(Object[].class))).thenReturn(20L);
        usuario(20, "OPERADOR", null, null);
        service.criar(new UsuarioService.Novo(" Op@X.com ", " Operador ", SENHA, "OPERADOR", null), 1L);
        verify(jdbc).queryForObject(contains("insert into usuario_acesso"), eq(Long.class), eq("op@x.com"), eq("Operador"), eq("hash"), eq("OPERADOR"), isNull(), isNull(), isNull());
        verify(jdbc).update("insert into usuario_acesso_auditoria(usuario_id,operador_id,acao) values(?,?,?)", 20L, 1L, "CRIACAO");
    }

    @Test
    void emailDuplicadoViraConflito() {
        when(encoder.encode(anyString())).thenReturn("hash");
        when(jdbc.queryForObject(contains("insert into usuario_acesso"), eq(Long.class), any(Object[].class))).thenThrow(new DuplicateKeyException("dup"));
        assertThatThrownBy(() -> service.criar(new UsuarioService.Novo("a@x", "A", SENHA, "DEV", null))).isInstanceOf(ConflitoRevisaoException.class);
    }

    @Test
    void ninguemDesativaOProprioAcessoNemOUltimoAdministrador() {
        usuario(1, "ADMIN", null, null);
        assertThatThrownBy(() -> service.situacao(1, new UsuarioService.Situacao(false, 3L), 1)).hasMessageContaining("proprio acesso");
        when(jdbc.queryForObject(contains("perfil='ADMIN' and ativo"), eq(Long.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.situacao(1, new UsuarioService.Situacao(false, 3L), 2)).hasMessageContaining("pelo menos um administrador");
        verify(jdbc, times(2)).execute("lock table usuario_acesso in share row exclusive mode");
    }

    @Test
    void reativarExigeMedicoEEmpresaAtivosEVersaoAtual() {
        usuario(7, "MEDICO", 5L, null);
        medicoAtivo(5, false);
        assertThatThrownBy(() -> service.situacao(7, new UsuarioService.Situacao(true, 3L), 1)).hasMessageContaining("Medico inativo");
        usuario(8, "EMPRESA", null, 6L);
        empresaAtiva(6, false);
        assertThatThrownBy(() -> service.situacao(8, new UsuarioService.Situacao(true, 3L), 1)).hasMessageContaining("Empresa inativa");
        usuario(9, "OPERADOR", null, null);
        when(jdbc.update(contains("set ativo=?"), eq(false), eq(9L), eq(2L))).thenReturn(0);
        assertThatThrownBy(() -> service.situacao(9, new UsuarioService.Situacao(false, 2L), 1)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.update(contains("set ativo=?"), eq(false), eq(9L), eq(3L))).thenReturn(1);
        service.situacao(9, new UsuarioService.Situacao(false, 3L), 1);
        verify(jdbc).update("insert into usuario_acesso_auditoria(usuario_id,operador_id,acao) values(?,?,?)", 9L, 1L, "DESATIVACAO");
    }

    @Test
    void redefinirSenhaValidaSenhaVersaoEDesbloqueia() {
        usuario(9, "OPERADOR", null, null);
        assertThatThrownBy(() -> service.redefinir(9, new UsuarioService.NovaSenha("curta", 3L))).hasMessageContaining("12 caracteres");
        when(encoder.encode(SENHA)).thenReturn("novo");
        when(jdbc.update(contains("set senha_hash=?"), eq("novo"), eq(9L), eq(2L))).thenReturn(0);
        assertThatThrownBy(() -> service.redefinir(9, new UsuarioService.NovaSenha(SENHA, 2L))).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.update(contains("set senha_hash=?"), eq("novo"), eq(9L), eq(3L))).thenReturn(1);
        service.redefinir(9, new UsuarioService.NovaSenha(SENHA, 3L), 1L);
        verify(jdbc).update("insert into usuario_acesso_auditoria(usuario_id,operador_id,acao) values(?,?,?)", 9L, 1L, "REDEFINICAO_SENHA");
    }

    @Test
    void usuarioInexistenteELoginComFalhasEBloqueio() {
        doReturn(List.of()).when(service).listar();
        doCallRealMethod().when(service).buscar(anyLong());
        assertThatThrownBy(() -> service.buscar(99)).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.query(contains("where u.email=?"), any(RowMapper.class), eq("x@x"))).thenReturn(List.of());
        assertThatThrownBy(() -> service.loadUserByUsername(" X@X ")).isInstanceOf(UsernameNotFoundException.class);
        service.falha(" A@X ");
        verify(jdbc).update(contains("interval '15 minutes'"), eq("a@x"));
        service.sucesso(4);
        verify(jdbc).update("update usuario_acesso set falhas=0,bloqueado_ate=null where id=?", 4L);
    }

    @Test
    void sessaoValidaSoComUsuarioAtivoEMesmaVersao() {
        var sessao = new UsuarioAutenticado(1, "a@x", "A", "h", "ADMIN", null, true, false, 3);
        doThrow(new UsernameNotFoundException("x")).when(service).loadUserByUsername("a@x");
        assertThat(service.sessaoValida(sessao)).isFalse();
        doReturn(new UsuarioAutenticado(1, "a@x", "A", "h", "ADMIN", null, true, false, 4)).when(service).loadUserByUsername("a@x");
        assertThat(service.sessaoValida(sessao)).isFalse(); // senha/situacao mudou: versao nova
        doReturn(new UsuarioAutenticado(1, "a@x", "A", "h", "ADMIN", null, false, false, 3)).when(service).loadUserByUsername("a@x");
        assertThat(service.sessaoValida(sessao)).isFalse(); // desativado
        doReturn(new UsuarioAutenticado(1, "a@x", "A", "h", "ADMIN", null, true, false, 3)).when(service).loadUserByUsername("a@x");
        assertThat(service.sessaoValida(sessao)).isTrue();
    }
}
