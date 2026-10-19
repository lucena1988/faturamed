package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class IdentificacaoProducaoTest {
    private final ConciliadorVisitas motor=new ConciliadorVisitas();
    private final MedicoCadastroService medicos=spy(new MedicoCadastroService(null));
    private final IdentificacaoProducaoService service=new IdentificacaoProducaoService(motor,medicos);
    private final MedicoCadastroService.Medico a=new MedicoCadastroService.Medico(1,"Ana","123","SP",true,0,List.of("Dra Ana"));
    private final MedicoCadastroService.Medico b=new MedicoCadastroService.Medico(2,"Bia","456","SP",true,0,List.of());
    @BeforeEach void setup() { doReturn(a).when(medicos).buscar(1);doReturn(List.of(a,b)).when(medicos).listar(); }
    private LeitorPlanilhaVisitas.Linha producao(int n,String medico) { return new LeitorPlanilhaVisitas.Linha(n,Map.of("atendimento","123","dt.","2026-01-01","medico",medico)); }
    private LeitorPlanilhaVisitas.Linha hospital(int n,String medico) { return new LeitorPlanilhaVisitas.Linha(n,Map.of("atendimento","123","data consumo","2026-01-01","medico",medico,"cod produto","10102019","produto","Visita","valor tot","100","vl. a repassar","85","regra","0.85","setor","Faturado")); }
    private ConciliadorVisitas.Relatorio conferir(List<LeitorPlanilhaVisitas.Linha> p,List<LeitorPlanilhaVisitas.Linha> h,Long id) { return service.conciliar("Hospital","p.xlsx","h.xlsx",p,h,id); }
    @Test void preencheSemModificarOriginalEIdentificaSemHospital() {
        var v=conferir(List.of(producao(2,"")),List.of(),1L).visitas().getFirst();
        assertThat(v.medicoEfetivo()).isEqualTo("Ana");assertThat(v.medicoCadastroId()).isEqualTo(1L);
        assertThat(v.original()).containsEntry("medico","");assertThat(v.camposRevisados()).isNull();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
    }
    @Test void resolveDuasVisitasHospitalaresPorMedicoSemAlterarNomeDoHospital() {
        var r=conferir(List.of(producao(2,"")),List.of(hospital(2,"Dra Ana"),hospital(3,"Bia")),1L);
        var v=r.visitas().getFirst();assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(v.hospital().medico()).isEqualTo("Dra Ana");assertThat(v.medicoCadastroId()).isEqualTo(1L);
        assertThat(v.candidatos()).hasSize(2);
    }
    @Test void medicoDiferenteNaLinhaFicaParaConferencia() {
        var v=conferir(List.of(producao(2,"Bia")),List.of(hospital(2,"Bia")),1L).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);assertThat(v.hospital()).isNull();
        assertThat(v.medicoEfetivo()).isEqualTo("Bia");assertThat(v.medicoCadastroId()).isNull();
        assertThat(v.original()).containsEntry("medico","Bia");assertThat(v.medicoProducao().conflito()).isTrue();
    }
    @Test void hospitalDeOutroMedicoNaoPreencheValores() {
        var v=conferir(List.of(producao(2,"")),List.of(hospital(2,"Bia")),1L).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);assertThat(v.hospital()).isNull();
        assertThat(v.repasseEfetivo()).isNull();assertThat(v.medicoEfetivo()).isEqualTo("Ana");
    }
    @Test void aliasDaMesmaPessoaNaoEConflito() {
        var v=conferir(List.of(producao(2,"Dra Ana")),List.of(hospital(2,"Ana")),1L).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);assertThat(v.original()).containsEntry("medico","Dra Ana");
        assertThat(v.medicoProducao().preenchido()).isFalse();
    }
    @Test void variosMedicosPreservaComportamentoAnterior() {
        var v=conferir(List.of(producao(2,"")),List.of(),null).visitas().getFirst();
        assertThat(v.medicoEfetivo()).isBlank();assertThat(v.medicoProducao()).isNull();
    }
    @Test void cadastroInativoNaoPodeSerSelecionado() {
        doReturn(new MedicoCadastroService.Medico(1,"Ana","123","SP",false,0,List.of())).when(medicos).buscar(1);
        assertThatThrownBy(()->conferir(List.of(producao(2,"")),List.of(),1L)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void hospitalHomonimoNaoEAutomaticamenteAtribuidoAoCadastroSelecionado() {
        doReturn(List.of(a,b,new MedicoCadastroService.Medico(3,"Ana","789","SP",true,0,List.of()))).when(medicos).listar();
        var v=conferir(List.of(producao(2,"")),List.of(hospital(2,"Ana")),1L).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);assertThat(v.hospital()).isNull();assertThat(v.repasseEfetivo()).isNull();
    }
    @Test void identificacaoPermaneceDepoisDeSalvarJson() throws Exception {
        var v=conferir(List.of(producao(2,"")),List.of(),1L).visitas().getFirst();
        var json=new ObjectMapper().registerModule(new JavaTimeModule());
        var restored=json.readValue(json.writeValueAsString(v),ConciliadorVisitas.Visita.class);
        assertThat(restored.medicoCadastroId()).isEqualTo(1L);assertThat(restored.original()).isEqualTo(v.original());
    }
    @Test void novaVersaoPreservaIdentificacaoEAdicionaVisitaSemMedico() {
        var base=conferir(List.of(producao(2,"")),List.of(),1L);
        var campos=new HashMap<>(producao(3,"").campos());campos.put("atendimento","456");
        var r=new AtualizadorConciliacao(motor,service).atualizar(new RevisaoVisitasService.Estado(base,base,List.of(),0),"nova.xlsx","h.xlsx",List.of(producao(2,""),new LeitorPlanilhaVisitas.Linha(3,campos)),List.of());
        assertThat(r.relatorio().visitas()).hasSize(2).allMatch(v->Objects.equals(v.medicoCadastroId(),1L));
        assertThat(r.novas()).isEqualTo(1);
    }
}
