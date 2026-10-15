package br.com.faturamed.seguranca;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExportadorExtratoMedico {
    public byte[] exportar(ExtratoMedicoService.Extrato e) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var moeda=workbook.createCellStyle();moeda.setDataFormat(workbook.createDataFormat().getFormat("\"R$\" #,##0.00;[Red]-\"R$\" #,##0.00"));
            var data=workbook.createCellStyle();data.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            var header=workbook.createCellStyle();var font=workbook.createFont();font.setBold(true);header.setFont(font);header.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setWrapText(true);
            var resumo=workbook.createSheet("Resumo");var r=e.resumo();var f=e.filtros();
            Object[][] linhas={{"Medico",e.medico()},{"Hospital",e.hospital()},{"Relatorio",e.relatorio()},
                {"Gerado em (Brasilia)",e.geradoEm().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDateTime().toString()},
                {"Visitas de",f.visitaDe()},{"Visitas ate",f.visitaAte()},{"Recebimentos de",f.recebimentoDe()},{"Recebimentos ate",f.recebimentoAte()},
                {"Visitas nos filtros",r.visitas()},{"Faturado confirmado",r.faturado()},{"Recebido acumulado",r.recebidoAcumulado()},{"Saldo atual confirmado",r.saldoAtual()},
                {"Visitas aguardando faturamento",r.pendentes()},{"Valor informado aguardando faturamento",r.aguardandoFaturamento()},
                {"Visitas divergentes",r.divergentes()},{"Valor informado em conferencia",r.emConferencia()},{"Visitas sem valor informado",r.semValor()},
                {"Visitas com financeiro nao confirmado",r.financeiroNaoConfirmado()},{"Entradas no periodo",r.entradasPeriodo()},{"Estornos no periodo",r.estornosPeriodo()},{"Liquido no periodo",r.liquidoPeriodo()},
                {"Criterio","Saldo atual e recebido acumulado consideram todo o historico das visitas filtradas. O periodo de recebimentos filtra somente movimentos; nao produz saldo historico. Valores desconhecidos ficam em branco nos detalhes. Pendencias e divergencias nao compoem o saldo confirmado."}};
            for(int i=0;i<linhas.length;i++)preencher(resumo.createRow(i),linhas[i],moeda,data);
            resumo.setColumnWidth(0,48*256);resumo.setColumnWidth(1,85*256);var wrap=workbook.createCellStyle();wrap.setWrapText(true);resumo.getRow(linhas.length-1).getCell(1).setCellStyle(wrap);resumo.getRow(linhas.length-1).setHeightInPoints(72);
            var visitas=workbook.createSheet("Visitas");cabecalho(visitas,header,"Linha","Atendimento","Data da visita","Codigo","Procedimento","Conciliacao","Valor informado","Faturado","Recebido acumulado","Saldo atual","Situacao financeira");
            for(var v:e.visitas())preencher(visitas.createRow(visitas.getLastRowNum()+1),new Object[]{v.linha(),v.atendimento(),v.data(),v.codigo(),v.procedimento(),conciliacao(v.status()),v.valorInformado(),v.faturado(),v.recebido(),v.saldo(),situacao(v.situacao())},moeda,data);
            configurar(visitas,11);visitas.setColumnWidth(4,48*256);visitas.setColumnWidth(10,32*256);
            var movimentos=workbook.createSheet("Movimentos");cabecalho(movimentos,header,"Lancamento","Data do movimento","Linha","Atendimento","Data da visita","Codigo","Tipo","Valor","Recebimento estornado","Pagamento","Referencia");
            for(var m:e.movimentos())preencher(movimentos.createRow(movimentos.getLastRowNum()+1),new Object[]{m.id(),m.data(),m.linha(),m.atendimento(),m.dataVisita(),m.codigo(),m.estornoDe()==null?m.estornado()?"Recebimento (estornado)":"Recebimento":"Estorno",m.valor(),m.estornoDe(),m.pagamentoId(),m.referencia()},moeda,data);
            configurar(movimentos,11);movimentos.setColumnWidth(6,32*256);movimentos.setColumnWidth(10,40*256);workbook.write(output);return output.toByteArray();
        }
    }
    public static String conciliacao(String status) { return switch(status){case "PAGA"->"Faturado";case "PENDENTE"->"Aguardando faturamento";default->"Divergente";}; }
    public static String situacao(String status) { return switch(status){case "RECEBIDO"->"Recebido";case "PARCIAL"->"Recebido parcialmente";case "A_RECEBER"->"A receber";case "SEM_FATURAMENTO"->"Sem faturamento confirmado";default->"Financeiro em conferencia";}; }
    private void preencher(Row row,Object[] valores,CellStyle moeda,CellStyle data) {
        for(int i=0;i<valores.length;i++){var c=row.createCell(i);var v=valores[i];if(v instanceof BigDecimal b){c.setCellValue(b.doubleValue());c.setCellStyle(moeda);}else if(v instanceof LocalDate d){c.setCellValue(d);c.setCellStyle(data);}else if(v instanceof Number n)c.setCellValue(n.doubleValue());else if(v!=null)c.setCellValue(v.toString());}
    }
    private void cabecalho(Sheet sheet,CellStyle style,String... valores) {var row=sheet.createRow(0);row.setHeightInPoints(36);for(int i=0;i<valores.length;i++){var c=row.createCell(i);c.setCellValue(valores[i]);c.setCellStyle(style);}}
    private void configurar(Sheet sheet,int colunas) {sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,sheet.getLastRowNum(),0,colunas-1));for(int i=0;i<colunas;i++)sheet.setColumnWidth(i,24*256);}
}
