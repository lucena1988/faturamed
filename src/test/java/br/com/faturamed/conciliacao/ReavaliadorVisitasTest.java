package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReavaliadorVisitasTest {
    private final ReavaliadorVisitas motor = new ReavaliadorVisitas(new AplicadorRevisaoVisita());
    private final ConciliadorVisitas.RegistroHospital a = hospital(10, "85");
    private final ConciliadorVisitas.RegistroHospital b = hospital(11, "85");
    private ConciliadorVisitas.RegistroHospital hospital(int linha, String repasse) {
        return new ConciliadorVisitas.RegistroHospital(linha,"123",LocalDate.of(2026,1,1),"Paciente","Convenio","Medico",
                "10102019","Visita","Conta","Faturado",new BigDecimal("100"),new BigDecimal("0.85"),new BigDecimal(repasse));
    }
    private ConciliadorVisitas.Visita visita(int linha, ConciliadorVisitas.RegistroHospital h) {
        return new ConciliadorVisitas.Visita(linha,"123",null,h==null?ConciliadorVisitas.Status.DIVERGENTE:ConciliadorVisitas.Status.PAGA,
                "Conferir",Map.of("atendimento","123"),h,List.of(a,b));
    }
    private ConciliadorVisitas.Relatorio base(List<ConciliadorVisitas.Visita> visitas) {
        return new ConciliadorVisitas.Relatorio("Teste","m.xlsx","h.xlsx","Faturado",visitas,List.of(),Map.of(),BigDecimal.ZERO);
    }
    @Test void preencheUnicoRestanteAposEscolha() {
        var visitas=List.of(visita(2,a),visita(3,null));
        assertThat(motor.propostas(base(visitas),visitas,"123",Set.of(2))).containsOnlyKeys(3).containsEntry(3,b);
    }
    @Test void naoEscolhePorQuantidadeAntesDaRevisao() {
        var visitas=List.of(visita(2,null),visita(3,null));
        assertThat(motor.propostas(base(visitas),visitas,"123",Set.of())).isEmpty();
    }
    @Test void naoReutilizaCandidatoDisputado() {
        var visitas=List.of(visita(2,a),visita(3,null),visita(4,null));
        assertThat(motor.propostas(base(visitas),visitas,"123",Set.of(2))).isEmpty();
    }
    @Test void respeitaDecisaoManualAnterior() {
        var visitas=List.of(visita(2,a),visita(3,null));
        assertThat(motor.propostas(base(visitas),visitas,"123",Set.of(2,3))).isEmpty();
    }
    @Test void naoConfirmaRepasseInconsistente() {
        var errado=new ConciliadorVisitas.Visita(3,"123",null,ConciliadorVisitas.Status.DIVERGENTE,"Conferir",Map.of(),null,List.of(a,hospital(11,"90")));
        var visitas=List.of(visita(2,a),errado);
        assertThat(motor.propostas(base(List.of(errado)),visitas,"123",Set.of(2))).isEmpty();
    }
}
