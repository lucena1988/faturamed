package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class EnvioHospitalCamposTest {
    private static final LocalDate D = LocalDate.of(2026, 11, 9);

    private static FechamentoProducaoService.Calculo calc(Long lote, long medico, String valor) {
        return new FechamentoProducaoService.Calculo(lote, D, "MANHA", medico, "Dr " + medico, 1L, "regra", 3, 0, new BigDecimal(valor));
    }

    @Test
    void rateiaValorDaAgendaEntreEtiquetasSemPerderCentavos() {
        var valores = FechamentoProducaoService.ratear(List.of(calc(10L, 1, "100.00"), calc(null, 1, "500.00")), List.of(
                new FechamentoProducaoService.RefItem(1, 10, 1L), new FechamentoProducaoService.RefItem(2, 10, 1L),
                new FechamentoProducaoService.RefItem(3, 10, 1L)));
        assertEquals(new BigDecimal("33.33"), valores.get(1L));
        assertEquals(new BigDecimal("33.34"), valores.get(3L));
        assertEquals(new BigDecimal("100.00"), valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void abaEtiquetasLevaSoOsCamposDoHospital() throws Exception {
        var f = new FechamentoProducaoService.Fechamento(7, 1, "Hospital X", D, D, "AGUARDANDO", new BigDecimal("100.00"), null, null,
                null, null, null, 1, null, null, 0, null, null, null);
        var e = new FechamentoProducaoService.Etiqueta(D, "MANHA", "Dr 1", "Eletrocardiograma", "57782116", "Maria", "CONV-SECRETO",
                "PLANO-SECRETO", "CART-SECRETA", "GUIA-SECRETA", 1, 10, "INCLUIDA", null, null, null, 1L, new BigDecimal("100.00"));
        var d = new FechamentoProducaoService.Detalhe(f, List.of(calc(10L, 1, "100.00")), List.of(), List.of(e));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(new ExportadorFechamentoProducao().exportar(d)))) {
            Sheet et = wb.getSheet("Etiquetas");
            List<String> cab = new ArrayList<>();
            et.getRow(0).forEach(c -> cab.add(c.getStringCellValue()));
            assertEquals(List.of("Data", "Atendimento", "Nome", "Tipo de exame", "Período", "Quantidade", "Valor"), cab);
            Row r = et.getRow(1);
            assertEquals("57782116", r.getCell(1).getStringCellValue());
            assertEquals("Manhã", r.getCell(4).getStringCellValue());
            assertEquals(1.0, r.getCell(5).getNumericCellValue());
            assertEquals(100.0, r.getCell(6).getNumericCellValue());
            DataFormatter fmt = new DataFormatter();
            for (Row row : et) for (Cell c : row) assertFalse(fmt.formatCellValue(c).contains("SECRET"), "dado sensivel na planilha");
        }
    }
}
