package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.faturamed.seguranca.UsuarioAutenticado;

class AcompanhamentoCobrancaTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final RevisaoVisitasService revisoes=mock(RevisaoVisitasService.class);
    private final AcompanhamentoCobrancaService service=new AcompanhamentoCobrancaService(jdbc,revisoes);
    private final UsuarioAutenticado usuario=new UsuarioAutenticado(5,"a@test.local","Admin","hash","ADMIN",null,true,false,0);
    private RevisaoVisitasService.Estado estado(boolean pago) {
        var producao=List.of(new LeitorPlanilhaVisitas.Linha(2,Map.of("atendimento","A","medico","Ana")),new LeitorPlanilhaVisitas.Linha(3,Map.of("atendimento","A","medico","Ana")));
        var r=new ConciliadorVisitas().conciliar("H","p","h",producao,List.of());
        if(pago){var v=r.visitas().getFirst();var nova=new ConciliadorVisitas.Visita(2,v.atendimento(),v.data(),ConciliadorVisitas.Status.PAGA,v.motivo(),v.original(),null,List.of());
            r=new ConciliadorVisitas.Relatorio(r.hospital(),r.arquivoMedico(),r.arquivoHospital(),r.regraStatus(),List.of(nova,r.visitas().get(1)),List.of(),r.resumo(),r.repasseCorrespondente());}
        return new RevisaoVisitasService.Estado(r,r,List.of(),8);
    }
    private void configurar(boolean pago) throws Exception {
        when(revisoes.carregar(anyLong())).thenReturn(estado(pago));
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),anyLong())).thenReturn(1L);
        when(jdbc.queryForList(contains("for update"),eq(10L))).thenReturn(List.of(Map.of("id",10L)));
    }
    private Map<String,Object> contato(long versao) {
        Map<String,Object> row=new HashMap<>();row.put("id",versao);row.put("linha_medico",2);row.put("situacao","ENVIADA");row.put("data_cobranca",java.sql.Date.valueOf("2026-01-01"));
        row.put("resposta","");row.put("observacoes","Contato inicial");row.put("proximo_contato",java.sql.Date.valueOf("2026-01-05"));row.put("responsavel","Admin (a@test.local)");row.put("criado_em",java.sql.Timestamp.from(Instant.parse("2026-01-01T12:00:00Z")));return row;
    }
    private ContatoCobrancaRequest request(long versao) { return new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.ENVIADA,LocalDate.of(2026,1,1),null,"Contato inicial",LocalDate.of(2026,1,5),versao); }
    @Test void gravaContatoComUsuarioAutenticadoSemAlterarConciliacao() throws Exception {
        configurar(false);
        when(jdbc.queryForList(contains("order by id desc"),eq(1L),eq(2))).thenReturn(List.of(),List.of(contato(12)));
        var result=service.registrar(10,2,request(0),usuario);
        assertThat(result.atual().versao()).isEqualTo(12);assertThat(result.atual().responsavel()).isEqualTo("Admin (a@test.local)");
        verify(jdbc).update(contains("insert into cobranca_contato"),eq(1L),eq(10L),eq(2),eq("ENVIADA"),eq(LocalDate.of(2026,1,1)),eq(""),eq("Contato inicial"),eq(LocalDate.of(2026,1,5)),eq("Admin (a@test.local)"),eq(5L));
        verify(jdbc,never()).update(contains("update conciliacao_visitas"),any(Object[].class));
    }
    @Test void versoesCompartilhamHistoricoMasVisitasRepetidasNao() throws Exception {
        configurar(false);
        when(jdbc.queryForList(contains("order by id desc"),eq(1L),eq(2))).thenReturn(List.of(contato(12)));
        assertThat(service.detalhar(10,2).historico()).hasSize(1);assertThat(service.detalhar(11,2).atual().versao()).isEqualTo(12);
        assertThat(service.detalhar(11,3).historico()).isEmpty();assertThat(service.detalhar(11,3).atual().versao()).isZero();
    }
    @Test void rejeitaEdicaoConcorrente() throws Exception {
        configurar(false);when(jdbc.queryForList(contains("order by id desc"),eq(1L),eq(2))).thenReturn(List.of(contato(12)));
        assertThatThrownBy(()->service.registrar(10,2,request(0),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void visitaResolvidaMantemHistoricoSomenteParaConsulta() throws Exception {
        configurar(true);when(jdbc.queryForList(contains("order by id desc"),eq(1L),eq(2))).thenReturn(List.of(contato(12)));
        assertThat(service.detalhar(10,2).podeEditar()).isFalse();assertThat(service.detalhar(10,2).historico()).hasSize(1);
        assertThatThrownBy(()->service.registrar(10,2,request(12),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void versaoHistoricaNaoRecebeNovosContatos() throws Exception {
        configurar(false);when(jdbc.queryForList(contains("anterior_id=?"),eq(10L))).thenReturn(List.of(Map.of("id",11L)));
        assertThatThrownBy(()->service.registrar(10,2,request(0),usuario)).isInstanceOf(ConflitoRevisaoException.class);
    }
    @Test void validaDatasEResposta() {
        var hoje=LocalDate.of(2026,10,14);
        assertThatThrownBy(()->new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.ENVIADA,null,"","",null,0L).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.ENVIADA,hoje.plusDays(1),"","",null,0L).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.ENVIADA,hoje,"","",hoje.minusDays(1),0L).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.RETORNO_RECEBIDO,hoje," ","",null,0L).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        new ContatoCobrancaRequest(ContatoCobrancaRequest.Situacao.NAO_COBRADA,null,"","Planejar contato",hoje,0L).validar(hoje);
    }
    private CobrancaLoteRequest lote(CobrancaLoteRequest.Selecao... visitas) {
        return new CobrancaLoteRequest(List.of(visitas),ContatoCobrancaRequest.Situacao.ENVIADA,LocalDate.of(2026,1,1),null,"Lote",null);
    }
    @Test void loteGravaEventoIndependenteParaCadaVisita() throws Exception {
        configurar(false);
        assertThat(service.registrarLote(10,lote(new CobrancaLoteRequest.Selecao(2,0L),new CobrancaLoteRequest.Selecao(3,0L)),usuario).registradas()).isEqualTo(2);
        verify(jdbc,times(2)).update(contains("insert into cobranca_contato"),eq(1L),eq(10L),anyInt(),eq("ENVIADA"),eq(LocalDate.of(2026,1,1)),eq(""),eq("Lote"),isNull(),eq("Admin (a@test.local)"),eq(5L));
    }
    @Test void ultimaVisitaDesatualizadaBloqueiaTodoLote() throws Exception {
        configurar(false);
        when(jdbc.queryForList(contains("distinct on (linha_medico)"),eq(1L))).thenReturn(List.of(Map.of("linha_medico",3,"id",12L)));
        assertThatThrownBy(()->service.registrarLote(10,lote(new CobrancaLoteRequest.Selecao(2,0L),new CobrancaLoteRequest.Selecao(3,0L)),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void visitaForaDaFilaBloqueiaTodoLote() throws Exception {
        configurar(true);
        assertThatThrownBy(()->service.registrarLote(10,lote(new CobrancaLoteRequest.Selecao(3,0L),new CobrancaLoteRequest.Selecao(2,0L)),usuario)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void loteRejeitaSelecaoVaziaDuplicadaOuAcimaDoLimite() {
        var hoje=LocalDate.of(2026,10,14);
        assertThatThrownBy(()->lote().validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->lote(new CobrancaLoteRequest.Selecao(2,0L),new CobrancaLoteRequest.Selecao(2,0L)).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
        var linhas=java.util.stream.IntStream.rangeClosed(1,501).mapToObj(i->new CobrancaLoteRequest.Selecao(i,0L)).toArray(CobrancaLoteRequest.Selecao[]::new);
        assertThatThrownBy(()->lote(linhas).validar(hoje)).isInstanceOf(IllegalArgumentException.class);
    }
}
