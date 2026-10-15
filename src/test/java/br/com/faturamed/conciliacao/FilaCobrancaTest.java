package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FilaCobrancaTest {
    private final MedicoCadastroService medicos=new MedicoCadastroService(null) {
        public List<Medico> listar(){return List.of(new Medico(1,"Ana","123","SP",true,0,List.of("Dra Ana")));}
    };
    private LeitorPlanilhaVisitas.Linha p(int linha,String atendimento,String data,String medico) {
        return new LeitorPlanilhaVisitas.Linha(linha,Map.of("id","ID"+linha,"atendimento",atendimento,"dt.",data,"medico",medico,"cod produto","10102019","procedimento/mat-med","Visita","valor orig","0"));
    }
    private LeitorPlanilhaVisitas.Linha h(int linha,String atendimento,String medico,String setor) {
        return new LeitorPlanilhaVisitas.Linha(linha,Map.of("atendimento",atendimento,"data consumo","2026-01-01","medico",medico,"cod produto","10102019","produto","Visita","valor tot","100","vl. a repassar","85","regra","0.85","setor",setor));
    }
    private ConciliadorVisitas.Relatorio relatorio() {
        return new ConciliadorVisitas().conciliar("H","p","h",List.of(p(2,"A","2026-01-01","Dra Ana"),p(3,"B","2026-02-01",""),p(4,"B","2026-02-01",""),p(5,"C","","Ana"),p(6,"D","2026-01-01","Ana"),p(7,"E","2026-01-01",""),p(8,"F","2026-01-01","Ana")),
                List.of(h(2,"D","Ana","Em analise"),h(3,"E","Ana","Faturado"),h(4,"E","Bia","Faturado"),h(5,"F","Ana","Faturado")));
    }
    @Test void incluiSomenteAusentesPreservaVisitasRepetidasEUsaDataReferencia() {
        var fila=new FilaCobranca(medicos).analisar(relatorio(),LocalDate.of(2026,3,1),0,"");
        assertThat(fila.visitas()).extracting(FilaCobranca.Item::linha).containsExactly(2,3,4,5);
        assertThat(fila.visitas().getFirst().dias()).isEqualTo(59L);
        assertThat(fila.visitas().getFirst().chaveMedico()).isEqualTo("id:1");
        assertThat(fila.semValor()).isEqualTo(4);assertThat(fila.repasseInformado()).isZero();
        assertThat(fila.semMedico()).isEqualTo(2);assertThat(fila.semData()).isEqualTo(1);
    }
    @Test void filtrosPorMedicoETempoNaoTransformamDataAusenteEmAntiga() {
        var service=new FilaCobranca(medicos);var date=LocalDate.of(2026,3,1);
        assertThat(service.analisar(relatorio(),date,30,"").visitas()).extracting(FilaCobranca.Item::linha).containsExactly(2);
        assertThat(service.analisar(relatorio(),date,0,"sem").visitas()).extracting(FilaCobranca.Item::linha).containsExactly(3,4);
        assertThat(service.analisar(relatorio(),date,0,"id:1").visitas()).extracting(FilaCobranca.Item::linha).containsExactly(2,5);
        assertThatThrownBy(()->service.analisar(relatorio(),date,-1,"")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void somaSomenteRepasseRevisadoEPreservaZeroConhecido() {
        var r=relatorio();
        var visitas=r.visitas().stream().map(v->{
            if(v.linhaMedico()!=2&&v.linhaMedico()!=3)return v;
            var campos=new ConciliadorVisitas.CamposRevisados("Ana","Visita","10102019",new java.math.BigDecimal("100"),
                    v.linhaMedico()==2?new java.math.BigDecimal("85"):java.math.BigDecimal.ZERO,1L);
            return new ConciliadorVisitas.Visita(v.linhaMedico(),v.atendimento(),v.data(),v.status(),v.motivo(),v.original(),v.hospital(),v.candidatos(),campos,v.medicoProducao());
        }).toList();
        var atual=new ConciliadorVisitas.Relatorio(r.hospital(),r.arquivoMedico(),r.arquivoHospital(),r.regraStatus(),visitas,r.hospitalSemProducao(),r.resumo(),r.repasseCorrespondente());
        var fila=new FilaCobranca(medicos).analisar(atual,LocalDate.of(2026,3,1),0,"");
        assertThat(fila.repasseInformado()).isEqualByComparingTo("85");assertThat(fila.semValor()).isEqualTo(2);
        assertThat(fila.visitas().get(1).repasse()).isEqualByComparingTo("0");assertThat(r.visitas().getFirst().original().get("valor orig")).isEqualTo("0");
    }
    @Test void exportaDatasIdentificadoresEValoresDesconhecidosSemDadosDePaciente() throws Exception {
        var fila=new FilaCobranca(medicos).analisar(relatorio(),LocalDate.of(2026,3,1),0,"");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new ExportadorRelatorioVisitas().exportarCobranca(10,fila)))) {
            var sheet=book.getSheet("Cobranca");assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(3))).isTrue();
            assertThat(sheet.getRow(1).getCell(9).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("10102019");
            assertThat(book.getSheet("Resumo").getRow(5).getCell(1).getNumericCellValue()).isEqualTo(4);
            for(var cell:sheet.getRow(0))assertThat(cell.getStringCellValue()).doesNotContain("Paciente");
        }
    }
    @Test void naoConsultaNemExportaVersaoHistorica() throws Exception {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var revisoes=mock(RevisaoVisitasService.class);var exportador=mock(ExportadorRelatorioVisitas.class);
        var r=relatorio();when(revisoes.carregar(10L)).thenReturn(new RevisaoVisitasService.Estado(r,r,List.of(),0));
        when(jdbc.queryForList(anyString(),eq(10L))).thenReturn(List.of(Map.of("id",11L)));
        var controller=new CobrancaController(revisoes,new FilaCobranca(medicos),exportador,jdbc);
        assertThatThrownBy(()->controller.exportar(10,0,"")).isInstanceOf(ConflitoRevisaoException.class);
        verifyNoInteractions(exportador);
    }
}
