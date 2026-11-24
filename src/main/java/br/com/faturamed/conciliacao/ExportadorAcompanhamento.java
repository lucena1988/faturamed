package br.com.faturamed.conciliacao;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Relatorio do acompanhamento por etapa com os mesmos dados e filtros da tela. */
@Component
public class ExportadorAcompanhamento {
    public record Filtros(String competencia,String etapa,String medico,String empresa) {}

    private static final Map<AcompanhamentoBaseHospitalarService.Etapa,String> ETAPAS=Map.of(
            AcompanhamentoBaseHospitalarService.Etapa.AGUARDANDO_PRODUCAO,"Aguardando producao",
            AcompanhamentoBaseHospitalarService.Etapa.AGUARDANDO_FATURAMENTO,"Aguardando faturamento",
            AcompanhamentoBaseHospitalarService.Etapa.FATURADO_SEM_RECEBIMENTO,"Faturado sem recebimento",
            AcompanhamentoBaseHospitalarService.Etapa.RECEBIDO_PARCIALMENTE,"Recebido parcialmente",
            AcompanhamentoBaseHospitalarService.Etapa.SEM_SALDO,"Sem saldo a receber",
            AcompanhamentoBaseHospitalarService.Etapa.EM_CONFERENCIA,"Em conferencia");
    private static final Set<AcompanhamentoBaseHospitalarService.Etapa> PAGAS=EnumSet.of(
            AcompanhamentoBaseHospitalarService.Etapa.FATURADO_SEM_RECEBIMENTO,
            AcompanhamentoBaseHospitalarService.Etapa.RECEBIDO_PARCIALMENTE,
            AcompanhamentoBaseHospitalarService.Etapa.SEM_SALDO);

    /** Empresa: vazio = todas, "SEM" = sem empresa, numero = id da empresa. */
    public static List<AcompanhamentoBaseHospitalarService.Item> filtrar(List<AcompanhamentoBaseHospitalarService.Item> itens,Filtros f) {
        return itens.stream()
                .filter(i->vazio(f.etapa()) || i.etapa().name().equals(f.etapa()))
                .filter(i->vazio(f.medico()) || f.medico().equals(i.medico()))
                .filter(i->vazio(f.empresa()) || ("SEM".equals(f.empresa()) ? i.empresaId()==null : f.empresa().equals(String.valueOf(i.empresaId()))))
                .toList();
    }

    public byte[] exportar(String hospital,Filtros filtros,List<AcompanhamentoBaseHospitalarService.Item> itens) throws Exception {
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            CellStyle moeda=wb.createCellStyle(); moeda.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle data=wb.createCellStyle(); data.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle titulo=wb.createCellStyle(); Font negrito=wb.createFont(); negrito.setBold(true); titulo.setFont(negrito);
            titulo.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex()); titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND); titulo.setWrapText(true);

            Sheet resumo=wb.createSheet("Resumo");
            List<Object[]> linhas=new ArrayList<>(List.of(
                    new Object[]{"Hospital",hospital},
                    new Object[]{"Competencia",vazio(filtros.competencia())?"Todas":mesAno(filtros.competencia())},
                    new Object[]{"Etapa",vazio(filtros.etapa())?"Todas":ETAPAS.get(AcompanhamentoBaseHospitalarService.Etapa.valueOf(filtros.etapa()))},
                    new Object[]{"Medico",vazio(filtros.medico())?"Todos":filtros.medico()},
                    new Object[]{"Empresa",vazio(filtros.empresa())?"Todas":"SEM".equals(filtros.empresa())?"Sem empresa":itens.stream().map(AcompanhamentoBaseHospitalarService.Item::empresa).filter(Objects::nonNull).findFirst().orElse("—")},
                    new Object[]{"Gerado em",LocalDate.now()},
                    new Object[]{"Procedimentos",itens.size()}));
            for(var etapa:AcompanhamentoBaseHospitalarService.Etapa.values())
                linhas.add(new Object[]{ETAPAS.get(etapa),itens.stream().filter(i->i.etapa()==etapa).count()});
            linhas.add(new Object[]{"Faturado confirmado (R$)",soma(itens,AcompanhamentoBaseHospitalarService.Item::faturado)});
            linhas.add(new Object[]{"Recebido confirmado (R$)",soma(itens,AcompanhamentoBaseHospitalarService.Item::recebido)});
            linhas.add(new Object[]{"Saldo confirmado (R$)",soma(itens,AcompanhamentoBaseHospitalarService.Item::saldo)});
            for(int i=0;i<linhas.size();i++) preencher(resumo.createRow(i),linhas.get(i),moeda,data);
            resumo.setColumnWidth(0,30*256); resumo.setColumnWidth(1,50*256);

            Sheet sheet=wb.createSheet("Acompanhamento");
            String[] cab={"ID fixo","Atendimento","Data","Medico","Empresa","Comp. producao","Comp. faturada (hospital)","Status","Etapa","Faturado (R$)","Recebido (R$)","Saldo (R$)"};
            Row h=sheet.createRow(0);
            for(int i=0;i<cab.length;i++){ Cell c=h.createCell(i); c.setCellValue(cab[i]); c.setCellStyle(titulo); }
            for(var i:itens) {
                String status=PAGAS.contains(i.etapa())?"PAGO "+mesAno(i.competencia()):ETAPAS.get(i.etapa());
                preencher(sheet.createRow(sheet.getLastRowNum()+1),new Object[]{i.visitaId(),i.atendimento(),i.data(),i.medico(),
                        Objects.toString(i.empresa(),"—"),mesAno(i.competenciaProducao()),mesAno(i.competencia()),status,ETAPAS.get(i.etapa()),
                        valor(i.faturado()),valor(i.recebido()),valor(i.saldo())},moeda,data);
            }
            int[] larguras={10,14,12,32,30,14,18,22,24,14,14,14};
            for(int i=0;i<larguras.length;i++) sheet.setColumnWidth(i,larguras[i]*256);
            sheet.createFreezePane(0,1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,sheet.getLastRowNum()),0,cab.length-1));
            wb.write(out); return out.toByteArray();
        }
    }

    private static Object valor(BigDecimal v) { return v==null?"Nao confirmado":v; }
    private static BigDecimal soma(List<AcompanhamentoBaseHospitalarService.Item> itens,java.util.function.Function<AcompanhamentoBaseHospitalarService.Item,BigDecimal> campo) {
        return itens.stream().map(campo).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static boolean vazio(String s) { return s==null||s.isBlank(); }
    private static String mesAno(String comp) { return vazio(comp)?"—":comp.length()==7?comp.substring(5)+"/"+comp.substring(0,4):comp; }
    private static void preencher(Row row,Object[] valores,CellStyle moeda,CellStyle data) {
        for(int i=0;i<valores.length;i++) {
            Cell c=row.createCell(i); Object v=valores[i];
            if(v instanceof BigDecimal b){ c.setCellValue(b.doubleValue()); c.setCellStyle(moeda); }
            else if(v instanceof Number n) c.setCellValue(n.doubleValue());
            else if(v instanceof LocalDate d){ c.setCellValue(d); c.setCellStyle(data); }
            else c.setCellValue(Objects.toString(v,""));
        }
    }
}
