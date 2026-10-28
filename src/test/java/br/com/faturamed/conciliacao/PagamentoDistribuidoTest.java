package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.faturamed.seguranca.UsuarioAutenticado;

class PagamentoDistribuidoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final RecebimentoService service=spy(new RecebimentoService(jdbc,mock(RevisaoVisitasService.class),mock(MedicoCadastroService.class)));
    private final UsuarioAutenticado usuario=new UsuarioAutenticado(5,"a@test.local","Admin","hash","ADMIN",null,true,false,0);
    private final LocalDate data=LocalDate.of(2026,1,1);
    private RecebimentoService.Visita visita(int linha,long medico,String saldo,long versao) {
        return new RecebimentoService.Visita(linha,"A"+linha,data,medico,"Ana","101","PAGA",new BigDecimal(saldo),BigDecimal.ZERO,new BigDecimal(saldo),versao,true,false,List.of());
    }
    private void configurar(RecebimentoService.Visita... visitas) throws Exception {
        doReturn(new RecebimentoService.Resultado("H",false,List.of(visitas))).when(service).listar(10);
        when(jdbc.queryForList(contains("for update"),eq(10L))).thenReturn(List.of(Map.of("id",10L)));
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),eq(10L))).thenReturn(1L);
        when(jdbc.queryForObject(contains("insert into pagamento_visitas"),eq(Long.class),any(Object[].class))).thenReturn(55L);
    }
    private PagamentoDistribuidoRequest dados(String total,PagamentoDistribuidoRequest.Alocacao... visitas) {
        return new PagamentoDistribuidoRequest(10L,data,new BigDecimal(total)," REF ","Pagamento parcial",List.of(visitas));
    }
    private PagamentoDistribuidoRequest.Alocacao alocacao(int linha,long versao,String valor) {return new PagamentoDistribuidoRequest.Alocacao(linha,versao,new BigDecimal(valor));}
    @Test void gravaPagamentoUnicoComVinculosIndependentesEAtorDoLogin() throws Exception {
        configurar(visita(2,10,"10",0),visita(3,10,"20",2));
        var r=service.distribuir(10,dados("15",alocacao(2,0,"5"),alocacao(3,2,"10")),usuario);
        assertThat(r.pagamentoId()).isEqualTo(55);assertThat(r.visitas()).isEqualTo(2);assertThat(r.valorTotal()).isEqualByComparingTo("15");
        verify(jdbc).queryForObject(contains("insert into pagamento_visitas"),eq(Long.class),eq(1L),eq(10L),eq(10L),eq(data),eq(new BigDecimal("15")),eq("REF"),eq("Pagamento parcial"),eq("Admin (a@test.local)"),eq(5L));
        verify(jdbc).update(contains("insert into recebimento_visita"),eq(1L),eq(10L),eq(2),eq(10L),eq(new BigDecimal("5")),eq(data),eq("Pagamento parcial"),eq("Admin (a@test.local)"),eq(5L),isNull(),eq("A2"),eq(data),eq("101"),eq(55L));
        verify(jdbc).update(contains("insert into recebimento_visita"),eq(1L),eq(10L),eq(3),eq(10L),eq(new BigDecimal("10")),eq(data),eq("Pagamento parcial"),eq("Admin (a@test.local)"),eq(5L),isNull(),eq("A3"),eq(data),eq("101"),eq(55L));
    }
    @Test void versaoDesatualizadaDeUltimaVisitaBloqueiaTodoPagamento() throws Exception {
        configurar(visita(2,10,"10",0),visita(3,10,"20",2));
        assertThatThrownBy(()->service.distribuir(10,dados("15",alocacao(2,0,"5"),alocacao(3,0,"10")),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).queryForObject(contains("insert into pagamento_visitas"),eq(Long.class),any(Object[].class));verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void misturaDeMedicosESaldoExcedidoNaoGravamNemCabecalho() throws Exception {
        configurar(visita(2,10,"10",0),visita(3,11,"20",0));
        assertThatThrownBy(()->service.distribuir(10,dados("15",alocacao(2,0,"5"),alocacao(3,0,"10")),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        configurar(visita(2,10,"10",0),visita(3,10,"9",0));
        assertThatThrownBy(()->service.distribuir(10,dados("15",alocacao(2,0,"5"),alocacao(3,0,"10")),usuario)).isInstanceOf(IllegalArgumentException.class);
        verify(jdbc,never()).queryForObject(contains("insert into pagamento_visitas"),eq(Long.class),any(Object[].class));verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void referenciaDuplicadaEVersaoHistoricaSaoBloqueadas() throws Exception {
        configurar(visita(2,10,"10",0));when(jdbc.queryForList(contains("referencia_chave"),eq(1L),eq(10L),eq("REF"))).thenReturn(List.of(Map.of("id",55L)));
        assertThatThrownBy(()->service.distribuir(10,dados("5",alocacao(2,0,"5")),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        when(jdbc.queryForList(contains("anterior_id=?"),eq(10L))).thenReturn(List.of(Map.of("id",11L)));
        assertThatThrownBy(()->service.distribuir(10,dados("5",alocacao(2,0,"5")),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void exigeDistribuicaoExataPositivaESemLinhasRepetidas() {
        dados("0.30",alocacao(2,0,"0.10"),alocacao(3,0,"0.20")).validar(data);
        for(var request:List.of(dados("1",alocacao(2,0,"0.10")),dados("1",alocacao(2,0,"0"),alocacao(3,0,"1")),dados("1",alocacao(2,0,"0.5"),alocacao(2,0,"0.5")),dados("1",alocacao(2,0,"1.001")),dados("1")))
            assertThatThrownBy(()->request.validar(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validaLimiteDatasEReferencia() {
        var linhas=java.util.stream.IntStream.rangeClosed(1,501).mapToObj(i->alocacao(i,0,"1")).toArray(PagamentoDistribuidoRequest.Alocacao[]::new);
        assertThatThrownBy(()->dados("501",linhas).validar(data)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PagamentoDistribuidoRequest(10L,data.plusDays(1),BigDecimal.ONE,"REF","",List.of(alocacao(2,0,"1"))).validar(data)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PagamentoDistribuidoRequest(10L,data,BigDecimal.ONE,"  ","",List.of(alocacao(2,0,"1"))).validar(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void revisaoConcorrenteBloqueiaPagamentoMesmoSemNovosRecebimentos() throws Exception {
        var visita=visita(2,10,"10",0);configurar(visita);
        doReturn(new RecebimentoService.Resultado("H",false,List.of(visita),5)).when(service).listar(10);
        assertThatThrownBy(()->service.distribuir(10,dados("5",alocacao(2,0,"5")),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).queryForObject(contains("insert into pagamento_visitas"),eq(Long.class),any(Object[].class));verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
