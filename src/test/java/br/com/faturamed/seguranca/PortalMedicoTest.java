package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

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
        var liberacoes=mock(LiberacaoRelatorioService.class);
        var controller=new PortalMedicoController(liberacoes);
        var user=new UsuarioAutenticado(1,"a@test.local","Ana","hash","MEDICO",10L,true,false,0);
        when(liberacoes.buscarDoMedico(1L,10L)).thenThrow(new br.com.faturamed.shared.RecursoNaoEncontradoException("Nao liberado"));
        assertThatThrownBy(()->controller.buscar(1L,user)).isInstanceOf(br.com.faturamed.shared.RecursoNaoEncontradoException.class);
        verify(liberacoes).buscarDoMedico(1L,10L);
    }
    @Test void tamanhoDaSenhaRespeitaLimiteEmBytesDoBcrypt() {
        assertThatThrownBy(()->UsuarioService.validarSenha("curta")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UsuarioService.validarSenha("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UsuarioService.validarSenha("\u00e1".repeat(37))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->UsuarioService.validarSenha("SenhaSegura123456")).doesNotThrowAnyException();
    }
}
