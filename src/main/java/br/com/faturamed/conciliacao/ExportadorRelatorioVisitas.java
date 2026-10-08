package br.com.faturamed.conciliacao;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExportadorRelatorioVisitas {
    public byte[] exportar(ConciliadorVisitas.Relatorio relatorio) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            CellStyle moeda = workbook.createCellStyle();
            moeda.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00########"));
            CellStyle data = workbook.createCellStyle();
            data.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle titulo = workbook.createCellStyle();
            Font font = workbook.createFont(); font.setBold(true); titulo.setFont(font);
            titulo.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Sheet resumo = workbook.createSheet("Resumo");
            preencher(resumo.createRow(0), new Object[]{"Hospital", relatorio.hospital()}, moeda, data);
            preencher(resumo.createRow(1), new Object[]{"Arquivo medico", relatorio.arquivoMedico()}, moeda, data);
            preencher(resumo.createRow(2), new Object[]{"Arquivo hospital", relatorio.arquivoHospital()}, moeda, data);
            preencher(resumo.createRow(3), new Object[]{"Regra dos status", relatorio.regraStatus()}, moeda, data);
            int index = 5;
            for (var status : ConciliadorVisitas.Status.values()) {
                preencher(resumo.createRow(index++), new Object[]{status.name(), relatorio.resumo().get(status)}, moeda, data);
            }
            preencher(resumo.createRow(index), new Object[]{"Repasse das correspondencias pagas (R$)", relatorio.repasseCorrespondente()}, moeda, data);
            preencher(resumo.createRow(index + 2), new Object[]{"Valores", "Valores informados pelo hospital; preco contratado nao validado."}, moeda, data);
            resumo.setColumnWidth(0, 48 * 256); resumo.setColumnWidth(1, 90 * 256);
            for (Row row : resumo) { row.setHeightInPoints(32); if (row.getCell(1) != null) {
                CellStyle wrap = workbook.createCellStyle(); wrap.cloneStyleFrom(row.getCell(1).getCellStyle());
                wrap.setWrapText(true); row.getCell(1).setCellStyle(wrap);
            } }
            String[] headers = {"Linha medico", "Data", "Atendimento", "Convenio", "Paciente", "Procedimento", "Medico",
                    "Valor Orig (R$)", "Status", "Codigo procedimento", "Conta paciente", "Valor hospital (R$)",
                    "Regra", "Repasse hospital (R$)", "Linha hospital", "Candidatos", "Motivo"};
            Sheet visitas = workbook.createSheet("Visitas");
            cabecalho(visitas, headers, titulo);
            for (var visita : relatorio.visitas()) {
                var h = visita.hospital();
                preencher(visitas.createRow(visitas.getLastRowNum() + 1), new Object[]{
                        visita.linhaMedico(), visita.data(), visita.atendimento(),
                        originalOuHospital(visita, "convenio", h == null ? null : h.convenio()),
                        originalOuHospital(visita, "nome", h == null ? null : h.paciente()),
                        originalOuHospital(visita, "procedimento/mat-med", h == null ? null : h.procedimento()),
                        originalOuHospital(visita, "medico", h == null ? null : h.medico()),
                        visita.original().getOrDefault("valor orig", "").isBlank()
                                ? (h == null ? null : h.valorTotal()) : visita.original().get("valor orig"), visita.status().name(),
                        h == null ? null : h.codigo(), h == null ? null : h.conta(), h == null ? null : h.valorTotal(),
                        h == null ? null : h.regra(), h == null ? null : h.repasse(), h == null ? null : h.linha(),
                        visita.candidatos().size(), visita.motivo()}, moeda, data);
            }
            configurar(visitas, headers.length);
            Sheet candidatos = workbook.createSheet("Candidatos hospital");
            String[] detailHeaders = {"Linha medico", "Linha hospital", "Data", "Atendimento", "Medico", "Codigo", "Procedimento",
                    "Conta", "Valor total (R$)", "Regra", "Repasse (R$)", "Setor"};
            cabecalho(candidatos, detailHeaders, titulo);
            for (var visita : relatorio.visitas()) if (visita.hospital() == null) {
                for (var h : visita.candidatos()) hospital(candidatos, visita.linhaMedico(), h, moeda, data);
            }
            configurar(candidatos, detailHeaders.length);
            Sheet semProducao = workbook.createSheet("Hospital sem producao");
            cabecalho(semProducao, detailHeaders, titulo);
            for (var h : relatorio.hospitalSemProducao()) hospital(semProducao, null, h, moeda, data);
            configurar(semProducao, detailHeaders.length);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String originalOuHospital(ConciliadorVisitas.Visita visita, String campo, String hospital) {
        String original = visita.original().getOrDefault(campo, "");
        return original.isBlank() ? hospital : original;
    }

    private void hospital(Sheet sheet, Integer linhaMedico, ConciliadorVisitas.RegistroHospital h, CellStyle moeda, CellStyle data) {
        preencher(sheet.createRow(sheet.getLastRowNum() + 1), new Object[]{linhaMedico, h.linha(), h.data(), h.atendimento(),
                h.medico(), h.codigo(), h.procedimento(), h.conta(), h.valorTotal(), h.regra(), h.repasse(), h.setor()}, moeda, data);
    }

    private void preencher(Row row, Object[] valores, CellStyle moeda, CellStyle data) {
        for (int i = 0; i < valores.length; i++) {
            Cell cell = row.createCell(i); Object valor = valores[i];
            if (valor instanceof LocalDate date) { cell.setCellValue(date); cell.setCellStyle(data); }
            else if (valor instanceof BigDecimal decimal) { cell.setCellValue(decimal.doubleValue()); cell.setCellStyle(moeda); }
            else if (valor instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (valor != null) cell.setCellValue(valor.toString());
        }
    }

    private void cabecalho(Sheet sheet, String[] headers, CellStyle estilo) {
        Row row = sheet.createRow(0); row.setHeightInPoints(30);
        for (int i = 0; i < headers.length; i++) { Cell cell = row.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(estilo); }
    }

    private void configurar(Sheet sheet, int colunas) {
        sheet.createFreezePane(3, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, sheet.getLastRowNum(), 0, colunas - 1));
        for (int i = 0; i < colunas; i++) sheet.setColumnWidth(i, (i == colunas - 1 && colunas > 12 ? 85 : 28) * 256);
    }
}
