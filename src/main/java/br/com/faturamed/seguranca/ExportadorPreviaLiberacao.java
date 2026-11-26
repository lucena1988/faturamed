package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Previa em Excel do que sera liberado ao medico: resumo, uma aba por medico com as visitas como ele as vera no portal
 * e uma aba com o que ficaria de fora. Para conferir antes de dar o ok da liberacao.
 */
@Component
public class ExportadorPreviaLiberacao {
    private static final Map<ConciliadorVisitas.Status,String> SITUACAO=Map.of(ConciliadorVisitas.Status.PAGA,"Paga",ConciliadorVisitas.Status.PENDENTE,"Pendente",
            ConciliadorVisitas.Status.DIVERGENTE,"Divergente",ConciliadorVisitas.Status.EXCLUIDA,"Excluida");

    public byte[] exportar(LiberacaoRelatorioService.Previa previa) throws Exception {
        try(var workbook=new XSSFWorkbook();var saida=new ByteArrayOutputStream()) {
            CellStyle moeda=workbook.createCellStyle();moeda.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle data=workbook.createCellStyle();data.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            Font negrito=workbook.createFont();negrito.setBold(true);
            CellStyle titulo=workbook.createCellStyle();titulo.setFont(negrito);titulo.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);titulo.setWrapText(true);
            CellStyle destaque=workbook.createCellStyle();destaque.setFont(negrito);destaque.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());destaque.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle total=workbook.createCellStyle();total.setFont(negrito);
            CellStyle totalMoeda=workbook.createCellStyle();totalMoeda.cloneStyleFrom(moeda);totalMoeda.setFont(negrito);

            Sheet resumo=workbook.createSheet("Resumo");
            var geradaEm=LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            int linha=0;
            Row aviso=resumo.createRow(linha++);aviso.createCell(0).setCellValue("PREVIA - conferencia interna. Nada foi enviado ao medico.");aviso.getCell(0).setCellStyle(destaque);
            String situacao="LIBERADO".equals(previa.situacao())?"Liberado ao medico (esta revisao ja foi publicada)":"Em revisao (ainda nao liberado)";
            long noPortal=previa.medicos().stream().mapToLong(m->m.visitas().size()).sum();
            Object[][] itens={{"Hospital",previa.hospital()},{"Relatorio",previa.relatorio()},{"Revisao",previa.versao()},{"Situacao atual",situacao},{"Gerada em",geradaEm},
                    {"Visitas no relatorio (sem excluidas)",previa.visitas()},{"Visitas que os medicos passam a ver",noPortal},
                    {"Visitas sem medico cadastrado (nao aparecem no portal)",previa.semMedico().size()},{"Linhas excluidas por duplicidade (nao vao ao medico)",previa.excluidas()}};
            for(Object[] item:itens){Row row=resumo.createRow(linha++);row.createCell(0).setCellValue(item[0].toString());preencher(row.createCell(1),item[1]);}
            linha++;
            Row cab=resumo.createRow(linha++);int c=0;
            for(String rotulo:new String[]{"Medico","Visitas","Pagas","Pendentes","Divergentes","Repasse das pagas (R$)"}){Cell cell=cab.createCell(c++);cell.setCellValue(rotulo);cell.setCellStyle(titulo);}
            BigDecimal repasseGeral=BigDecimal.ZERO;
            for(var medico:previa.medicos()) {
                Row row=resumo.createRow(linha++);BigDecimal repasse=repasseDasPagas(medico.visitas());repasseGeral=repasseGeral.add(repasse);
                row.createCell(0).setCellValue(medico.nome());row.createCell(1).setCellValue(medico.visitas().size());
                row.createCell(2).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.PAGA));row.createCell(3).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.PENDENTE));
                row.createCell(4).setCellValue(contar(medico.visitas(),ConciliadorVisitas.Status.DIVERGENTE));Cell valor=row.createCell(5);valor.setCellValue(repasse.doubleValue());valor.setCellStyle(moeda);
            }
            Row soma=resumo.createRow(linha);soma.createCell(0).setCellValue("Total");soma.createCell(1).setCellValue(noPortal);Cell somaValor=soma.createCell(5);somaValor.setCellValue(repasseGeral.doubleValue());somaValor.setCellStyle(totalMoeda);
            for(int i=0;i<=1;i++)soma.getCell(i).setCellStyle(total);
            resumo.setColumnWidth(0,58*256);resumo.setColumnWidth(1,34*256);for(int i=2;i<=5;i++)resumo.setColumnWidth(i,16*256);resumo.setColumnWidth(5,24*256);

            Set<String> nomes=new HashSet<>(List.of("resumo","fora da liberacao"));
            for(var medico:previa.medicos()) {
                String nome=nomeUnico(workbook,medico.nome(),nomes);
                Sheet aba=workbook.createSheet(nome);
                String[] cabecalho={"Atendimento","Data","Codigo do procedimento","Procedimento","Situacao","Repasse (R$)","Observacao"};
                Row topo=aba.createRow(0);for(int i=0;i<cabecalho.length;i++){Cell cell=topo.createCell(i);cell.setCellValue(cabecalho[i]);cell.setCellStyle(titulo);}
                int r=1;
                for(var v:medico.visitas()) {
                    Row row=aba.createRow(r++);row.createCell(0).setCellValue(v.atendimento()==null?"":v.atendimento());
                    Cell d=row.createCell(1);if(v.data()!=null){d.setCellValue(v.data());d.setCellStyle(data);}
                    row.createCell(2).setCellValue(v.codigoProcedimento()==null?"":v.codigoProcedimento());row.createCell(3).setCellValue(v.procedimento()==null?"":v.procedimento());
                    row.createCell(4).setCellValue(SITUACAO.getOrDefault(v.status(),String.valueOf(v.status())));
                    Cell valor=row.createCell(5);if(v.repasse()!=null){valor.setCellValue(v.repasse().doubleValue());valor.setCellStyle(moeda);}
                    row.createCell(6).setCellValue(v.motivo()==null?"":v.motivo());
                }
                Row fim=aba.createRow(r);fim.createCell(0).setCellValue("Repasse das pagas");fim.getCell(0).setCellStyle(total);
                Cell soma2=fim.createCell(5);soma2.setCellValue(repasseDasPagas(medico.visitas()).doubleValue());soma2.setCellStyle(totalMoeda);
                aba.createFreezePane(0,1);
                int[] larguras={16,13,24,48,13,16,70};for(int i=0;i<larguras.length;i++)aba.setColumnWidth(i,larguras[i]*256);
            }
            if(!previa.semMedico().isEmpty()) {
                Sheet fora=workbook.createSheet("Fora da liberacao");
                String[] cabecalho={"Atendimento","Data","Codigo do procedimento","Procedimento","Situacao","Motivo"};
                Row topo=fora.createRow(0);for(int i=0;i<cabecalho.length;i++){Cell cell=topo.createCell(i);cell.setCellValue(cabecalho[i]);cell.setCellStyle(titulo);}
                int r=1;
                for(var v:previa.semMedico()) {
                    Row row=fora.createRow(r++);row.createCell(0).setCellValue(v.atendimento()==null?"":v.atendimento());
                    Cell d=row.createCell(1);if(v.data()!=null){d.setCellValue(v.data());d.setCellStyle(data);}
                    row.createCell(2).setCellValue(v.codigo()==null?"":v.codigo());row.createCell(3).setCellValue(v.procedimento()==null?"":v.procedimento());
                    row.createCell(4).setCellValue(SITUACAO.getOrDefault(v.status(),String.valueOf(v.status())));row.createCell(5).setCellValue(v.motivo()==null?"":v.motivo());
                }
                fora.createFreezePane(0,1);int[] larguras={16,13,24,48,13,70};for(int i=0;i<larguras.length;i++)fora.setColumnWidth(i,larguras[i]*256);
            }
            workbook.write(saida);return saida.toByteArray();
        }
    }

    private static void preencher(Cell cell,Object valor) {
        if(valor instanceof Number n)cell.setCellValue(n.doubleValue());else if(valor instanceof LocalDate d)cell.setCellValue(d.toString());else cell.setCellValue(String.valueOf(valor));
    }
    private static long contar(List<PortalMedicoController.Visita> visitas,ConciliadorVisitas.Status status) { return visitas.stream().filter(v->v.status()==status).count(); }
    /** Repasse so das visitas pagas, como o resumo do relatorio; sem repasse conhecido nao entra na soma. */
    static BigDecimal repasseDasPagas(List<PortalMedicoController.Visita> visitas) {
        return visitas.stream().filter(v->v.status()==ConciliadorVisitas.Status.PAGA && v.repasse()!=null).map(PortalMedicoController.Visita::repasse).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    /** Nome de aba valido (31 caracteres, sem caracteres proibidos) e unico no arquivo. */
    private static String nomeUnico(Workbook workbook,String desejado,Set<String> usados) {
        String base=WorkbookUtil.createSafeSheetName(desejado==null||desejado.isBlank()?"Medico":desejado);
        String nome=base;int n=2;
        while(!usados.add(nome.toLowerCase(Locale.ROOT))) {
            String sufixo=" ("+n+++")";nome=(base.length()+sufixo.length()>31?base.substring(0,31-sufixo.length()):base)+sufixo;
        }
        return nome;
    }
}
