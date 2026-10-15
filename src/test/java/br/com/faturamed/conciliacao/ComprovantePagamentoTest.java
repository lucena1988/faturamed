package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

class ComprovantePagamentoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ComprovantePagamentoService service=new ComprovantePagamentoService(jdbc);
    private final UsuarioAutenticado usuario=new UsuarioAutenticado(5,"admin@test.local","Admin","hash","ADMIN",null,true,false,0);
    private MockMultipartFile pdf(String nome){return new MockMultipartFile("arquivo",nome,"text/plain","%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));}
    private void configurar(){when(jdbc.queryForList(contains("with recursive"),eq(10L),eq(55L))).thenReturn(List.of(Map.of("id",55L)));when(jdbc.queryForList(contains("for update"),eq(10L))).thenReturn(List.of(Map.of("id",10L)));}
    @Test void reconheceAssinaturaNaoConfiaNoMimeERemoveCaminho() throws Exception {
        var arquivo=ComprovantePagamentoService.validar(pdf("C:\\pasta\\comprovante.pdf"));assertThat(arquivo.nome()).isEqualTo("comprovante.pdf");assertThat(arquivo.tipo()).isEqualTo("application/pdf");assertThat(arquivo.sha256()).hasSize(64);
        assertThat(ComprovantePagamentoService.validar(pdf("outro.pdf")).sha256()).isEqualTo(arquivo.sha256());
        assertThat(ComprovantePagamentoService.validar(new MockMultipartFile("arquivo","a.png","application/pdf",new byte[]{(byte)137,80,78,71,13,10,26,10})).tipo()).isEqualTo("image/png");
        assertThat(ComprovantePagamentoService.validar(new MockMultipartFile("arquivo","a.jpeg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,0})).tipo()).isEqualTo("image/jpeg");
    }
    @Test void rejeitaTipoNomeVazioETamanhoIncompativeis() {
        for(var file:List.of(pdf("documento.html"),pdf("nome\r\n.pdf"),new MockMultipartFile("arquivo","falso.pdf","application/pdf","texto".getBytes(StandardCharsets.US_ASCII)),new MockMultipartFile("arquivo",new byte[0])))
            assertThatThrownBy(()->ComprovantePagamentoService.validar(file)).isInstanceOf(IllegalArgumentException.class);
        var grande=mock(MultipartFile.class);when(grande.getSize()).thenReturn((long)ComprovantePagamentoService.LIMITE+1);
        assertThatThrownBy(()->ComprovantePagamentoService.validar(grande)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void anexaComAtorAutenticadoSemAlterarValoresFinanceiros() throws Exception {
        configurar();service.anexar(10,55,pdf("comprovante.pdf"),usuario);var arquivo=ComprovantePagamentoService.validar(pdf("comprovante.pdf"));
        verify(jdbc).update(contains("insert into pagamento_comprovante"),eq(55L),eq("comprovante.pdf"),eq("application/pdf"),eq(arquivo.conteudo().length),eq(arquivo.sha256()),aryEq(arquivo.conteudo()),eq(5L),eq("Admin (admin@test.local)"));
        verify(jdbc,never()).update(contains("recebimento_visita"),any(Object[].class));verify(jdbc,never()).update(contains("update pagamento_visitas"),any(Object[].class));
    }
    private static byte[] aryEq(byte[] bytes){return org.mockito.AdditionalMatchers.aryEq(bytes);}
    @Test void duplicidadeLimiteEVersaoHistoricaBloqueiamUpload() throws Exception {
        configurar();when(jdbc.queryForList(contains("and sha256=?"),eq(55L),anyString())).thenReturn(List.of(Map.of("id",1L)));
        assertThatThrownBy(()->service.anexar(10,55,pdf("x.pdf"),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForList(contains("and sha256=?"),eq(55L),anyString())).thenReturn(List.of());when(jdbc.queryForObject(contains("select count(*)"),eq(Long.class),eq(55L))).thenReturn(10L);
        assertThatThrownBy(()->service.anexar(10,55,pdf("x.pdf"),usuario)).isInstanceOf(IllegalArgumentException.class);
        when(jdbc.queryForList(contains("anterior_id=?"),eq(10L))).thenReturn(List.of(Map.of("id",11L)));
        assertThatThrownBy(()->service.anexar(10,55,pdf("x.pdf"),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void acessoDeOutraCadeiaNaoRevelaArquivo(){assertThatThrownBy(()->service.baixar(10,55,1)).isInstanceOf(RecursoNaoEncontradoException.class);verify(jdbc,never()).queryForList(contains("select nome,tipo,conteudo"),anyLong(),anyLong());}
    @Test void retiradaExigeJustificativaEPreservaBlob(){configurar();when(jdbc.update(contains("update pagamento_comprovante"),any(Object[].class))).thenReturn(1);
        assertThatThrownBy(()->service.retirar(10,55,1,new ComprovantePagamentoService.Retirada(" "),usuario)).isInstanceOf(IllegalArgumentException.class);
        service.retirar(10,55,1,new ComprovantePagamentoService.Retirada("Arquivo incorreto"),usuario);
        verify(jdbc).update(contains("update pagamento_comprovante"),eq("Admin (admin@test.local)"),eq("Arquivo incorreto"),eq(1L),eq(55L));verify(jdbc,never()).update(contains("delete"),any(Object[].class));
    }
    @Test void downloadForcaAnexoSemCache(){var mock=mock(ComprovantePagamentoService.class);when(mock.baixar(10,55,1)).thenReturn(new ComprovantePagamentoService.Arquivo("comprovante.pdf","application/pdf",new byte[]{1,2},"hash"));
        var response=new ComprovantePagamentoController(mock).baixar(10,55,1);assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");assertThat(response.getBody()).containsExactly((byte)1,(byte)2);
    }
}
