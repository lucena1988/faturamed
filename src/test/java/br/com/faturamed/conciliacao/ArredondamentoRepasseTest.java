package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ArredondamentoRepasseTest {
    private ConciliadorVisitas.Visita visita(int linha,String valor) {
        var hospital=new ConciliadorVisitas.RegistroHospital(linha,"123",LocalDate.of(2026,1,1),"","","Medico","101","Visita","","Faturado",new BigDecimal("157.76"),new BigDecimal("0.85"),new BigDecimal(valor));
        return new ConciliadorVisitas.Visita(linha,"123",hospital.data(),ConciliadorVisitas.Status.PAGA,"",Map.of(),hospital,List.of(hospital));
    }

    @Test void somaVisitasArredondadasEPreservaValorOriginal() throws Exception {
        var primeira=visita(2,"134.096");
        var segunda=visita(3,"134.096");
        var relatorio=new ConciliadorVisitas.Relatorio("Hospital","producao","hospital","",List.of(primeira,segunda),List.of(),Map.of(),new BigDecimal("268.192"));
        assertThat(primeira.repasseEfetivo()).isEqualByComparingTo("134.10");
        assertThat(primeira.hospital().repasse()).isEqualByComparingTo("134.096");
        assertThat(relatorio.repasseCorrespondente()).isEqualByComparingTo("268.20");
        assertThat(new AplicadorRevisaoVisita().recalcular(relatorio,relatorio.visitas()).repasseCorrespondente()).isEqualByComparingTo("268.20");
        var json=new ObjectMapper().registerModule(new JavaTimeModule());
        var salvo=json.readValue(json.writeValueAsString(relatorio),ConciliadorVisitas.Relatorio.class);
        assertThat(salvo.repasseCorrespondente()).isEqualByComparingTo("268.20");
        assertThat(salvo.visitas().getFirst().hospital().repasse()).isEqualByComparingTo("134.096");
    }

    @Test void ajusteManualUsaMesmaRegraEPreservaPrecisaoDoAjuste() {
        var original=visita(2,"134.096");
        var ajuste=new ConciliadorVisitas.CamposRevisados("Medico","Visita","101",new BigDecimal("157.76"),new BigDecimal("134.095"));
        var revisada=new ConciliadorVisitas.Visita(2,"123",original.data(),original.status(),"",Map.of(),original.hospital(),original.candidatos(),ajuste);
        assertThat(revisada.repasseEfetivo()).isEqualByComparingTo("134.10");
        assertThat(revisada.camposRevisados().repasse()).isEqualByComparingTo("134.095");
        assertThat(visita(3,"134.094").repasseEfetivo()).isEqualByComparingTo("134.09");
        var semValor=new ConciliadorVisitas.Visita(4,"456",null,ConciliadorVisitas.Status.PENDENTE,"",Map.of(),null,List.of());
        assertThat(semValor.repasseEfetivo()).isNull();
    }
}
