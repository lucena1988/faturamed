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
        var visita=mock(PortalMedicoController.Visita.class);when(visita.linhaMedico()).thenReturn(2);
        when(portal.buscar(1L,user)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));
        var propria=mock(RecebimentoService.Visita.class);when(propria.linha()).thenReturn(2);when(propria.medicoId()).thenReturn(10L);when(propria.recebido()).thenReturn(BigDecimal.TEN);when(propria.saldo()).thenReturn(BigDecimal.ONE);
        var outro=mock(RecebimentoService.Visita.class);when(outro.linha()).thenReturn(3);when(outro.medicoId()).thenReturn(20L);
        when(service.listar(1)).thenReturn(new RecebimentoService.Resultado("H",false,List.of(propria,outro)));
        var result=new FinanceiroMedicoController(portal,service).listar(1,user);
        assertThat(result).containsExactly(new FinanceiroMedicoController.Saldo(2,BigDecimal.TEN,BigDecimal.ONE,false));
        when(propria.medicoId()).thenReturn(20L);assertThat(new FinanceiroMedicoController(portal,service).listar(1,user)).isEmpty();
    }
    @Test void inconsistenciaNaoRevelaDinheiroPossivelmenteAssociadoAOutroMedico() throws Exception {
        var portal=mock(PortalMedicoController.class);var service=mock(RecebimentoService.class);
        var user=new UsuarioAutenticado(1,"ana@test.local","Ana","hash","MEDICO",10L,true,false,0);
        var visita=mock(PortalMedicoController.Visita.class);when(visita.linhaMedico()).thenReturn(2);
        when(portal.buscar(1L,user)).thenReturn(new PortalMedicoController.Relatorio(1,"H",List.of(visita)));
        var financeira=mock(RecebimentoService.Visita.class);when(financeira.linha()).thenReturn(2);when(financeira.medicoId()).thenReturn(10L);when(financeira.inconsistente()).thenReturn(true);when(financeira.recebido()).thenReturn(BigDecimal.TEN);
        when(service.listar(1)).thenReturn(new RecebimentoService.Resultado("H",false,List.of(financeira)));
        var result=new FinanceiroMedicoController(portal,service).listar(1,user).getFirst();assertThat(result.recebido()).isNull();assertThat(result.saldo()).isNull();assertThat(result.inconsistente()).isTrue();
    }
}
