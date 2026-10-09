package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;

class MedicoCadastroServiceTest {
    private final MedicoCadastroService service=new MedicoCadastroService(null);
    private ConciliadorVisitas.Visita visita(String nome, Long id) {
        return new ConciliadorVisitas.Visita(2,"123",null,ConciliadorVisitas.Status.PENDENTE,"Conferir",Map.of("medico",nome),null,List.of(),
                id==null?null:new ConciliadorVisitas.CamposRevisados(nome,"Visita","10102019",null,null,id));
    }
    private MedicoCadastroService.Medico medico(long id,String nome,boolean ativo,List<String> aliases) {
        return new MedicoCadastroService.Medico(id,nome,"123","SP",ativo,0,aliases);
    }
    @Test void reconheceAliasUnicoSemAlterarNomeOriginal() {
        var v=visita(" DR. JOSE TESTE ",null);
        assertThat(service.identificar(v,List.of(medico(1,"Jose Teste",true,List.of("Dr. Jose Teste"))))).isEqualTo(1L);
        assertThat(v.medicoEfetivo()).isEqualTo(" DR. JOSE TESTE ");
    }
    @Test void homonimosNaoSaoIdentificadosAutomaticamente() {
        assertThat(service.identificar(visita("Medico Teste",null),List.of(medico(1,"Medico Teste",true,List.of()),medico(2,"Medico Teste",true,List.of())))).isNull();
    }
    @Test void aliasColidindoComNomeTambemEAmibiguo() {
        var medicos=List.of(medico(1,"Medico Teste",true,List.of()),medico(2,"Outro",true,List.of("Medico Teste")));
        assertThat(service.identificar(visita("Medico Teste",null),medicos)).isNull();
    }
    @Test void naoVinculaInativoAutomaticamenteMasPreservaVinculoExplicito() {
        var lista=List.of(medico(1,"Medico Teste",false,List.of()));
        assertThat(service.identificar(visita("Medico Teste",null),lista)).isNull();
        assertThat(service.identificar(visita("Medico Teste",1L),lista)).isEqualTo(1L);
    }
}
