package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class FinanceiroMedicoTest {
    @Test void filtraPorVisitaAutorizadaECadastroDoLoginSemExporHistorico() throws Exception {
        var portal=mock(PortalMedicoController.class);var service=mock(RecebimentoService.class);
        var user=new UsuarioAutenticado(1,"ana@test.local","Ana","hash","MEDICO",10L,true,false,0);
        var visita=new PortalMedicoController.Visita(2,"A",null,ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",new BigDecimal("11"));
        when(portal.buscar(1L,user)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));
        var propria=mock(RecebimentoService.Visita.class);when(propria.linha()).thenReturn(2);when(propria.medicoId()).thenReturn(10L);when(propria.recebido()).thenReturn(BigDecimal.TEN);when(propria.saldo()).thenReturn(BigDecimal.ONE);
        var outro=mock(RecebimentoService.Visita.class);when(outro.linha()).thenReturn(3);when(outro.medicoId()).thenReturn(20L);
        when(service.listar(1)).thenReturn(new RecebimentoService.Resultado("H",false,List.of(propria,outro)));
        when(service.movimentosDoMedico(1,10)).thenReturn(List.of(new RecebimentoService.Movimento(1,2,"A",null,"101",java.time.LocalDate.of(2026,1,1),BigDecimal.TEN,null,false),new RecebimentoService.Movimento(2,3,"B",null,"101",java.time.LocalDate.of(2026,1,1),new BigDecimal("999"),null,false)));
        var result=new FinanceiroMedicoController(portal,service).listar(1,user);
        assertThat(result).containsExactly(new FinanceiroMedicoController.Saldo(2,BigDecimal.TEN,new BigDecimal("1.00"),false));
        verify(service,never()).listar(anyLong());verify(service).movimentosDoMedico(1,10);
    }
    @Test void inconsistenciaNaoRevelaDinheiroPossivelmenteAssociadoAOutroMedico() throws Exception {
        var portal=mock(PortalMedicoController.class);var service=mock(RecebimentoService.class);
        var user=new UsuarioAutenticado(1,"ana@test.local","Ana","hash","MEDICO",10L,true,false,0);
        var visita=new PortalMedicoController.Visita(2,"A",null,ConciliadorVisitas.Status.PAGA,"","Ana",10,"Visita","101",new BigDecimal("11"));
        when(portal.buscar(1L,user)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));
        var financeira=mock(RecebimentoService.Visita.class);when(financeira.linha()).thenReturn(2);when(financeira.medicoId()).thenReturn(10L);when(financeira.inconsistente()).thenReturn(true);when(financeira.recebido()).thenReturn(BigDecimal.TEN);
        when(service.listar(1)).thenReturn(new RecebimentoService.Resultado("H",false,List.of(financeira)));
        when(service.movimentosDoMedico(1,10)).thenReturn(List.of(new RecebimentoService.Movimento(1,2,"OUTRO",null,"101",java.time.LocalDate.of(2026,1,1),BigDecimal.TEN,null,false)));
        var result=new FinanceiroMedicoController(portal,service).listar(1,user).getFirst();assertThat(result.recebido()).isNull();assertThat(result.saldo()).isNull();assertThat(result.inconsistente()).isTrue();
    }
}
