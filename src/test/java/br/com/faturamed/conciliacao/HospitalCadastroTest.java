package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class HospitalCadastroTest {
    @Test void documentoOpcionalNaoCriaChaveVazia() {
        assertThat(HospitalCadastroService.normalizarDocumento(null)).isNull();
        assertThat(HospitalCadastroService.normalizarDocumento("  ")).isNull();
    }
    @Test void normalizaDocumentoParaDeteccaoDeDuplicidade() {
        assertThat(HospitalCadastroService.normalizarDocumento("12.345.678/0001-90")).isEqualTo("12345678000190");
        assertThat(HospitalCadastroService.normalizarDocumento("ab.123/45")).isEqualTo("AB12345");
        assertThatThrownBy(()->HospitalCadastroService.normalizarDocumento("---")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void revisaoEExportacaoFiltradaPreservamIdDoHospital() {
        var visita=new ConciliadorVisitas.Visita(2,"123",null,ConciliadorVisitas.Status.PENDENTE,"Conferir",Map.of("medico","Medico"),null,List.of());
        var base=new ConciliadorVisitas.Relatorio("Hospital","m.xlsx","h.xlsx","Faturado",List.of(visita),List.of(),Map.of(),BigDecimal.ZERO,77L);
        assertThat(new AplicadorRevisaoVisita().recalcular(base,base.visitas()).hospitalCadastroId()).isEqualTo(77L);
        assertThat(new FiltroRelatorioMedico().filtrar(base,"Medico").hospitalCadastroId()).isEqualTo(77L);
    }
}
