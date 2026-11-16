package br.com.faturamed.procedimento;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

/** Catalogo TUSS/AMB: importacao, busca por codigo/nome e permissao (sem banco). */
class CatalogoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CatalogoService service = new CatalogoService(jdbc);
    private static final String CSV = "id;source;display_name;extras_fim_vigencia;extras_fim_implantacao;extras_inicio_vigencia\n"
            + "\"40101010\";\"tuss-22\";\"ECG convencional\";\"-\";\"2014-08-31\";\"2014-01-01\"\n";

    private void catalogoAtual(Long id) {
        doReturn(id).when(jdbc).query(contains("order by consultado_em desc"), any(ResultSetExtractor.class), eq("TUSS"));
    }

    @Test
    void importarRecusaCodigoRepetidoVigenciaInvertidaEVersaoExistente() {
        var p = new CatalogoController.Procedimento("1", "A", null, null);
        assertThatThrownBy(() -> service.importar(new CatalogoController.ImportarCatalogo(CatalogoController.Tabela.TUSS, "v1", "f", LocalDate.now(), List.of(p, p))))
                .hasMessageContaining("repetido");
        var invertida = new CatalogoController.Procedimento("2", "B", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1));
        assertThatThrownBy(() -> service.importar(new CatalogoController.ImportarCatalogo(CatalogoController.Tabela.TUSS, "v1", "f", LocalDate.now(), List.of(invertida))))
                .hasMessageContaining("Vigencia");
        doReturn(null).when(jdbc).query(contains("insert into catalogo_procedimento"), any(ResultSetExtractor.class), any(), any(), any(), any());
        assertThatThrownBy(() -> service.importar(new CatalogoController.ImportarCatalogo(CatalogoController.Tabela.TUSS, "v1", "f", LocalDate.now(), List.of(p))))
                .hasMessageContaining("ja cadastradas");
    }

    @Test
    void importarPlanilhaLeOArquivoEGravaComFontePadrao() {
        doReturn(9L).when(jdbc).query(contains("insert into catalogo_procedimento"), any(ResultSetExtractor.class), eq("TUSS"), eq("TUSS 22"), eq("Arquivo tuss.csv"), any());
        var r = service.importarPlanilha(CatalogoController.Tabela.TUSS, " TUSS 22 ", " ", "tuss.csv", CSV.getBytes(StandardCharsets.UTF_8));
        assertThat(r).containsEntry("id", 9L).containsEntry("procedimentos", 1).containsEntry("versao", "TUSS 22");
        verify(jdbc).batchUpdate(contains("insert into procedimento_referencia"), anyList(), eq(500), any());
        assertThatThrownBy(() -> service.importarPlanilha(CatalogoController.Tabela.TUSS, " ", null, "tuss.csv", CSV.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("versao");
    }

    @Test
    void buscaSemCatalogoOuTermoCurtoNaoConsulta() {
        catalogoAtual(null);
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, "ecg", null)).isEmpty();
        catalogoAtual(1L);
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, "e", null)).isEmpty();
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, null, null)).isEmpty();
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void buscaPorCodigoPorNomeEPorListaDeCodigos() {
        catalogoAtual(1L);
        when(jdbc.queryForList(contains("codigo like ?"), eq(1L), eq("4010%"))).thenReturn(List.of(Map.of("codigo", "40101010")));
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, "4.01-0", null)).hasSize(1);

        when(jdbc.queryForList(contains("translate(lower(descricao)"), eq(1L), eq("%eco%"), eq("%transtoracico%"))).thenReturn(List.of(Map.of("codigo", "40901777")));
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, "Eco transtorácico", null)).hasSize(1);

        when(jdbc.queryForList(contains("codigo = any(?)"), eq(1L), any(String[].class))).thenReturn(List.of(Map.of("codigo", "10102019")));
        assertThat(service.buscarTexto(CatalogoController.Tabela.TUSS, null, List.of("1.01.02.019"))).hasSize(1);
    }

    @Test
    void buscarPorCatalogoInexistenteDaErro() {
        when(jdbc.queryForObject(contains("from catalogo_procedimento where id"), eq(Long.class), eq(5L))).thenReturn(0L);
        assertThatThrownBy(() -> service.buscar(5L, "1", null)).isInstanceOf(RecursoNaoEncontradoException.class);
        when(jdbc.queryForObject(contains("from catalogo_procedimento where id"), eq(Long.class), eq(6L))).thenReturn(1L);
        when(jdbc.queryForList(anyString(), eq(6L), eq("1"))).thenReturn(List.of());
        assertThat(service.buscar(6L, "1", null)).isEmpty();
        when(jdbc.queryForList(anyString(), eq(6L), eq("1"), any(), any())).thenReturn(List.of());
        assertThat(service.buscar(6L, "1", LocalDate.now())).isEmpty();
    }

    @Test
    void controllerSoDeixaAdminOuDevImportarPlanilha() throws Exception {
        var s = mock(CatalogoService.class);
        var c = new CatalogoController(s);
        var arquivo = new MockMultipartFile("arquivo", "tuss.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
        var operador = new UsuarioAutenticado(1, "o@x", "O", "h", "OPERADOR", null, true, false, 0);
        var dev = new UsuarioAutenticado(2, "d@x", "D", "h", "DEV", null, true, false, 0);
        assertThatThrownBy(() -> c.importarPlanilha(CatalogoController.Tabela.TUSS, "v", null, arquivo, operador)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> c.importarPlanilha(CatalogoController.Tabela.TUSS, "v", null,
                new MockMultipartFile("arquivo", "vazio.csv", "text/csv", new byte[0]), dev)).hasMessageContaining("vazio");
        c.importarPlanilha(CatalogoController.Tabela.TUSS, "v", "ANS", arquivo, dev);
        verify(s).importarPlanilha(eq(CatalogoController.Tabela.TUSS), eq("v"), eq("ANS"), eq("tuss.csv"), any());
        c.buscarTexto(CatalogoController.Tabela.TUSS, "ecg", null); verify(s).buscarTexto(CatalogoController.Tabela.TUSS, "ecg", null);
        c.listar(); verify(s).listar();
    }
}
