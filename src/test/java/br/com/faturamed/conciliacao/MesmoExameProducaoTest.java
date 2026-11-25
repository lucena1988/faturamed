package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MesmoExameProducaoTest {
    private ConciliadorVisitas.RegistroHospital hospital(String codigo,String nome) {
        return new ConciliadorVisitas.RegistroHospital(1,"1",java.time.LocalDate.of(2026,9,18),"","","Medico",codigo,nome,"","Faturado",null,null,null,"EXAME",null);
    }
    @Test void codigoIgualValeMesmoComNomeDiferente() {
        assertThat(ConciliadorVisitas.mesmoExame(Map.of("codigo procedimento","40901360","procedimento","Doppler Colorido Arterias Cervicais"),
                hospital("40901360","Doppler Colorido De Vasos Cervicais Arteriais Bilateral"))).isTrue();
    }
    @Test void codigoDiferenteEOutroExame() {
        assertThat(ConciliadorVisitas.mesmoExame(Map.of("codigo procedimento","40901106"),hospital("40901360","Doppler"))).isFalse();
        assertThat(ConciliadorVisitas.mesmoExame(Map.of("cod produto","4.09.01.360"),hospital("40901360","Doppler"))).isTrue();
    }
    @Test void semCodigoDecidePeloNomeOuAceitaQuandoNaoHaNada() {
        assertThat(ConciliadorVisitas.mesmoExame(Map.of("procedimento/mat-med","Ecodoppler"),hospital("","Doppler"))).isFalse();
        assertThat(ConciliadorVisitas.mesmoExame(Map.of("procedimento","DOPPLER"),hospital("","Doppler"))).isTrue();
        assertThat(ConciliadorVisitas.mesmoExame(Map.of(),hospital("40901360","Doppler"))).isTrue();
    }

    @Test void visitaSemHospitalExpoeCodigoNomeETipoDaProducao() {
        var visita=new ConciliadorVisitas.Visita(2,"57480263",java.time.LocalDate.of(2026,9,18),ConciliadorVisitas.Status.PENDENTE,"Sem correspondente",
                Map.of("atendimento","57480263","codigo procedimento","40901106","procedimento","Ecodopplercardiograma Transtoracico"),null,java.util.List.of());
        assertThat(visita.codigoProducao()).isEqualTo("40901106");
        assertThat(visita.procedimentoProducao()).isEqualTo("Ecodopplercardiograma Transtoracico");
        assertThat(visita.tipoProducao()).isEqualTo("EXAME");
        assertThat(visita.codigoEfetivo()).isNull();
    }
}
