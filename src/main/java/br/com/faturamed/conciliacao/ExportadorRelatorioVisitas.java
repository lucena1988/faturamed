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
        return exportar(relatorio, java.util.List.of());
    }

    public byte[] exportar(ConciliadorVisitas.Relatorio relatorio,
            java.util.List<RevisaoVisitasService.Revisao> revisoes) throws Exception {
        return exportar(relatorio, revisoes, null);
    }

    public byte[] exportar(ConciliadorVisitas.Relatorio relatorio,
            java.util.List<RevisaoVisitasService.Revisao> revisoes, String medico) throws Exception {
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
            preencher(resumo.createRow(1), medico == null ? new Object[]{"Arquivo medico", relatorio.arquivoMedico()}
                    : new Object[]{"Medico", relatorio.visitas().getFirst().medicoEfetivo()}, moeda, data);
            preencher(resumo.createRow(2), medico == null ? new Object[]{"Arquivo hospital", relatorio.arquivoHospital()}
                    : new Object[]{"Visitas do medico", relatorio.visitas().size()}, moeda, data);
            preencher(resumo.createRow(3), new Object[]{"Regra dos status", relatorio.regraStatus()}, moeda, data);
            int index = 5;
            for (var status : ConciliadorVisitas.Status.values()) {
                preencher(resumo.createRow(index++), new Object[]{status.name(), relatorio.resumo().get(status)}, moeda, data);
            }
            preencher(resumo.createRow(index), new Object[]{"Repasse das correspondencias pagas (R$)", relatorio.repasseCorrespondente()}, moeda, data);
            preencher(resumo.createRow(index + 2), new Object[]{"Valores", "Valores informados pelo hospital; preco contratado nao validado."}, moeda, data);
            int detalhe = index + 4;
            for (var status : ConciliadorVisitas.Status.values()) {
                var selecionadas = relatorio.visitas().stream().filter(v -> v.status() == status).toList();
                var valor = selecionadas.stream().map(ConciliadorVisitas.Visita::repasseEfetivo).filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                preencher(resumo.createRow(detalhe++), new Object[]{"Repasse conhecido " + status + " (R$)", valor}, moeda, data);
                preencher(resumo.createRow(detalhe++), new Object[]{"Visitas " + status + " sem repasse informado",
                        selecionadas.stream().filter(v -> v.repasseEfetivo() == null).count()}, moeda, data);
            }
            java.util.Map<Integer, RevisaoVisitaRequest.Acao> ultimas = new java.util.HashMap<>();
            revisoes.forEach(r -> ultimas.put(r.linhaMedico(), r.acao()));
            resumo.setColumnWidth(0, 48 * 256); resumo.setColumnWidth(1, 90 * 256);
            for (Row row : resumo) { row.setHeightInPoints(32); if (row.getCell(1) != null) {
                CellStyle wrap = workbook.createCellStyle(); wrap.cloneStyleFrom(row.getCell(1).getCellStyle());
                wrap.setWrapText(true); row.getCell(1).setCellStyle(wrap);
            } }
            String[] headers = {"Linha medico", "Data", "Atendimento", "Convenio", "Paciente", "Procedimento", "Medico",
                    "Valor Orig (R$)", "Status", "Codigo procedimento", "Conta paciente", "Valor hospital (R$)",
                    "Regra", "Repasse hospital (R$)", "Linha hospital", "Candidatos", "Motivo", "Origem da conciliacao"};
            Sheet visitas = workbook.createSheet("Visitas");
            cabecalho(visitas, headers, titulo);
            for (var visita : relatorio.visitas()) {
                var h = visita.hospital();
                preencher(visitas.createRow(visitas.getLastRowNum() + 1), new Object[]{
                        visita.linhaMedico(), visita.data(), visita.atendimento(),
                        originalOuHospital(visita, "convenio", h == null ? null : h.convenio()),
                        originalOuHospital(visita, "nome", h == null ? null : h.paciente()),
                        visita.procedimentoEfetivo(), visita.medicoEfetivo(),
                        visita.camposRevisados() != null ? visita.valorEfetivo() : visita.original().getOrDefault("valor orig", "").isBlank()
                                ? (h == null ? null : h.valorTotal()) : visita.original().get("valor orig"), visita.status().name(),
                        visita.codigoEfetivo(), h == null ? null : h.conta(), visita.valorEfetivo(),
                        h == null ? null : h.regra(), visita.repasseEfetivo(), h == null ? null : h.linha(),
                        medico == null ? visita.candidatos().size() : null, visita.motivo(), origem(ultimas.get(visita.linhaMedico()))}, moeda, data);
            }
            configurar(visitas, headers.length);
            if (medico == null) {
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
            }
            if (medico == null && !revisoes.isEmpty()) {
                Sheet historico = workbook.createSheet("Revisoes");
                String[] reviewHeaders = {"Revisao", "Linha medico", "Data da decisao (UTC)", "Responsavel", "Acao",
                        "Status anterior", "Status revisado", "Linha hospital anterior", "Linha hospital revisada", "Justificativa", "Campos alterados"};
                cabecalho(historico, reviewHeaders, titulo);
                for (var revisao : revisoes) preencher(historico.createRow(historico.getLastRowNum() + 1), new Object[]{
                        revisao.id(), revisao.linhaMedico(), revisao.criadoEm().toString(), revisao.responsavel(), revisao.acao().name(),
                        revisao.antes().status().name(), revisao.depois().status().name(),
                        revisao.antes().hospital() == null ? null : revisao.antes().hospital().linha(),
                        revisao.depois().hospital() == null ? null : revisao.depois().hospital().linha(), revisao.justificativa(),
                        alteracoes(revisao.antes(), revisao.depois())}, moeda, data);
                configurar(historico, reviewHeaders.length);
                historico.setColumnWidth(9, 85 * 256);
                historico.setColumnWidth(10, 85 * 256);
                CellStyle wrap = workbook.createCellStyle(); wrap.setWrapText(true);
                for (int i = 1; i <= historico.getLastRowNum(); i++) {
                    Cell cell = historico.getRow(i).getCell(10); cell.setCellStyle(wrap);
                    int lines = java.util.Arrays.stream(cell.getStringCellValue().split("\n", -1))
                            .mapToInt(line -> Math.max(1, (line.length() + 79) / 80)).sum();
                    historico.getRow(i).setHeightInPoints(Math.min(409, 16 * lines));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String origem(RevisaoVisitaRequest.Acao acao) {
        if (acao == null || acao == RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO) return "Importacao automatica";
        return acao == RevisaoVisitaRequest.Acao.CORRESPONDENCIA_AUTOMATICA ? "Automatica apos revisao" : "Revisao manual";
    }

    private String originalOuHospital(ConciliadorVisitas.Visita visita, String campo, String hospital) {
        String original = visita.original().getOrDefault(campo, "");
        return original.isBlank() ? hospital : original;
    }

    private String alteracoes(ConciliadorVisitas.Visita antes, ConciliadorVisitas.Visita depois) {
        String[] labels = {"Data", "Atendimento", "Medico", "Procedimento", "Codigo", "Valor hospital", "Repasse"};
        Object[] old = {antes.data(), antes.atendimento(), antes.medicoEfetivo(), antes.procedimentoEfetivo(),
                antes.codigoEfetivo(), antes.valorEfetivo(), antes.repasseEfetivo()};
        Object[] updated = {depois.data(), depois.atendimento(), depois.medicoEfetivo(), depois.procedimentoEfetivo(),
                depois.codigoEfetivo(), depois.valorEfetivo(), depois.repasseEfetivo()};
        var changes = new java.util.ArrayList<String>();
        for (int i = 0; i < labels.length; i++) if (!java.util.Objects.equals(old[i], updated[i])) {
            changes.add(labels[i] + ": " + java.util.Objects.toString(old[i], "Nao informado")
                    + " -> " + java.util.Objects.toString(updated[i], "Nao informado"));
        }
        return String.join("\n", changes);
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
        for (int i = 0; i < colunas; i++) sheet.setColumnWidth(i, (i == 16 && colunas > 16 ? 85 : 28) * 256);
    }
}
