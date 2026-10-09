package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PortalMedicoTest {
    @Test void payloadNaoContemOriginalCandidatosPacienteNemOutroMedico() throws Exception {
        var medico=new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of());
        var candidato=new ConciliadorVisitas.RegistroHospital(2,"1",null,"Paciente","Convenio","Outro medico","101","Visita","Conta","Faturado",null,null,BigDecimal.TEN);
        var visita=new ConciliadorVisitas.Visita(2,"1",null,ConciliadorVisitas.Status.DIVERGENTE,"Conferir",Map.of("nome","Paciente","medico","Ana"),null,List.of(candidato));
        String json=new ObjectMapper().writeValueAsString(PortalMedicoController.sanitizar(visita,medico));
        assertThat(json).doesNotContain("Paciente","Outro medico","candidatos","original","hospital");
        assertThat(PortalMedicoController.sanitizar(visita,medico).repasse()).isNull();
    }
    @Test void acessoUsaCadastroDoLoginENaoNomeDaPlanilha() throws Exception {
        var revisoes=mock(RevisaoVisitasService.class);
        var medicos=mock(MedicoCadastroService.class);
        var controller=new PortalMedicoController(null,revisoes,medicos);
        var medico=new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of());
        var user=new UsuarioAutenticado(1,"a@test.local","Ana","hash","MEDICO",10L,true,false,0);
        when(medicos.buscar(10)).thenReturn(medico);
        when(medicos.listar()).thenReturn(List.of(medico));
        // A same-name visit explicitly assigned elsewhere must not be disclosed.
        var visita=new ConciliadorVisitas.Visita(2,"1",null,ConciliadorVisitas.Status.PENDENTE,"Conferir",Map.of("medico","Ana"),null,List.of());
        when(medicos.identificar(visita,List.of(medico))).thenReturn(20L);
        var base=new ConciliadorVisitas.Relatorio("Hospital","a.xlsx","b.xlsx","Regra",List.of(visita),List.of(),Map.of(),BigDecimal.ZERO);
        var estado=mock(RevisaoVisitasService.Estado.class);
        when(estado.atual()).thenReturn(base);when(revisoes.carregar(1L)).thenReturn(estado);
        assertThatThrownBy(()->controller.buscar(1L,user)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void tamanhoDaSenhaRespeitaLimiteEmBytesDoBcrypt() {
        assertThatThrownBy(()->UsuarioService.validarSenha("curta")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UsuarioService.validarSenha("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UsuarioService.validarSenha("\u00e1".repeat(37))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->UsuarioService.validarSenha("SenhaSegura123456")).doesNotThrowAnyException();
    }
}
