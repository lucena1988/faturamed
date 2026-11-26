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
    /** Linha do arquivo do hospital fora do relatorio, como a consulta devolve. */
    private Map<String,Object> exameDoHospital(long id,String atendimento,LocalDate data,String medico,String repasse,boolean comProducao) {
        Map<String,Object> m=new HashMap<>();
        m.put("id",id);m.put("atendimento",atendimento);m.put("data_visita",java.sql.Date.valueOf(data));m.put("medico_nome",medico);m.put("competencia","2026-08");
        m.put("codigo","40901106");m.put("procedimento","Ecodopplercardiograma");m.put("paciente","Carlos Paciente");m.put("convenio","Amil");
        m.put("repasse",repasse);m.put("valor_tot",null);m.put("regra",null);m.put("com_producao",comProducao);
        return m;
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
        when(medicos.identificarNome(eq("Ana"),anyList())).thenReturn(10L);
        when(jdbc.queryForObject(contains("with recursive"),eq(Long.class),anyLong())).thenReturn(1L);
        // A paga veio do arquivo do hospital de 08/2026 (faturamento pago em mes diferente da producao).
        when(jdbc.queryForList(contains("from vinculo_base_hospitalar b"),eq(1L))).thenReturn(List.of(Map.<String,Object>of("linha_producao",2,"competencia","2026-08")));
        when(jdbc.queryForList(contains("select competencia from arquivo_base_hospitalar"),eq(String.class),eq(1L))).thenReturn(List.of("2026-09"));
        // Mesmo faturamento, fora do relatorio: dois exames da Ana (um sem producao, um com producao em outro relatorio) e um de outro medico.
        when(jdbc.queryForList(contains("select v.id,v.atendimento,v.data_visita"),eq(1L),eq(1L),eq(1L))).thenReturn(List.of(
                exameDoHospital(901,"X1",LocalDate.of(2026,7,10),"Ana","133,06",false),
                exameDoHospital(902,"X2",LocalDate.of(2026,8,12),"Ana","146,56",true),
                exameDoHospital(903,"X3",LocalDate.of(2026,8,14),"Dr Outro","50,00",false)));
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
        // Exames do mesmo faturamento: cada medico recebe so os dele; o de outro medico entra apenas na contagem.
        assertThat(previa.outrosExames().get(10L)).extracting(o->o.atendimento()).containsExactly("X1","X2");
        assertThat(previa.outrosExames().get(10L)).extracting(o->o.comProducao()).containsExactly(false,true);
        assertThat(previa.outrosExames().get(10L).getFirst().repasse()).isEqualByComparingTo("133.06");
        assertThat(previa.deOutrosMedicos()).isEqualTo(1);
        assertThat(previa.repasseDeOutrosMedicos()).isEqualByComparingTo("50.00");
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

    @Test void excelDoMedicoTemCabecalhoComTotaisColunasOrigemStatusRepasseZeradoOrdemEFiltro() throws Exception {
        configurar();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(service.previa(1))))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            var aba=workbook.getSheet("Ana");
            // Cabecalho: medico, hospital, competencias e os totais que fecham com a tela do relatorio.
            assertThat(aba.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Ana");
            assertThat(aba.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Hospital Demo");
            assertThat(aba.getRow(2).getCell(1).getStringCellValue()).isEqualTo("09/2026");
            assertThat(aba.getRow(3).getCell(1).getStringCellValue()).isEqualTo("08/2026");
            assertThat(aba.getRow(4).getCell(0).getStringCellValue()).contains("neste relatorio");
            assertThat(aba.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(100.0);
            assertThat(aba.getRow(5).getCell(0).getStringCellValue()).contains("fora deste relatorio");
            assertThat(aba.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(279.62);
            assertThat(aba.getRow(6).getCell(0).getStringCellValue()).contains("Total faturado pelo hospital");
            assertThat(aba.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(379.62);
            // Tabela: cabecalho na linha 9 com as colunas do modelo (competencias, origem e status).
            var cab=aba.getRow(8);
            assertThat(cab.getCell(0).getStringCellValue()).isEqualTo("Atendimento");
            assertThat(cab.getCell(6).getStringCellValue()).isEqualTo("Comp. exame");
            assertThat(cab.getCell(7).getStringCellValue()).isEqualTo("Comp. producao");
            assertThat(cab.getCell(8).getStringCellValue()).isEqualTo("Comp. faturada (hospital)");
            assertThat(cab.getCell(9).getStringCellValue()).isEqualTo("Origem");
            assertThat(cab.getCell(10).getStringCellValue()).isEqualTo("Status");
            assertThat(cab.getCell(11).getStringCellValue()).isEqualTo("Repasse (R$)");
            // 1) Paga do relatorio, com paciente, convenio e status "PAGO 08/2026".
            var paga=aba.getRow(9);
            assertThat(paga.getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(paga.getCell(2).getStringCellValue()).isEqualTo("Maria Paciente");
            assertThat(paga.getCell(3).getStringCellValue()).isEqualTo("Unimed");
            assertThat(paga.getCell(4).getStringCellValue()).isEqualTo("40901106");
            assertThat(paga.getCell(6).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(7).getStringCellValue()).isEqualTo("09/2026");
            assertThat(paga.getCell(8).getStringCellValue()).isEqualTo("08/2026");
            assertThat(paga.getCell(9).getStringCellValue()).isEqualTo("Neste relatorio");
            assertThat(paga.getCell(10).getStringCellValue()).isEqualTo("PAGO 08/2026");
            assertThat(paga.getCell(11).getNumericCellValue()).isEqualTo(100.0);
            // 2) Exames pagos do mesmo faturamento fora do relatorio (de outros meses), por data do exame.
            var semProducao=aba.getRow(10);
            assertThat(semProducao.getCell(0).getStringCellValue()).isEqualTo("X1");
            assertThat(semProducao.getCell(2).getStringCellValue()).isEqualTo("Carlos Paciente");
            assertThat(semProducao.getCell(3).getStringCellValue()).isEqualTo("Amil");
            assertThat(semProducao.getCell(4).getStringCellValue()).isEqualTo("40901106");
            assertThat(semProducao.getCell(6).getStringCellValue()).isEqualTo("07/2026");
            assertThat(semProducao.getCell(7).getStringCellValue()).isEqualTo("Sem producao");
            assertThat(semProducao.getCell(8).getStringCellValue()).isEqualTo("08/2026");
            assertThat(semProducao.getCell(9).getStringCellValue()).isEqualTo(ExportadorPreviaLiberacao.ORIGEM_SEM_PRODUCAO);
            assertThat(semProducao.getCell(10).getStringCellValue()).isEqualTo("PAGO 08/2026");
            assertThat(semProducao.getCell(11).getNumericCellValue()).isEqualTo(133.06);
            assertThat(semProducao.getCell(12).getStringCellValue()).contains("Faturado pelo hospital em 08/2026").contains("exame de 07/2026").contains("sem producao");
            var comProducao=aba.getRow(11);
            assertThat(comProducao.getCell(0).getStringCellValue()).isEqualTo("X2");
            assertThat(comProducao.getCell(7).getStringCellValue()).isEqualTo("Outro relatorio");
            assertThat(comProducao.getCell(9).getStringCellValue()).isEqualTo(ExportadorPreviaLiberacao.ORIGEM_OUTRO_RELATORIO);
            assertThat(comProducao.getCell(12).getStringCellValue()).contains("outro relatorio");
            // 3) Divergente e depois pendente, sempre com repasse 0,00 e agrupadas no fim.
            var divergente=aba.getRow(12);
            assertThat(divergente.getCell(0).getStringCellValue()).isEqualTo("B");
            assertThat(divergente.getCell(10).getStringCellValue()).isEqualTo("DIVERGENTE");
            assertThat(divergente.getCell(11).getNumericCellValue()).isZero();
            var pendente=aba.getRow(13);
            assertThat(pendente.getCell(0).getStringCellValue()).isEqualTo("E");
            assertThat(pendente.getCell(2).getStringCellValue()).isEqualTo("Joao Silva");
            assertThat(pendente.getCell(4).getStringCellValue()).isEqualTo("99887766");
            assertThat(pendente.getCell(5).getStringCellValue()).isEqualTo("Doppler Venoso");
            assertThat(pendente.getCell(6).getStringCellValue()).isEqualTo("08/2026");
            assertThat(pendente.getCell(8).getStringCellValue()).isEmpty();
            assertThat(pendente.getCell(9).getStringCellValue()).isEqualTo("Neste relatorio");
            assertThat(pendente.getCell(10).getStringCellValue()).isEqualTo("PENDENTE");
            assertThat(pendente.getCell(11).getNumericCellValue()).isZero();
            // O exame de outro medico nao aparece na aba da Ana.
            assertThat(texto(aba)).doesNotContain("X3");
            // Filtro nos cabecalhos e painel congelado abaixo do cabecalho.
            assertThat(aba.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A9:M14");
            assertThat(aba.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 9);
        }
    }

    @Test void resumoTemCompetenciasTotaisQueFecham_ContagemDeOutrosMedicosEAbaForaDaLiberacao() throws Exception {
        configurar();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(service.previa(1))))) {
            var resumo=workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Relatório Conciliado mensal").doesNotContain("PREVIA");
            var t=texto(resumo);
            assertThat(t).contains("Hospital Demo","Medico(s)","Ana","Competencia da producao","09/2026","Competencia faturada pelo hospital","08/2026","Em revisao");
            assertThat(t).contains("Exames do mesmo faturamento fora deste relatorio","Exames do mesmo faturamento de outros medicos","Total faturado pelo hospital (R$)");
            // Linha da Ana na tabela do resumo: 100 neste relatorio, 2 outros exames de 279,62 e 379,62 no total.
            Row ana=null; for(Row row:resumo) if(row.getCell(0)!=null && "Ana".equals(row.getCell(0).toString()) && row.getCell(5)!=null && row.getCell(5).getCellType()==CellType.NUMERIC) ana=row;
            assertThat(ana).isNotNull();
            assertThat(ana.getCell(5).getNumericCellValue()).isEqualTo(100.0);
            assertThat(ana.getCell(6).getNumericCellValue()).isEqualTo(2.0);
            assertThat(ana.getCell(7).getNumericCellValue()).isEqualTo(279.62);
            assertThat(ana.getCell(8).getNumericCellValue()).isEqualTo(379.62);
            var fora=workbook.getSheet("Fora da liberacao");
            assertThat(fora.getRow(1).getCell(0).getStringCellValue()).isEqualTo("C");
            assertThat(fora.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Maria Paciente");
            assertThat(fora.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Unimed");
            assertThat(fora.getRow(1).getCell(8).getStringCellValue()).isEqualTo("Obs C");
        }
    }

    /** Previa de um medico so, com as visitas (codigo, nome) dadas, sem exames de outros meses. */
    private LiberacaoRelatorioService.Previa previaCom(List<PortalMedicoController.Visita> visitas) {
        return new LiberacaoRelatorioService.Previa(1,0,"EM_REVISAO","H",List.of("2026-09"),visitas.size(),
                List.of(new LiberacaoRelatorioService.PreviaMedico(10,"Ana",visitas)),Map.of(),Map.of(),0,BigDecimal.ZERO,List.of(),0);
    }
    private PortalMedicoController.Visita lista(int linha,String codigo,String nome) {
        return new PortalMedicoController.Visita(linha,"A"+linha,LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,nome,codigo,BigDecimal.TEN);
    }
    private Row linhaDoResumo(Sheet resumo,String primeiraCelula) { for(Row row:resumo) if(row.getCell(0)!=null && primeiraCelula.equals(row.getCell(0).toString())) return row; return null; }

    @Test void oRotuloSegueOTipo_Exames_Visitas_Consultas_EProcedimentosQuandoMistoOuSemTipo() throws Exception {
        record Caso(List<PortalMedicoController.Visita> visitas,String plural,String singular) {}
        var casos=List.of(
                new Caso(List.of(lista(1,"40901106","Ecodoppler"),lista(2,"40901360","Doppler Venoso")),"exames","exame"),
                new Caso(List.of(lista(1,"10102019","Visita hospitalar")),"visitas","visita"),
                new Caso(List.of(lista(1,"10101012","Consulta em consultorio")),"consultas","consulta"),
                new Caso(List.of(lista(1,"40901106","Ecodoppler"),lista(2,"10102019","Visita hospitalar")),"procedimentos","procedimento"),
                new Caso(List.of(lista(1,"40901106","Ecodoppler"),lista(2,"","")),"procedimentos","procedimento"));
        for(var caso:casos) {
            var previa=previaCom(caso.visitas());
            var sub=ExportadorPreviaLiberacao.substantivo(previa);
            assertThat(sub.plural()).as(caso.plural()).isEqualTo(caso.plural());
            assertThat(sub.singular()).isEqualTo(caso.singular());
            try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
                var resumo=workbook.getSheet("Resumo");
                var cabecalho=linhaDoResumo(resumo,"Medico");
                String esperado=Character.toUpperCase(caso.plural().charAt(0))+caso.plural().substring(1);
                assertThat(cabecalho.getCell(1).getStringCellValue()).isEqualTo(esperado);
                var t=texto(resumo);
                assertThat(t).contains(esperado+" no relatorio","Outros "+caso.plural()+" do faturamento","Repasse dos outros "+caso.plural());
                if(!caso.plural().equals("visitas")) assertThat(t).doesNotContain("Visitas no relatorio");
                assertThat(texto(workbook.getSheet("Ana"))).contains("Repasse de "+caso.plural()+" do mesmo faturamento");
            }
        }
    }

    @Test void oTituloSubstituiOAvisoDePreviaEOObservacaoUsaOSubstantivoDoTipo() throws Exception {
        var exame=lista(1,"40901106","Ecodoppler");
        var outro=new LiberacaoRelatorioService.PreviaOutroExame(900,"X1",LocalDate.of(2026,7,10),"40901106","Ecodoppler","","","2026-08",BigDecimal.TEN,false);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"EM_REVISAO","H",List.of("2026-09"),1,List.of(new LiberacaoRelatorioService.PreviaMedico(10,"Ana",List.of(exame))),
                Map.of(),Map.of(10L,List.of(outro)),0,BigDecimal.ZERO,List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            var a1=workbook.getSheet("Resumo").getRow(0).getCell(0);
            assertThat(a1.getStringCellValue()).isEqualTo("Relatório Conciliado mensal");
            assertThat(texto(workbook.getSheet("Resumo"))).doesNotContain("PREVIA","Nada foi enviado");
            assertThat(workbook.getFontAt(a1.getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(texto(workbook.getSheet("Ana"))).contains("(exame de 07/2026)");
        }
    }

    @Test void nomeDoArquivoEORelatorioConciliadoMensalComACompetenciaSemBarras() {
        assertThat(ExportadorPreviaLiberacao.nomeDoArquivo(List.of("2026-09"))).isEqualTo("Relatório Conciliado mensal - 09-2026.xlsx");
        assertThat(ExportadorPreviaLiberacao.nomeDoArquivo(List.of("2026-07","2026-08"))).isEqualTo("Relatório Conciliado mensal - 07-2026_08-2026.xlsx");
        assertThat(ExportadorPreviaLiberacao.nomeDoArquivo(List.of())).isEqualTo("Relatório Conciliado mensal.xlsx");
        assertThat(ExportadorPreviaLiberacao.nomeDoArquivo(List.of("2026-09"))).doesNotContain("/","\\");
    }

    @Test void statusEMesAnoNoFormatoDoModelo() {
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PAGA,"2026-08")).isEqualTo("PAGO 08/2026");
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PAGA,"")).isEqualTo("PAGO");
        assertThat(ExportadorPreviaLiberacao.statusTexto(ConciliadorVisitas.Status.PENDENTE,"2026-08")).isEqualTo("PENDENTE");
        assertThat(ExportadorPreviaLiberacao.mesAno("2026-09")).isEqualTo("09/2026");
        assertThat(ExportadorPreviaLiberacao.mesAnoDaData(LocalDate.of(2026,7,3))).isEqualTo("07/2026");
    }

    @Test void semOutrosExamesNemVisitasForaDaLiberacaoNaoCriaAbaSobrandoEOsTotaisFicamSoComORelatorio() throws Exception {
        var v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","Ana",10,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"LIBERADO","H",List.of("2026-09"),1,List.of(new LiberacaoRelatorioService.PreviaMedico(10,"Ana",List.of(v))),Map.of(),Map.of(),0,BigDecimal.ZERO,List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            assertThat(workbook.getSheet("Fora da liberacao")).isNull();
            assertThat(texto(workbook.getSheet("Resumo"))).contains("Liberado ao medico");
            var aba=workbook.getSheet("Ana");
            assertThat(aba.getRow(5).getCell(1).getNumericCellValue()).isZero();
            assertThat(aba.getRow(6).getCell(1).getNumericCellValue()).isEqualTo(10.0);
            // Sem competencia faturada conhecida, o status fica "PAGO" e a coluna em branco.
            assertThat(aba.getRow(9).getCell(10).getStringCellValue()).isEqualTo("PAGO");
        }
    }

    @Test void nomesDeAbaSaoValidosEUnicosMesmoComMedicosHomonimosOuNomesLongos() throws Exception {
        PortalMedicoController.Visita v=new PortalMedicoController.Visita(2,"A",LocalDate.of(2026,9,1),ConciliadorVisitas.Status.PAGA,"","X",1,"Eco","1",BigDecimal.TEN);
        var previa=new LiberacaoRelatorioService.Previa(1,0,"EM_REVISAO","H",List.of(),3,List.of(
                new LiberacaoRelatorioService.PreviaMedico(1,"Ana",List.of(v)),new LiberacaoRelatorioService.PreviaMedico(2,"Ana",List.of(v)),
                new LiberacaoRelatorioService.PreviaMedico(3,"Dr: Nome/Muito?Longo [Que] Passa De Trinta E Um Caracteres",List.of(v))),Map.of(),Map.of(),0,BigDecimal.ZERO,List.of(),0);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(previa)))) {
            List<String> nomes=new ArrayList<>();for(int i=0;i<workbook.getNumberOfSheets();i++)nomes.add(workbook.getSheetName(i));
            assertThat(nomes).hasSize(4).doesNotHaveDuplicates().contains("Resumo","Ana","Ana (2)");
            assertThat(nomes).allSatisfy(n->{ assertThat(n.length()).isLessThanOrEqualTo(31); assertThat(n).doesNotContainPattern("[\\[\\]:*?/\\\\]"); });
        }
    }
}
