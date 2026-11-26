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
        return new ConciliadorVisitas.Visita(linha,atendimento,LocalDate.of(2026,9,linha),status,"Obs "+atendimento,Map.of("paciente","Maria Paciente","convenio","Unimed"),null,List.of(),
                new ConciliadorVisitas.CamposRevisados("Ana","Ecodoppler","40901106",new BigDecimal(repasse),new BigDecimal(repasse),medico));
    }
    /** Visita ainda sem registro do hospital: codigo, nome do exame, paciente e convenio so existem na producao do medico. */
    private ConciliadorVisitas.Visita visitaSoComProducao(int linha,String atendimento) {
        return new ConciliadorVisitas.Visita(linha,atendimento,LocalDate.of(2026,8,linha),ConciliadorVisitas.Status.PENDENTE,"Sem correspondente no arquivo do hospital",
                Map.of("paciente","Joao Silva","convenio","Bradesco","codigo procedimento","99887766","procedimento","Doppler Venoso"),null,List.of());
    }
    private void configurar() throws Exception {
        var pendente=visitaSoComProducao(6,"E");
        var pagaAna=visita(2,"A",ConciliadorVisitas.Status.PAGA,"100",10L);
        var divergenteAna=visita(3,"B",ConciliadorVisitas.Status.DIVERGENTE,"50",10L);
        var semMedico=visita(4,"C",ConciliadorVisitas.Status.PAGA,"70",null);
        var excluida=visita(5,"D",ConciliadorVisitas.Status.EXCLUIDA,"10",10L);
        // A pendente vem primeiro no relatorio para provar que a previa a coloca depois das pagas.
        var base=new ConciliadorVisitas.Relatorio("Hospital Demo","p","h","",List.of(pendente,pagaAna,divergenteAna,semMedico,excluida),List.of(),Map.of(),BigDecimal.ZERO);
        when(revisoes.carregar(anyLong())).thenReturn(new RevisaoVisitasService.Estado(base,base,List.of(),3));
        when(medicos.listar()).thenReturn(List.of(new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of())));
        for(var v:List.of(pendente,pagaAna,divergenteAna,excluida)) when(medicos.identificar(eq(v),anyList())).thenReturn(10L);
        when(medicos.identificar(eq(semMedico),anyList())).thenReturn(null);
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),anyLong())).thenReturn(1L);
        // A paga veio do arquivo do hospital de 08/2026 (faturamento pago em mes diferente da producao).
        when(jdbc.queryForList(contains("from vinculo_base_hospitalar b"),eq(1L))).thenReturn(List.of(Map.<String,Object>of("linha_producao",2,"competencia","2026-08")));
        when(jdbc.queryForList(contains("select competencia from arquivo_base_hospitalar"),eq(String.class),eq(1L))).thenReturn(List.of("2026-09"));
    }
    private String texto(Sheet sheet) { var t=new StringBuilder();sheet.forEach(row->row.forEach(cell->t.append(cell.toString()).append('|')));return t.toString(); }

    @Test void previaTraz_PorMedico_AsMesmasVisitasDaLiberacao_ESeparaOQueFicaDeFora_SemGravarNada() throws Exception {
        configurar();
        var previa=service.previa(1);
        assertThat(previa.hospital()).isEqualTo("Hospital Demo");
        assertThat(previa.versao()).isEqualTo(3);
        assertThat(previa.situacao()).isEqualTo("EM_REVISAO");
        assertThat(previa.competenciasProducao()).containsExactly("2026-09");
        assertThat(previa.visitas()).isEqualTo(4);
        assertThat(previa.excluidas()).isEqualTo(1);
        assertThat(previa.medicos()).singleElement().satisfies(m->{
            assertThat(m.id()).isEqualTo(10); assertThat(m.nome()).isEqualTo("Ana");
            assertThat(m.visitas()).extracting(v->v.atendimento()).containsExactlyInAnyOrder("E","A","B"); });
        assertThat(previa.semMedico()).singleElement().satisfies(v->{ assertThat(v.atendimento()).isEqualTo("C"); assertThat(v.paciente()).isEqualTo("Maria Paciente"); });
        assertThat(previa.extras().get(2).compFaturada()).isEqualTo("2026-08");
        assertThat(previa.extras().get(6)).satisfies(e->{ assertThat(e.paciente()).isEqualTo("Joao Silva"); assertThat(e.convenio()).isEqualTo("Bradesco"); assertThat(e.compFaturada()).isEmpty(); });
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void semRegistroDoHospitalCodigoENomeDoExameVemDaProducaoDoMedico() throws Exception {
        configurar();
        var ana=service.previa(1).medicos().getFirst();
        var pendente=ana.visitas().stream().filter(v->v.atendimento().equals("E")).findFirst().orElseThrow();
        assertThat(pendente.codigoProcedimento()).isEqualTo("99887766");
        assertThat(pendente.procedimento()).isEqualTo("Doppler Venoso");
        // O que o medico recebe no portal e o mesmo: a visita publicada tambem traz codigo e exame da producao, sem dados do paciente.
        var publicada=PortalMedicoController.sanitizar(visitaSoComProducao(6,"E"),new MedicoCadastroService.Medico(10,"Ana","123","SP",true,0,List.of()));
        assertThat(publicada.codigoProcedimento()).isEqualTo("99887766");
        assertThat(publicada.procedimento()).isEqualTo("Doppler Venoso");
        assertThat(publicada.toString()).doesNotContain("Joao Silva","Bradesco");
    }

    @Test void excelDaMedicoTemCabecalhoColunasDeCompetenciaStatusRepasseZeradoOrdemEFiltro() throws Exception {
        configurar();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(service.previa(1))))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            var aba=workbook.getSheet("Ana");
            // Bloco de cabecalho: medico, hospital, competencia e repasse das pagas.
            assertThat(aba.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Medico");
            assertThat(aba.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Ana");
            assertThat(aba.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Hospital Demo");
            assertThat(aba.getRow(2).getCell(1).getStringCellValue()).isEqualTo("09/2026");
            assertThat(aba.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(100.0);
            // Tabela: cabecalho na linha 6 com as colunas do modelo (comp. exame, producao, faturada, status).
            var cab=aba.getRow(5);
            assertThat(cab.getCell(0).getStringCellValue()).isEqualTo("Atendimento");
            assertThat(cab.getCell(2).getStringCellValue()).isEqualTo("Paciente");
            assertThat(cab.getCell(3).getStringCellValue()).isEqualTo("Convenio");
            assertThat(cab.getCell(6).getStringCellValue()).isEqualTo("Comp. exame");
            assertThat(cab.getCell(7).getStringCellValue()).isEqualTo("Comp. producao");
            assertThat(cab.getCell(8).getStringCellValue()).isEqualTo("Comp. faturada (hospital)");
            assertThat(cab.getCell(9).getStringCellValue()).isEqualTo("Status");
            assertThat(cab.getCell(10).getStringCellValue()).isEqualTo("Repasse (R$)");
            // Paga primeiro, com paciente, convenio, competencias e status "PAGO 08/2026"; repasse 100.
            var paga=aba.getRow(6);
            assertThat(paga.getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(paga.getCell(2).getStringCellValue()).isEqualTo("Maria Paciente");
            assertThat(paga.getCell(3).getStringCellValue()).isEqualTo("Unimed");
            assertThat(paga.getCell(4).getStringCellValue()).isEqualTo("40901106");
            assertThat(paga.getCell(6).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(7).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(8).getStringCellValue()).isEqualTo("08/2026");
            assertThat(paga.getCell(9).getStringCellValue()).isEqualTo("PAGO 08/2026");
            assertThat(paga.getCell(10).getNumericCellValue()).isEqualTo(100.0);
            // Depois as divergentes e as pendentes, sempre com repasse 0,00 (nada faturado), agrupadas no fim.
            var divergente=aba.getRow(7);
            assertThat(divergente.getCell(0).getStringCellValue()).isEqualTo("B");
            assertThat(divergente.getCell(9).getStringCellValue()).isEqualTo("DIVERGENTE");
            assertThat(divergente.getCell(10).getNumericCellValue()).isZero();
            var pendente=aba.getRow(8);
            assertThat(pendente.getCell(0).getStringCellValue()).isEqualTo("E");
            assertThat(pendente.getCell(2).getStringCellValue()).isEqualTo("Joao Silva");
            assertThat(pendente.getCell(3).getStringCellValue()).isEqualTo("Bradesco");
            assertThat(pendente.getCell(4).getStringCellValue()).isEqualTo("99887766");
            assertThat(pendente.getCell(5).getStringCellValue()).isEqualTo("Doppler Venoso");
            assertThat(pendente.getCell(6).getStringCellValue()).isEqualTo("08/2026");
            assertThat(pendente.getCell(8).getStringCellValue()).isEmpty();
            assertThat(pendente.getCell(9).getStringCellValue()).isEqualTo("PENDENTE");
            assertThat(pendente.getCell(10).getNumericCellValue()).isZero();
            // Filtro nos cabecalhos (ordenar zerados para cima ou para baixo) e painel congelado abaixo do cabecalho.
            assertThat(aba.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(aba.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A6:L9");
            assertThat(aba.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 6);
        }
    }

    @Test void resumoTemCompetenciasHospitalMedicoEAbaForaDaLiberacao() throws Exception {
        configurar();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(service.previa(1))))) {
            var resumo=workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue()).contains("PREVIA").contains("Nada foi enviado");
            var t=texto(resumo);
            assertThat(t).contains("Hospital Demo","Medico(s)","Ana","Competencia da producao","09/2026","Competencia faturada pelo hospital","08/2026","Em revisao");
            var fora=workbook.getSheet("Fora da liberacao");
            assertThat(fora.getRow(1).getCell(0).getStringCellValue()).isEqualTo("C");
            assertThat(fora.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Maria Paciente");
            assertThat(fora.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Unimed");
            assertThat(fora.getRow(1).getCell(8).getStringCellValue()).isEqualTo("Obs C");
        }
    }

    @Test void statusEMesAnoNoFormatoDoModelo() {
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PAGA,"2026-08")).isEqualTo("PAGO 08/2026");
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PAGA,"")).isEqualTo("PAGO");
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PENDENTE,"2026-08")).isEqualTo("PENDENTE");
        assertThat(ExportadorPreviaLiberacao.mesAno("2026-09")).isEqualTo("09/2026");
        assertThat(ExportadorPreviaLiberacao.mesAnoDaData(LocalDate.of(2026,7,3))).isEqualTo("07/2026");
    }

    @Test void semVisitasForaDaLiberacaoNaoCriaAbaSobrando() throws Exception {
        var v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"LIBERADO","H",List.of("2026-09"),1,List.of(new LiberacaoRelatorioService.PreviaMedico(10,"Ana",List.of(v))),Map.of(),List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            assertThat(workbook.getSheet("Fora da liberacao")).isNull();
            assertThat(texto(workbook.getSheet("Resumo"))).contains("Liberado ao medico");
            // Sem competencia faturada conhecida, o status fica "PAGO" e a coluna em branco.
            assertThat(workbook.getSheet("Ana").getRow(6).getCell(9).getStringCellValue()).isEqualTo("PAGO");
        }
    }

    @Test void nomesDeAbaSaoValidosEUnicosMesmoComMedicosHomonimosOuNomesLongos() throws Exception {
        PortalMedicoController.Visita v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","X",1,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"EM_REVISAO","H",List.of(),3,List.of(
                new LiberacaoRelatorioService.PreviaMedico(1,"Ana",List.of(v)),new LiberacaoRelatorioService.PreviaMedico(2,"Ana",List.of(v)),
                new LiberacaoRelatorioService.PreviaMedico(3,"Dr: Nome/Muito?Longo [Que] Passa De Trinta E Um Caracteres",List.of(v))),Map.of(),List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            List<String> nomes=new ArrayList<>();for(int i=0;i<workbook.getNumberOfSheets();i++)nomes.add(workbook.getSheetName(i));
            assertThat(nomes).hasSize(4).doesNotHaveDuplicates().contains("Resumo","Ana","Ana (2)");
            assertThat(nomes).allSatisfy(n->{ assertThat(n.length()).isLessThanOrEqualTo(31); assertThat(n).doesNotContainPattern("[\\[\\]:*?/\\\\]"); });
        }
    }
}
