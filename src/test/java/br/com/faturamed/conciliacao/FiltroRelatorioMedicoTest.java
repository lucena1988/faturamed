package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FiltroRelatorioMedicoTest {
    private final FiltroRelatorioMedico filtro = new FiltroRelatorioMedico();
    private ConciliadorVisitas.RegistroHospital hospital(int linha, String medico) {
        return new ConciliadorVisitas.RegistroHospital(linha,"123",LocalDate.of(2026,1,1),"Paciente " + medico,"Convenio",medico,
                "10102019","Visita","1","Faturado",new BigDecimal("100"),new BigDecimal("0.85"),new BigDecimal("85"));
    }
    private ConciliadorVisitas.Visita visita(int linha, String medico, ConciliadorVisitas.Status status) {
        var h = medico.isEmpty() ? null : hospital(linha,medico);
        return new ConciliadorVisitas.Visita(linha,"123",null,status,"Conferir",Map.of(),h,List.of(hospital(99,"Outro Medico")));
    }
    private ConciliadorVisitas.Relatorio base() {
        return new ConciliadorVisitas.Relatorio("Hospital","medico.xlsx","hospital.xlsx","Faturado",
                List.of(visita(2,"Medico A",ConciliadorVisitas.Status.PAGA),visita(3,"Medico A",ConciliadorVisitas.Status.PENDENTE),
                    visita(4,"Outro Medico",ConciliadorVisitas.Status.PAGA),visita(5,"",ConciliadorVisitas.Status.DIVERGENTE)),
                List.of(hospital(100,"Outro Medico")),Map.of(),BigDecimal.ZERO);
    }
    @Test void filtraERecalculaSemAtribuirCandidatosAmbiguos() {
        var r = filtro.filtrar(base()," medico a ");
        assertThat(r.visitas()).hasSize(2).allMatch(v -> v.medicoEfetivo().equals("Medico A"));
        assertThat(r.resumo()).containsEntry(ConciliadorVisitas.Status.PAGA,1L).containsEntry(ConciliadorVisitas.Status.PENDENTE,1L);
        assertThat(r.repasseCorrespondente()).isEqualByComparingTo("85");
        assertThat(r.hospitalSemProducao()).isEmpty();
    }
    @Test void rejeitaMedicoAusenteOuNaoIdentificado() {
        assertThatThrownBy(() -> filtro.filtrar(base()," ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filtro.filtrar(base(),"Inexistente")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exportacaoIndividualNaoIncluiCandidatosOuHistoricoDeOutros() throws Exception {
        var r = filtro.filtrar(base(),"Medico A");
        var revisao = new RevisaoVisitasService.Revisao(1,2,RevisaoVisitaRequest.Acao.AJUSTAR_DADOS,"Interno",
                "Outro Medico",Instant.now(),visita(2,"Outro Medico",ConciliadorVisitas.Status.PENDENTE),r.visitas().getFirst());
        byte[] bytes = new ExportadorRelatorioVisitas().exportar(r,List.of(revisao),"Medico A");
        try (var w = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(2);
            assertThat(w.getSheet("Visitas").getLastRowNum()).isEqualTo(2);
            assertThat(w.getSheet("Resumo").getRow(1).getCell(1).getStringCellValue()).isEqualTo("Medico A");
            assertThat(w.getSheet("Visitas").getRow(1).getCell(17).getStringCellValue()).isEqualTo("Revisao manual");
            for(var sheet:w) for(var row:sheet) for(var cell:row) assertThat(cell.toString()).doesNotContain("Outro Medico");
        }
    }
    @Test void usaMedicoCorrigidoNaRevisao() {
        var original=visita(2,"Outro Medico",ConciliadorVisitas.Status.PENDENTE);
        var corrigida=new ConciliadorVisitas.Visita(2,original.atendimento(),null,original.status(),original.motivo(),original.original(),
                original.hospital(),original.candidatos(),new ConciliadorVisitas.CamposRevisados("Medico A","Visita","10102019",null,null));
        var r=new ConciliadorVisitas.Relatorio("Hospital","m.xlsx","h.xlsx","Faturado",List.of(corrigida),List.of(),Map.of(),BigDecimal.ZERO);
        assertThat(filtro.filtrar(r,"medico a").visitas()).containsExactly(corrigida);
        assertThatThrownBy(() -> filtro.filtrar(r,"Outro Medico")).isInstanceOf(IllegalArgumentException.class);
    }
}
