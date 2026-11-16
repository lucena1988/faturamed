package br.com.faturamed.producao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Planilha consolidada enviada ao hospital: resumo por medico, calculo por agenda e lista de etiquetas. */
@Component
public class ExportadorFechamentoProducao {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> PERIODOS = Map.of("MANHA", "Manhã", "TARDE", "Tarde", "NOITE", "Noite", "INTEGRAL", "Integral");

    /** Campos enviados ao hospital na aba Etiquetas. */
    static final List<String> COLUNAS_ETIQUETAS = List.of("Data", "Atendimento", "Nome", "Tipo de exame", "Período", "Quantidade", "Valor");

    public byte[] exportar(FechamentoProducaoService.Detalhe d) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle titulo = estilo(wb, true, false, 14);
            CellStyle cab = estilo(wb, true, true, 11);
            CellStyle moeda = wb.createCellStyle();
            moeda.setDataFormat(wb.createDataFormat().getFormat("R$ #,##0.00"));
            CellStyle moedaNegrito = estilo(wb, true, false, 11);
            moedaNegrito.setDataFormat(moeda.getDataFormat());
            var f = d.fechamento();
            String periodo = f.dataInicio().format(DATA) + " a " + f.dataFim().format(DATA);

            Sheet resumo = wb.createSheet("Resumo");
            linha(resumo, 0, titulo, "Produção médica — " + f.hospital());
            linha(resumo, 1, null, "Período: " + periodo + "   ·   Fechamento nº " + f.id());
            linha(resumo, 3, cab, "Médico", "Agendas", "Exames", "Valor bruto");
            int r = 4;
            for (var m : d.resumo()) {
                Row row = linha(resumo, r++, null, m.medico(), m.agendas(), m.exames());
                valor(row, 3, m.total(), moeda);
            }
            Row tot = linha(resumo, r, cab, "TOTAL");
            valor(tot, 3, f.total(), moedaNegrito);
            ajustar(resumo, 4);

            Sheet calc = wb.createSheet("Cálculo por agenda");
            linha(calc, 0, titulo, "Cálculo por agenda — " + f.hospital() + " — " + periodo);
            linha(calc, 2, cab, "Médico", "Data", "Período", "Regra / descrição", "Exames", "Pontos", "Valor");
            r = 3;
            String medicoAtual = null;
            BigDecimal subtotal = BigDecimal.ZERO;
            for (var c : d.calculos()) {
                if (medicoAtual != null && !medicoAtual.equals(c.medico())) { subtotal(calc, r++, medicoAtual, subtotal, moedaNegrito, cab); subtotal = BigDecimal.ZERO; }
                medicoAtual = c.medico();
                Row row = linha(calc, r++, null, c.medico(), c.data() == null ? "—" : c.data().format(DATA),
                        c.periodo() == null ? "" : PERIODOS.getOrDefault(c.periodo(), c.periodo()), c.descricao(), c.exames(), c.pontos());
                valor(row, 6, c.valor(), moeda);
                subtotal = subtotal.add(c.valor());
            }
            if (medicoAtual != null) subtotal(calc, r++, medicoAtual, subtotal, moedaNegrito, cab);
            Row t2 = linha(calc, r + 1, cab, "TOTAL GERAL");
            valor(t2, 6, f.total(), moedaNegrito);
            ajustar(calc, 7);

            Sheet et = wb.createSheet("Etiquetas");
            // so os campos que o hospital usa; convenio, plano, carteirinha e guia nao saem da FaturaMed
            linha(et, 0, cab, COLUNAS_ETIQUETAS.toArray());
            r = 1;
            BigDecimal somaEtiquetas = BigDecimal.ZERO;
            for (var e : d.etiquetas().stream().filter(x -> "INCLUIDA".equals(x.situacao())).toList()) {
                BigDecimal v = e.valor() == null ? BigDecimal.ZERO : e.valor();
                Row row = linha(et, r++, null, e.data().format(DATA), e.nrAtendimento(), e.paciente(), e.exame(),
                        PERIODOS.getOrDefault(e.periodo(), e.periodo()), 1);
                valor(row, 6, v, moeda);
                somaEtiquetas = somaEtiquetas.add(v);
            }
            int ultima = Math.max(0, r - 1);
            BigDecimal fixos = f.total() == null ? BigDecimal.ZERO : f.total().subtract(somaEtiquetas);
            if (fixos.signum() != 0) valor(linha(et, r++, null, "Fixo mensal (não vinculado a etiquetas)"), 6, fixos, moeda);
            valor(linha(et, r, cab, "TOTAL"), 6, f.total(), moedaNegrito);
            et.setAutoFilter(new CellRangeAddress(0, ultima, 0, COLUNAS_ETIQUETAS.size() - 1));
            ajustar(et, COLUNAS_ETIQUETAS.size());

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a planilha", e);
        }
    }

    private static void subtotal(Sheet s, int r, String medico, BigDecimal v, CellStyle moeda, CellStyle cab) {
        Row row = linha(s, r, cab, "Subtotal " + medico);
        valor(row, 6, v, moeda);
    }

    private static Row linha(Sheet s, int r, CellStyle estilo, Object... valores) {
        Row row = s.createRow(r);
        for (int i = 0; i < valores.length; i++) {
            Cell c = row.createCell(i);
            if (valores[i] instanceof Number n) c.setCellValue(n.doubleValue());
            else c.setCellValue(valores[i] == null ? "" : valores[i].toString());
            if (estilo != null) c.setCellStyle(estilo);
        }
        return row;
    }

    private static void valor(Row row, int col, BigDecimal v, CellStyle estilo) {
        Cell c = row.createCell(col);
        c.setCellValue(v == null ? 0 : v.doubleValue());
        c.setCellStyle(estilo);
    }

    private static CellStyle estilo(Workbook wb, boolean negrito, boolean fundo, int tamanho) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(negrito);
        f.setFontHeightInPoints((short) tamanho);
        s.setFont(f);
        if (fundo) {
            s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    private static void ajustar(Sheet s, int colunas) {
        for (int i = 0; i < colunas; i++) { s.autoSizeColumn(i); s.setColumnWidth(i, Math.min(s.getColumnWidth(i) + 512, 60 * 256)); }
    }
}
