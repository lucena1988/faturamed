package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LeitorPlanilhaVisitasTest {
    @Test
    void importaProducaoComUmaColunaEPreservaRepeticoes() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Atendimento");
            sheet.createRow(1).createCell(0).setCellValue("00123");
            sheet.createRow(2).createCell(0).setCellValue("00123");
            workbook.write(output);
            var linhas = new LeitorPlanilhaVisitas().ler(new MockMultipartFile("producao", "medico.xlsx",
                    "application/octet-stream", output.toByteArray()), false);
            assertThat(linhas).hasSize(2);
            assertThat(linhas.getFirst().campo("Atendimento")).isEqualTo("00123");
            assertThat(linhas.getFirst().campo("Dt.")).isEmpty();
            assertThat(linhas.get(1).numero()).isEqualTo(3);
        }
    }
}
