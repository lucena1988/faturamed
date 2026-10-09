package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AtualizadorConciliacaoTest {
    private final ConciliadorVisitas motor=new ConciliadorVisitas();
    private final AtualizadorConciliacao versoes=new AtualizadorConciliacao(motor);
    private LeitorPlanilhaVisitas.Linha medico(int linha,String att) { return new LeitorPlanilhaVisitas.Linha(linha,Map.of("atendimento",att)); }
    private LeitorPlanilhaVisitas.Linha hospital(int linha,String att,String setor,String repasse) {
        return new LeitorPlanilhaVisitas.Linha(linha,Map.of("atendimento",att,"data consumo","2026-01-01","medico","Medico "+att,
                "cod produto","10102019","produto","Visita","conta paciente",att,"setor",setor,"valor tot","100","regra","0.85","vl. a repassar",repasse));
    }
    private RevisaoVisitasService.Estado estado(List<LeitorPlanilhaVisitas.Linha> producao,List<LeitorPlanilhaVisitas.Linha> hospital) {
        var r=motor.conciliar("Hospital","m.xlsx","h.xlsx",producao,hospital);
        return new RevisaoVisitasService.Estado(r,r,List.of(),0);
    }
    @Test void preservaPagoReavaliaPendenteEAdicionaNovaMesmoReordenando() {
        var anterior=estado(List.of(medico(2,"A"),medico(3,"B")),List.of(hospital(2,"A","Faturado","85")));
        var r=versoes.atualizar(anterior,"novo.xlsx","novo-h.xlsx",List.of(medico(2,"B"),medico(3,"C"),medico(4,"A")),
                List.of(hospital(2,"C","Faturado","85"),hospital(3,"B","Faturado","85"),hospital(4,"A","Faturado","85")));
        assertThat(r.preservadas()).isEqualTo(1);assertThat(r.reavaliadas()).isEqualTo(1);assertThat(r.novas()).isEqualTo(1);
        assertThat(r.relatorio().resumo()).containsEntry(ConciliadorVisitas.Status.PAGA,3L);
        assertThat(r.relatorio().repasseCorrespondente()).isEqualByComparingTo("255");
        assertThat(r.relatorio().visitas().stream().filter(v->v.atendimento().equals("A")).findFirst().orElseThrow().hospital().linha()).isEqualTo(2);
        assertThat(r.linhasProducao()).containsEntry(2,4).containsEntry(3,2).containsEntry(4,3);
    }
    @Test void quantidadeRepetidaNaoEDescartadaComoDuplicata() {
        var anterior=estado(List.of(medico(2,"A")),List.of(hospital(2,"A","Faturado","85")));
        var novoHospital=new HashMap<>(hospital(3,"A","Faturado","85").campos());novoHospital.put("data consumo","2026-02-01");
        var r=versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A"),medico(3,"A")),
                List.of(hospital(2,"A","Faturado","85"),new LeitorPlanilhaVisitas.Linha(3,novoHospital)));
        assertThat(r.novas()).isEqualTo(1);assertThat(r.relatorio().visitas()).hasSize(2);
        assertThat(r.relatorio().resumo()).containsEntry(ConciliadorVisitas.Status.PAGA,2L);
    }
    @Test void rejeitaReducaoDaProducao() {
        var anterior=estado(List.of(medico(2,"A"),medico(3,"A")),List.of(hospital(2,"A","Faturado","85")));
        assertThatThrownBy(()->versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A")),List.of(hospital(2,"A","Faturado","85"))))
                .isInstanceOf(ConflitoRevisaoException.class);
    }
    @Test void rejeitaRegistroPagoAlteradoOuAusente() {
        var anterior=estado(List.of(medico(2,"A")),List.of(hospital(2,"A","Faturado","85")));
        assertThatThrownBy(()->versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A")),List.of(hospital(2,"A","Faturado","90"))))
                .isInstanceOf(ConflitoRevisaoException.class);
        assertThatThrownBy(()->versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A")),List.of())).isInstanceOf(ConflitoRevisaoException.class);
    }
    @Test void reavaliaFaturamentoAindaNaoRevisado() {
        var anterior=estado(List.of(medico(2,"A")),List.of(hospital(2,"A","Em analise","85")));
        var r=versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A")),List.of(hospital(2,"A","Faturado","85")));
        assertThat(r.reavaliadas()).isEqualTo(1);assertThat(r.relatorio().resumo()).containsEntry(ConciliadorVisitas.Status.PAGA,1L);
    }
    @Test void decisaoManualPendenteNaoPermiteAssociarCandidatoPorExclusao() {
        var original=estado(List.of(medico(2,"A")),List.of());var v=original.atual().visitas().getFirst();
        var revisao=new RevisaoVisitasService.Revisao(1,2,RevisaoVisitaRequest.Acao.MANTER_PENDENTE,"Operador","Conferido",Instant.now(),v,v);
        var anterior=new RevisaoVisitasService.Estado(original.original(),original.atual(),List.of(revisao),1);
        var r=versoes.atualizar(anterior,"m.xlsx","h.xlsx",List.of(medico(2,"A"),medico(3,"A")),List.of(hospital(2,"A","Faturado","85")));
        assertThat(r.preservadas()).isEqualTo(1);assertThat(r.relatorio().resumo()).containsEntry(ConciliadorVisitas.Status.PAGA,0L);
        assertThat(r.relatorio().visitas().get(1).status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
    }
}
