package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

class ConsultaPagamentosTest {
    private Map<String,Object> pagamento(){return new HashMap<>(Map.of("id",55L,"relatorio_id",1L,"medico_id",10L,"medico","Ana","referencia","REF-123","data_recebimento",java.sql.Date.valueOf("2026-01-01"),"valor_total",new BigDecimal("100"),"responsavel","Admin","criado_em",java.sql.Timestamp.from(Instant.EPOCH)));}
    private Map<String,Object> evento(long id,int linha,String valor,Long estorno){var row=new HashMap<String,Object>();row.put("id",id);row.put("pagamento_id",55L);row.put("linha_medico",linha);row.put("valor",new BigDecimal(valor));row.put("estorno_de",estorno);row.put("atendimento","A"+linha);row.put("data_visita",java.sql.Date.valueOf("2026-01-01"));row.put("data_recebimento",java.sql.Date.valueOf("2026-02-01"));row.put("responsavel","Admin");row.put("criado_em",java.sql.Timestamp.from(Instant.EPOCH));return row;}
    @Test void somaAlocacoesSemRepetirValorDoCabecalho(){var p=ConsultaPagamentosService.montar(pagamento(),List.of(evento(1,2,"30",null),evento(2,3,"70",null)));
        assertThat(p.valorOriginal()).isEqualByComparingTo("100");assertThat(p.liquido()).isEqualByComparingTo("100");assertThat(p.visitas()).hasSize(2);assertThat(p.situacao()).isEqualTo(ConsultaPagamentosService.Situacao.SEM_ESTORNO);}
    @Test void estornoDeUmaVisitaNaoAlteraOutra(){var p=ConsultaPagamentosService.montar(pagamento(),List.of(evento(1,2,"30",null),evento(2,3,"70",null),evento(3,2,"-30",1L)));
        assertThat(p.estornos()).isEqualByComparingTo("30");assertThat(p.liquido()).isEqualByComparingTo("70");assertThat(p.visitas().getFirst().liquido()).isZero();assertThat(p.visitas().get(1).liquido()).isEqualByComparingTo("70");assertThat(p.historico()).hasSize(3);assertThat(p.situacao()).isEqualTo(ConsultaPagamentosService.Situacao.PARCIALMENTE_ESTORNADO);}
    @Test void estornoIntegralMantemValorOriginalEHistorico(){var p=ConsultaPagamentosService.montar(pagamento(),List.of(evento(1,2,"100",null),evento(2,2,"-100",1L)));
        assertThat(p.valorOriginal()).isEqualByComparingTo("100");assertThat(p.liquido()).isZero();assertThat(p.situacao()).isEqualTo(ConsultaPagamentosService.Situacao.ESTORNADO);assertThat(p.historico()).hasSize(2);}
    @Test void somaIncompativelExigeConferencia(){var p=ConsultaPagamentosService.montar(pagamento(),List.of(evento(1,2,"90",null)));assertThat(p.situacao()).isEqualTo(ConsultaPagamentosService.Situacao.CONFERENCIA);}
    @Test void listaFiltraNaCadeiaAtualSemDuplicarVersoesEImpedeDetalheDeOutroRelatorio() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var revisoes=mock(RevisaoVisitasService.class);var service=new ConsultaPagamentosService(jdbc,revisoes);
        var base=new ConciliadorVisitas.Relatorio("H","p","h","",List.of(),List.of(),Map.of(),BigDecimal.ZERO);when(revisoes.carregar(anyLong())).thenReturn(new RevisaoVisitasService.Estado(base,base,List.of(),0));
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),eq(2L))).thenReturn(1L);
        when(jdbc.queryForList(contains("select p.*"),eq(1L))).thenReturn(List.of(pagamento()));when(jdbc.queryForList(contains("select r.*"),eq(1L))).thenReturn(List.of(evento(1,2,"30",null),evento(2,3,"70",null),evento(3,2,"-30",1L)));
        var r=service.listar(2,"ref-1",10L,LocalDate.of(2026,1,1),LocalDate.of(2026,1,1),ConsultaPagamentosService.Situacao.PARCIALMENTE_ESTORNADO);
        assertThat(r.pagamentos()).hasSize(1);assertThat(r.valorOriginal()).isEqualByComparingTo("100");assertThat(r.liquido()).isEqualByComparingTo("70");
        assertThat(service.detalhar(2,55).relatorioOrigem()).isEqualTo(1);assertThatThrownBy(()->service.detalhar(2,99)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(service.listar(2,"REF",20L,null,null,ConsultaPagamentosService.Situacao.TODOS).pagamentos()).isEmpty();
        assertThat(service.listar(2,"REF",null,LocalDate.of(2026,2,1),null,ConsultaPagamentosService.Situacao.TODOS).pagamentos()).isEmpty();
        assertThatThrownBy(()->service.listar(2,"",null,LocalDate.of(2026,2,1),LocalDate.of(2026,1,1),ConsultaPagamentosService.Situacao.TODOS)).isInstanceOf(IllegalArgumentException.class);
    }
}
