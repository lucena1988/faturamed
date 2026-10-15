package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class ConferenciaMedicoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final LiberacaoRelatorioService liberacoes=mock(LiberacaoRelatorioService.class);
    private final ConferenciaMedicoService service=new ConferenciaMedicoService(jdbc,liberacoes);
    private final UsuarioAutenticado medico=new UsuarioAutenticado(7,"medico@test.local","Ana","hash","MEDICO",10L,true,false,0);
    private final UsuarioAutenticado admin=new UsuarioAutenticado(8,"admin@test.local","Admin","hash","ADMIN",null,true,false,0);
    @BeforeEach void configurar() throws Exception {
        when(jdbc.queryForList(contains("for update of r"),eq(9L))).thenReturn(List.of(Map.of("id",1L)));
        when(jdbc.queryForObject(contains("select l.id"),eq(Long.class),eq(1L),eq("10"))).thenReturn(9L);
        var visita=new PortalMedicoController.Visita(2,"A",null,ConciliadorVisitas.Status.PENDENTE,"Motivo","Ana",10L,"Visita","101",BigDecimal.TEN);
        when(liberacoes.buscarDoMedico(1,10)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));
        when(jdbc.update(contains("insert into solicitacao_medico"),eq(9L),eq(10L),eq(2),anyString(),eq(7L))).thenReturn(1);
    }
    @Test void cienciaUsaCadastroESessaoEEscritaIdempotente() throws Exception {
        service.ciencia(1,new ConferenciaMedicoService.Ciencia(9),medico);
        verify(jdbc).update(contains("on conflict do nothing"),eq(9L),eq(10L),eq(7L));verify(liberacoes).buscarDoMedico(1,10);
    }
    @Test void pedidoPreservaPublicacaoLinhaMotivoEAtor() throws Exception {
        service.solicitar(1,new ConferenciaMedicoService.Pedido(9,2," Conferir visita "),medico);
        verify(jdbc).update(contains("insert into solicitacao_medico"),eq(9L),eq(10L),eq(2),eq("Conferir visita"),eq(7L));
    }
    @Test void visitaDeOutroMedicoNaoPodeSerSolicitada() {
        assertThatThrownBy(()->service.solicitar(1,new ConferenciaMedicoService.Pedido(9,88,"Conferir visita"),medico)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void publicacaoTrocadaBloqueiaCienciaEPedido(){
        when(jdbc.queryForObject(contains("select l.id"),eq(Long.class),eq(1L),eq("10"))).thenReturn(11L);
        assertThatThrownBy(()->service.ciencia(1,new ConferenciaMedicoService.Ciencia(9),medico)).isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(()->service.solicitar(1,new ConferenciaMedicoService.Pedido(9,2,"Conferir visita"),medico)).isInstanceOf(ConflitoRevisaoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void rascunhoOuRelatorioNaoAutorizadoNaoPermiteEscrita() throws Exception {
        when(liberacoes.buscarDoMedico(1,10)).thenThrow(new RecursoNaoEncontradoException("Nao encontrado"));
        assertThatThrownBy(()->service.ciencia(1,new ConferenciaMedicoService.Ciencia(9),medico)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void pedidoDuplicadoRetornaConflito() {
        when(jdbc.update(contains("insert into solicitacao_medico"),eq(9L),eq(10L),eq(2),anyString(),eq(7L))).thenReturn(0);
        assertThatThrownBy(()->service.solicitar(1,new ConferenciaMedicoService.Pedido(9,2,"Conferir visita"),medico)).isInstanceOf(ConflitoRevisaoException.class);
    }
    @Test void respostaExigeTextoEPreservaAtorSemAlterarConciliacao(){
        when(jdbc.queryForList(contains("from solicitacao_medico where id=? for update"),eq(3L))).thenReturn(List.of(Map.of("id",3L)));
        service.responder(3,new ConferenciaMedicoService.Resposta(" Verificado no hospital "),admin);
        verify(jdbc).update(contains("update solicitacao_medico"),eq("Verificado no hospital"),eq(8L),eq(3L));verifyNoInteractions(liberacoes);
        assertThatThrownBy(()->service.responder(3,new ConferenciaMedicoService.Resposta("    "),admin)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void respostaNaoPodeSerSobrescrita(){
        when(jdbc.queryForList(contains("from solicitacao_medico where id=? for update"),eq(3L))).thenReturn(List.of(Map.of("id",3L,"resposta","Ja respondida")));
        assertThatThrownBy(()->service.responder(3,new ConferenciaMedicoService.Resposta("Nova resposta"),admin)).isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(()->service.responder(404,new ConferenciaMedicoService.Resposta("Nova resposta"),admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void rejeitaFiltrosETextosInvalidosSemGravar(){
        assertThatThrownBy(()->service.listar("INVALIDO")).isInstanceOf(IllegalArgumentException.class);
        for(String motivo:Arrays.asList(null,"    ","a".repeat(2001)))assertThatThrownBy(()->service.solicitar(1,new ConferenciaMedicoService.Pedido(9,2,motivo),medico)).isInstanceOf(IllegalArgumentException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
