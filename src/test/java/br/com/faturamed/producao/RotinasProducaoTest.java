package br.com.faturamed.producao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Rotinas agendadas da producao: criptografia de legado, descarte de fotos e lembrete de prazo (sem banco). */
class RotinasProducaoTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CriptografiaDados cripto = mock(CriptografiaDados.class);
    private final HistoricoProducao historico = mock(HistoricoProducao.class);

    @Test
    void semChaveNaoCriptografaNada() {
        new ProtecaoDadosRotinas(jdbc, cripto, historico, 90).criptografarRegistrosAntigos();
        verifyNoInteractions(jdbc);
    }

    @Test
    void criptografaEtiquetasEFotosEmClaro() {
        when(cripto.ativa()).thenReturn(true);
        Map<String, Object> item = new HashMap<>(Map.of("id", 1L, "carteirinha", "c", "guia", "g", "senha", "s"));
        when(jdbc.queryForList(contains("from producao_item"))).thenReturn(List.of(item));
        when(cripto.cifrar(anyString())).thenAnswer(a -> "enc:" + a.getArgument(0));
        when(jdbc.queryForList(contains("from producao_lote where foto is not null"), eq(Long.class))).thenReturn(List.of(5L, 6L, 7L));
        when(jdbc.queryForObject(contains("select foto"), eq(byte[].class), eq(5L))).thenReturn(new byte[]{1, 2, 3});
        when(jdbc.queryForObject(contains("select foto"), eq(byte[].class), eq(6L))).thenReturn(null);
        when(cripto.cifrarArquivo(any())).thenReturn(new byte[]{9});
        new ProtecaoDadosRotinas(jdbc, cripto, historico, 90).criptografarRegistrosAntigos();
        verify(jdbc).update(contains("update producao_item"), eq("enc:c"), eq("enc:g"), eq("enc:s"), eq(1L));
        verify(jdbc).update(contains("update producao_lote set foto=?"), any(byte[].class), eq(5L));
        verify(jdbc, never()).update(contains("update producao_lote set foto=?"), any(), eq(6L));
    }

    @Test
    void descartaFotosVencidasERegistraHistorico() {
        var r = new ProtecaoDadosRotinas(jdbc, cripto, historico, 0);
        when(jdbc.queryForList(contains("l.foto is not null"), eq(Long.class), eq(1))).thenReturn(List.of(3L), List.of());
        assertThat(r.descartarFotosVencidas()).isEqualTo(1);
        verify(jdbc).update(contains("foto=null"), eq(3L));
        verify(historico).lote(eq(3L), eq("FOTO_DESCARTADA"), contains("1 dias"), eq("sistema"));
        assertThat(r.descartarFotosVencidas()).isZero();
    }

    @Test
    void lembraSoQuemEstaPertoDoPrazo() {
        var aviso = mock(AvisoHospitalEmail.class);
        var p = new PrazoConferencia(jdbc, aviso, 0);
        assertThat(p.dias()).isEqualTo(1);
        assertThat(p.calcular(LocalDate.of(2026, 11, 6))).isEqualTo(LocalDate.of(2026, 11, 9));
        var hoje = LocalDate.now();
        when(jdbc.queryForList(contains("lembrete_prazo_em is null"))).thenReturn(List.of(
                Map.of("id", 1L, "prazo_conferencia", java.sql.Date.valueOf(hoje)),
                Map.of("id", 2L, "prazo_conferencia", java.sql.Date.valueOf(hoje.plusDays(30)))));
        p.enviarLembretes();
        verify(aviso).lembretePrazo(1L, hoje);
        verify(aviso, never()).lembretePrazo(eq(2L), any());
        verify(jdbc).update(contains("lembrete_prazo_em=now()"), eq(1L));
        assertThat(PrazoConferencia.diasUteisAte(hoje, null)).isEqualTo(Integer.MAX_VALUE);
    }
}
