package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DiagnosticoPlanilhasTest {
    private LeitorPlanilhaVisitas.Linha p(int n,String id,String atendimento,String medico) {
        return new LeitorPlanilhaVisitas.Linha(n,Map.of("id",id,"atendimento",atendimento,"dt.","2026-01-01","medico",medico));
    }
    private LeitorPlanilhaVisitas.Linha h(int n,String id,String atendimento,String medico) {
        return new LeitorPlanilhaVisitas.Linha(n,Map.of("id",id,"atendimento",atendimento,"data consumo","2026-01-01","medico",medico,"cod produto","10102019","valor tot","100","vl. a repassar","85","regra","0.85","setor","Faturado"));
    }
    private DiagnosticoPlanilhas.Resultado analisar(List<LeitorPlanilhaVisitas.Linha> p,List<LeitorPlanilhaVisitas.Linha> h) {
        return new DiagnosticoPlanilhas().analisar(p,h,new ConciliadorVisitas().conciliar("H","p","h",p,h));
    }
    @Test void separaCoberturaAmbiguidadeEIdentificacao() {
        var p=List.of(p(2,"A","1",""),p(3,"A","1",""),p(4,"B","2",""),p(5,"C","3",""));
        var h=List.of(h(2,"A","1","Ana"),h(3,"A","1","Bia"),h(4,"B","2","Ana"));
        var d=analisar(p,h);
        assertThat(d.producao().idsRepetidos()).isEqualTo(1);
        assertThat(d.producao().linhasComIdRepetido()).isEqualTo(2);
        assertThat(d.hospital().idsRepetidos()).isEqualTo(1);
        assertThat(d.correspondenciasSeguras()).isEqualTo(1);
        assertThat(d.semCorrespondencia()).isEqualTo(1);
        assertThat(d.ambiguas()).isEqualTo(2);
        assertThat(d.alertas()).filteredOn(a->a.codigo().equals("MEDICO_NAO_IDENTIFICADO")).singleElement().satisfies(a->assertThat(a.quantidade()).isEqualTo(3));
        assertThat(d.producao().semMedico()).isEqualTo(4);
        assertThat(p.getFirst().campo("Medico")).isEmpty();
    }
    @Test void ausenciaDeIdNaoMudaCorrespondenciaEIdsNaoEscolhemPrimeiraLinha() {
        var d=analisar(List.of(p(2,"","1","Ana")),List.of(h(2,"","1","Ana")));
        assertThat(d.correspondenciasSeguras()).isEqualTo(1);
        assertThat(d.producao().idsAusentes()).isEqualTo(1);
        assertThat(d.producao().idsRepetidos()).isZero();
        var repetido=analisar(List.of(p(2,"A","1","")),List.of(h(2,"A","1","Ana"),h(3,"A","1","Bia")));
        assertThat(repetido.correspondenciasSeguras()).isZero();assertThat(repetido.ambiguas()).isEqualTo(1);
    }
    @Test void limitaExemplosSemLimitarTotais() {
        var p=java.util.stream.IntStream.range(2,32).mapToObj(n->p(n,"A",String.valueOf(n),"")).toList();
        var d=analisar(p,List.of());
        assertThat(d.semCorrespondencia()).isEqualTo(30);
        assertThat(d.alertas()).allSatisfy(a->assertThat(a.linhas().size()).isLessThanOrEqualTo(20));
        assertThat(d.hospital().inicio()).isNull();
    }
}
