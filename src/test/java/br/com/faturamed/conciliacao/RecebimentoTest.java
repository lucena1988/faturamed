package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.faturamed.seguranca.UsuarioAutenticado;

class RecebimentoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final RevisaoVisitasService revisoes=mock(RevisaoVisitasService.class);
    private final MedicoCadastroService medicos=mock(MedicoCadastroService.class);
    private final RecebimentoService service=new RecebimentoService(jdbc,revisoes,medicos);
    private final UsuarioAutenticado usuario=new UsuarioAutenticado(5,"admin@test.local","Admin","hash","ADMIN",null,true,false,0);
    private void configurar(ConciliadorVisitas.Status status) throws Exception {
        var visita=new ConciliadorVisitas.Visita(2,"A",LocalDate.of(2026,1,1),status,"",Map.of(),null,List.of(),new ConciliadorVisitas.CamposRevisados("Ana","Visita","101",new BigDecimal("110"),new BigDecimal("100.005"),10L));
        var base=new ConciliadorVisitas.Relatorio("H","p","h","",List.of(visita),List.of(),Map.of(),BigDecimal.ZERO);
        when(revisoes.carregar(1L)).thenReturn(new RevisaoVisitasService.Estado(base,base,List.of(),0));
        when(medicos.identificar(eq(visita),anyList())).thenReturn(10L);
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),eq(1L))).thenReturn(1L);
        when(jdbc.queryForList(contains("for update"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));
    }
    private Map<String,Object> evento(long id,String valor,Long estorno,long medico) {
        Map<String,Object> r=new HashMap<>();r.put("atendimento","A");r.put("data_visita",java.sql.Date.valueOf("2026-01-01"));r.put("codigo_procedimento","101");r.put("id",id);r.put("linha_medico",2);r.put("medico_id",medico);r.put("valor",new BigDecimal(valor));r.put("estorno_de",estorno);r.put("data_recebimento",java.sql.Date.valueOf("2026-01-01"));r.put("observacoes","Teste");r.put("responsavel","Admin");r.put("criado_em",java.sql.Timestamp.from(Instant.EPOCH));return r;
    }
    private RecebimentoService.Dados dados(String valor,long versao) { return new RecebimentoService.Dados(new BigDecimal(valor),LocalDate.of(2026,1,1),"Parcial",versao); }
    @Test void somaParciaisArredondaRepasseEAplicaEstorno() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);
        when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(evento(1,"40.00",null,10),evento(2,"20.00",null,10),evento(3,"-40.00",1L,10)));
        var v=service.listar(1).visitas().getFirst();assertThat(v.faturado()).isEqualByComparingTo("100.01");assertThat(v.recebido()).isEqualByComparingTo("20");assertThat(v.saldo()).isEqualByComparingTo("80.01");assertThat(v.historico().getFirst().estornado()).isTrue();assertThat(v.versao()).isEqualTo(3);
    }
    @Test void registraAtorDoLoginSemAlterarConciliacao() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);service.registrar(1,2,dados("40.00",0),usuario);
        verify(jdbc).update(contains("insert into recebimento_visita"),eq(1L),eq(1L),eq(2),eq(10L),eq(new BigDecimal("40.00")),eq(LocalDate.of(2026,1,1)),eq("Parcial"),eq("Admin (admin@test.local)"),eq(5L),isNull(),eq("A"),eq(LocalDate.of(2026,1,1)),eq("101"));
        verify(jdbc,never()).update(contains("update conciliacao_visitas"),any(Object[].class));
    }
    @Test void bloqueiaExcessoESnapshotDesatualizado() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);
        when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(evento(1,"40.00",null,10)));
        assertThatThrownBy(()->service.registrar(1,2,dados("70",1),usuario)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.registrar(1,2,dados("10",0),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void pendenciaEAlteracaoDeMedicoNaoRecebemPagamento() throws Exception {
        configurar(ConciliadorVisitas.Status.PENDENTE);assertThat(service.listar(1).visitas().getFirst().saldo()).isNull();
        assertThatThrownBy(()->service.registrar(1,2,dados("1",0),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        configurar(ConciliadorVisitas.Status.PAGA);when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(evento(1,"10.00",null,20)));
        assertThat(service.listar(1).visitas().getFirst().inconsistente()).isTrue();
        assertThatThrownBy(()->service.registrar(1,2,dados("1",1),usuario)).isInstanceOf(ConflitoRevisaoException.class);
    }
    @Test void estornoTemJustificativaEEventoIndependente() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(evento(1,"40.00",null,10)));when(jdbc.queryForObject(contains("select medico_id"),eq(Long.class),eq(1L))).thenReturn(10L);
        service.estornar(1,2,1,new RecebimentoService.Estorno("Erro de lancamento",1L),usuario);
        verify(jdbc).update(contains("insert into recebimento_visita"),eq(1L),eq(1L),eq(2),eq(10L),eq(new BigDecimal("-40.00")),any(LocalDate.class),eq("Erro de lancamento"),eq("Admin (admin@test.local)"),eq(5L),eq(1L),eq("A"),eq(LocalDate.of(2026,1,1)),eq("101"));
    }
    @Test void versaoHistoricaEDatasValoresInvalidosSaoBloqueados() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);when(jdbc.queryForList(contains("anterior_id=?"),eq(1L))).thenReturn(List.of(Map.of("id",2L)));
        assertThatThrownBy(()->service.registrar(1,2,dados("1",0),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        var hoje=LocalDate.of(2026,10,14);
        for(String valor:List.of("0","-1","1.001"))assertThatThrownBy(()->RecebimentoService.validar(dados(valor,0),hoje)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->RecebimentoService.validar(new RecebimentoService.Dados(BigDecimal.ONE,hoje.plusDays(1),"",0L),hoje)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void atendimentoDataOuCodigoAlteradoExigeConferenciaFinanceira() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);
        for(String campo:List.of("atendimento","data_visita","codigo_procedimento")) {
            var row=evento(1,"40.00",null,10);row.put(campo,campo.equals("data_visita")?java.sql.Date.valueOf("2026-01-02"):"Outra identidade");
            when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(row));
            assertThat(service.listar(1).visitas().getFirst().inconsistente()).isTrue();
            assertThatThrownBy(()->service.registrar(1,2,dados("1",1),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        }
    }
    @Test void estornoMantemIdentificacaoDoPagamentoDistribuido() throws Exception {
        configurar(ConciliadorVisitas.Status.PAGA);var row=evento(1,"40.00",null,10);row.put("pagamento_id",55L);
        when(jdbc.queryForList(contains("select * from recebimento_visita"),eq(1L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("select id,referencia from pagamento_visitas"),eq(1L))).thenReturn(List.of(Map.of("id",55L,"referencia","REF")));
        when(jdbc.queryForObject(contains("select medico_id"),eq(Long.class),eq(1L))).thenReturn(10L);
        assertThat(service.listar(1).visitas().getFirst().historico().getFirst().referencia()).isEqualTo("REF");
        service.estornar(1,2,1,new RecebimentoService.Estorno("Correcao",1L),usuario);
        verify(jdbc).update(contains("insert into recebimento_visita"),eq(1L),eq(1L),eq(2),eq(10L),eq(new BigDecimal("-40.00")),any(LocalDate.class),eq("Correcao"),eq("Admin (admin@test.local)"),eq(5L),eq(1L),eq("A"),eq(LocalDate.of(2026,1,1)),eq("101"),eq(55L));
    }
}
