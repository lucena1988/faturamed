package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MesmoProcedimentoTest {
    @Test void codigosDiferentesSaoProcedimentosDistintos() {
        assertThat(BaseHospitalarService.mesmoProcedimento("40901106","Ecodopplercardiograma","40901360","Doppler Colorido")).isFalse();
    }
    @Test void mesmoCodigoEMesmoProcedimentoMesmoComNomeDiferente() {
        assertThat(BaseHospitalarService.mesmoProcedimento("4.09.01.106","Ecodoppler","40901106","Ecodopplercardiograma Transtoracico")).isTrue();
    }
    @Test void semCodigoDecidePeloNome() {
        assertThat(BaseHospitalarService.mesmoProcedimento("","Ecodoppler","","Doppler")).isFalse();
        assertThat(BaseHospitalarService.mesmoProcedimento("","Ecodóppler","","ECODOPPLER")).isTrue();
    }
    @Test void semCodigoENomeContinuaComoRepeticaoPossivel() {
        assertThat(BaseHospitalarService.mesmoProcedimento("","","40901106","Ecodoppler")).isTrue();
    }
}
