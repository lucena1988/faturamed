package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Formatos de competencia aceitos pelas telas de importacao: MM/AAAA e AAAA/MM, com barra ou hifen. */
class NormalizarCompetenciaTest {
    @ParameterizedTest
    @ValueSource(strings={"07/2026","7/2026","07-2026","2026/07","2026-07","2026/7","2026-7"," 07/2026 "})
    void aceitaMesAnoEAnoMesComUmOuDoisDigitosDeMes(String entrada) {
        assertThat(BaseHospitalarService.normalizarCompetencia(entrada)).isEqualTo("2026-07");
    }

    @Test void preservaMesesDeDoisDigitos() {
        assertThat(BaseHospitalarService.normalizarCompetencia("12/2026")).isEqualTo("2026-12");
        assertThat(BaseHospitalarService.normalizarCompetencia("2026/10")).isEqualTo("2026-10");
    }

    @Test void aceitaNomeDoMesComAno() {
        assertThat(BaseHospitalarService.normalizarCompetencia("julho 2026")).isEqualTo("2026-07");
    }

    @ParameterizedTest
    @ValueSource(strings={"13/2026","2026/13","00/2026","2026/00","7/26","2026","julho","07/2026x",""})
    void rejeitaFormatosInvalidos(String entrada) {
        assertThatThrownBy(()->BaseHospitalarService.normalizarCompetencia(entrada)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejeitaNulo() {
        assertThatThrownBy(()->BaseHospitalarService.normalizarCompetencia(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
