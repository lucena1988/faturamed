package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

/** Cadastro de repasse por procedimento: validacoes, complemento das linhas e permissao (sem banco). */
@SuppressWarnings("unchecked")
class RepasseProcedimentoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RepasseProcedimentoService service = new RepasseProcedimentoService(jdbc);

    private static RepasseProcedimentoService.Repasse repasse(long id, String codigo, String tipo, String pct, boolean ativo) {
        return new RepasseProcedimentoService.Repasse(id, null, null, codigo, tipo, pct == null ? null : new BigDecimal(pct),
                pct == null ? new BigDecimal("50") : null, LocalDate.of(2000, 1, 1), null, ativo, "");
    }
    private void cadastro(RepasseProcedimentoService.Repasse... lista) {
        when(jdbc.query(contains("from repasse_procedimento r"), any(RowMapper.class))).thenReturn(List.of(lista));
    }
    private static RepasseProcedimentoService.Dados dados(String codigo, String tipo, String pct, String fixo) {
        return new RepasseProcedimentoService.Dados(null, codigo, tipo, pct == null ? null : new BigDecimal(pct), fixo == null ? null : new BigDecimal(fixo), null, null, null, null);
    }

    @Test
    void validaCodigoOuTipoEPercentualOuValor() {
        assertThatThrownBy(() -> service.salvar(null, dados(null, null, "10", null))).hasMessageContaining("ou o tipo do procedimento");
        assertThatThrownBy(() -> service.salvar(null, dados("1", null, null, null))).hasMessageContaining("percentual ou o valor");
        assertThatThrownBy(() -> service.salvar(null, dados("1", null, "10", "5"))).hasMessageContaining("percentual ou o valor");
        assertThatThrownBy(() -> service.salvar(null, dados("1", null, "101", null))).hasMessageContaining("entre 0 e 100");
        assertThatThrownBy(() -> service.salvar(null, dados("1", null, null, "-1"))).hasMessageContaining("Valor fixo");
        assertThatThrownBy(() -> service.salvar(null, dados("1", "QUALQUER", "10", null))).hasMessageContaining("Tipo de procedimento");
        var vigenciaInvertida = new RepasseProcedimentoService.Dados(null, "1", null, BigDecimal.TEN, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1), true, "");
        assertThatThrownBy(() -> service.salvar(null, vigenciaInvertida)).hasMessageContaining("vigencia");
    }

    @Test
    void criaEAtualiza() {
        when(jdbc.queryForObject(contains("insert into repasse_procedimento"), eq(Long.class), any(Object[].class))).thenReturn(3L);
        cadastro(repasse(3, "40101010", null, "60", true));
        assertThat(service.salvar(null, dados("40101010", null, "60", null)).id()).isEqualTo(3L);
        when(jdbc.update(contains("update repasse_procedimento"), any(Object[].class))).thenReturn(1);
        assertThat(service.salvar(3L, dados("40101010", "EXAME", "60", null)).id()).isEqualTo(3L);
        when(jdbc.update(contains("update repasse_procedimento"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.salvar(99L, dados("1", null, "10", null))).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void complementarSoPreencheLinhasSemRepasseEComCadastroAtivo() {
        var comRepasse = new LeitorPlanilhaVisitas.Linha(2, Map.of("vl. a repassar", "10", "valor tot", "100", "cod produto", "40101010", "produto", "ECG"));
        assertThat(service.complementar(1L, List.of(comRepasse))).containsExactly(comRepasse);
        verify(jdbc, never()).query(anyString(), any(RowMapper.class));

        var semRepasse = new LeitorPlanilhaVisitas.Linha(3, Map.of("valor tot", "200", "cod produto", "40101010", "produto", "ECG", "data consumo", "2026-01-02"));
        cadastro(repasse(1, "40101010", null, "60", false));
        assertThat(service.complementar(1L, List.of(semRepasse))).containsExactly(semRepasse); // inativo nao conta

        cadastro(repasse(2, "40101010", null, "60", true));
        var resultado = service.complementar(1L, List.of(comRepasse, semRepasse));
        assertThat(resultado.get(0)).isSameAs(comRepasse);
        assertThat(resultado.get(1).campo("Vl. A Repassar")).isEqualTo("120.00");
        assertThat(resultado.get(1).campo("origem repasse")).isEqualTo("CADASTRO");
    }

    @Test
    void aplicarIgnoraValorInvalidoESemValorParaPercentual() {
        var invalido = new LeitorPlanilhaVisitas.Linha(2, Map.of("valor tot", "abc", "cod produto", "1"));
        assertThat(RepasseProcedimentoService.aplicar(null, invalido, List.of(repasse(1, "1", null, "50", true)))).isSameAs(invalido);
        var semValor = new LeitorPlanilhaVisitas.Linha(3, Map.of("cod produto", "1", "data consumo", "data ruim"));
        assertThat(RepasseProcedimentoService.aplicar(null, semValor, List.of(repasse(1, "1", null, "50", true)))).isSameAs(semValor);
        // valor fixo nao depende do valor total
        assertThat(RepasseProcedimentoService.aplicar(null, semValor, List.of(repasse(2, "1", null, null, true))).campo("Vl. A Repassar")).isEqualTo("50");
    }

    @Test
    void listaProcedimentosConhecidosComESemHospital() {
        when(jdbc.query(contains("from visita_base_hospitalar"), any(RowMapper.class), any(), any())).thenReturn(List.of());
        assertThat(service.procedimentos(null)).isEmpty();
        assertThat(service.procedimentos(2L)).isEmpty();
        verify(jdbc).query(contains("from visita_base_hospitalar"), any(RowMapper.class), eq(2L), eq(2L));
    }

    @Test
    void somenteAdminOuDevAlteraOCadastro() {
        var s = mock(RepasseProcedimentoService.class);
        var c = new RepasseProcedimentoController(s);
        var operador = new UsuarioAutenticado(1, "o@x", "O", "h", "OPERADOR", null, true, false, 0);
        var admin = new UsuarioAutenticado(2, "a@x", "A", "h", "ADMIN", null, true, false, 0);
        var d = dados("1", null, "10", null);
        assertThatThrownBy(() -> c.criar(d, operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> c.atualizar(1L, d, null)).isInstanceOf(AccessDeniedException.class);
        c.criar(d, admin); verify(s).salvar(null, d);
        c.atualizar(4L, d, admin); verify(s).salvar(4L, d);
        c.listar(); verify(s).listar();
        c.procedimentos(3L); verify(s).procedimentos(3L);
    }
}
