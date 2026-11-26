package br.com.faturamed.seguranca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.faturamed.conciliacao.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Previa da liberacao: o que cada medico passaria a ver, sem publicar nada. */
class PreviaLiberacaoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);private final RevisaoVisitasService revisoes=mock(RevisaoVisitasService.class);private final MedicoCadastroService medicos=mock(MedicoCadastroService.class);
    private final LiberacaoRelatorioService service=new LiberacaoRelatorioService(jdbc,revisoes,medicos,new ObjectMapper().findAndRegisterModules());
    private final ExportadorPreviaLiberacao exportador=new ExportadorPreviaLiberacao();

    private ConciliadorVisitas.Visita visita(int linha,String atendimento,ConciliadorVisitas.Status status,String repasse,Long medico) {
        return new ConciliadorVisitas.Visita(linha,atendimento,LocalDate.of(2026,9,linha),status,"Obs "+atendimento,Map.of("paciente","SEGREDO"),null,List.of(),
                new ConciliadorVisitas.CamposRevisados("Ana","Ecodoppler","40901106",new BigDecimal(repasse),new BigDecimal(repasse),medico));
    }
    private List<ConciliadorVisitas.Visita> configurar() throws Exception {
        var pagaAna=visita(2,"A",ConciliadorVisitas.Status.PAGA,"100",10L);
        var pendenteAna=visita(3,"B",ConciliadorVisitas.Status.PENDENTE,"50",10L);
        var semMedico=visita(4,"C",ConciliadorVisitas.Status.PAGA,"70",null);
        var excluida=visita(5,"D",ConciliadorVisitas.Status.EXCLUIDA,"10",10L);
        var base=new ConciliadorVisitas.Relatorio("Hospital Demo","p","h","",List.of(pagaAna,pendenteAna,semMedico,excluida),List.of(),Map.of(),BigDecimal.ZERO);
        when(revisoes.carregar(anyLong())).thenReturn(new RevisaoVisitasService.Estado(base,base,List.of(),3));
        when(medicos.listar()).thenReturn(List.of(new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of())));
        for(var v:List.of(pagaAna,pendenteAna,excluida)) when(medicos.identificar(eq(v),anyList())).thenReturn(10L);
        when(medicos.identificar(eq(semMedico),anyList())).thenReturn(null);
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),anyLong())).thenReturn(1L);
        return List.of(pagaAna,pendenteAna,semMedico,excluida);
    }

    @Test void previaTraz_PorMedico_AsMesmasVisitasDaLiberacao_ESeparaOQueFicaDeFora_SemGravarNada() throws Exception {
        configurar();
        var previa=service.previa(1);
        assertThat(previa.hospital()).isEqualTo("Hospital Demo");
        assertThat(previa.versao()).isEqualTo(3);
        assertThat(previa.situacao()).isEqualTo("EM_REVISAO");
        assertThat(previa.visitas()).isEqualTo(3);
        assertThat(previa.excluidas()).isEqualTo(1);
        assertThat(previa.medicos()).singleElement().satisfies(m->{
            assertThat(m.id()).isEqualTo(10); assertThat(m.nome()).isEqualTo("Ana");
            assertThat(m.visitas()).extracting(v->v.atendimento()).containsExactly("A","B"); });
        assertThat(previa.semMedico()).singleElement().satisfies(v->assertThat(v.atendimento()).isEqualTo("C"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void excelDaPreviaTemResumoUmaAbaPorMedicoEOQueFicaDeFora() throws Exception {
        configurar();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(service.previa(1))))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            assertThat(workbook.getSheet("Ana")).isNotNull();
            assertThat(workbook.getSheet("Fora da liberacao")).isNotNull();
            var resumo=workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue()).contains("PREVIA").contains("Nada foi enviado");
            var textoResumo=new StringBuilder();
            resumo.forEach(row->row.forEach(cell->textoResumo.append(cell.toString()).append('|')));
            assertThat(textoResumo.toString()).contains("Hospital Demo","Em revisao").contains("Ana");
            // Aba do medico: cabecalho, as duas visitas dele e a linha de repasse das pagas (so a paga, 100).
            var aba=workbook.getSheet("Ana");
            assertThat(aba.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Atendimento");
            assertThat(aba.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(aba.getRow(1).getCell(2).getStringCellValue()).isEqualTo("40901106");
            assertThat(aba.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Paga");
            assertThat(aba.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(100.0);
            assertThat(aba.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Pendente");
            assertThat(aba.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Repasse das pagas");
            assertThat(aba.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(100.0);
            // O que o medico nao ve (paciente e dados brutos) nao esta em nenhuma aba.
            workbook.forEach(sheet->sheet.forEach(row->row.forEach(cell->assertThat(cell.toString()).doesNotContain("SEGREDO"))));
            // Fora da liberacao: a visita sem medico cadastrado, com o motivo.
            var fora=workbook.getSheet("Fora da liberacao");
            assertThat(fora.getRow(1).getCell(0).getStringCellValue()).isEqualTo("C");
            assertThat(fora.getRow(1).getCell(5).getStringCellValue()).isEqualTo("Obs C");
        }
    }

    @Test void semVisitasForaDaLiberacaoNaoCriaAbaSobrando() throws Exception {
        var v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"LIBERADO","H",1,List.of(new LiberacaoRelatorioService.PreviaMedico(10,"Ana",List.of(v))),List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            assertThat(workbook.getSheet("Fora da liberacao")).isNull();
            var texto=new StringBuilder();workbook.getSheet("Resumo").forEach(row->row.forEach(cell->texto.append(cell.toString()).append('|')));
            assertThat(texto.toString()).contains("Liberado ao medico");
        }
    }

    @Test void nomesDeAbaSaoValidosEUnicosMesmoComMedicosHomonimosOuNomesLongos() throws Exception {
        PortalMedicoController.Visita v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","X",1,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"EM_REVISAO","H",3,List.of(
                new LiberacaoRelatorioService.PreviaMedico(1,"Ana",List.of(v)),new LiberacaoRelatorioService.PreviaMedico(2,"Ana",List.of(v)),
                new LiberacaoRelatorioService.PreviaMedico(3,"Dr: Nome/Muito?Longo [Que] Passa De Trinta E Um Caracteres",List.of(v))),List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            List<String> nomes=new ArrayList<>();for(int i=0;i<workbook.getNumberOfSheets();i++)nomes.add(workbook.getSheetName(i));
            assertThat(nomes).hasSize(4).doesNotHaveDuplicates().contains("Resumo","Ana","Ana (2)");
            assertThat(nomes).allSatisfy(n->{ assertThat(n.length()).isLessThanOrEqualTo(31); assertThat(n).doesNotContainPattern("[\\[\\]:*?/\\\\]"); });
        }
    }
}
