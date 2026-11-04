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

    @Test
    void producaoAceitaAtendimentoVazioOuColunaAusenteMasHospitalNao() throws Exception {
        assertThat(ler(false, new String[][] {{"Dt.", "Medico"}, {"2026-01-05", "Medico A"}}).getFirst().campo("Atendimento")).isEmpty();
        var vazio = ler(false, new String[][] {{"Atendimento", "Dt."}, {"", "2026-01-05"}, {"00123", "2026-01-06"}});
        assertThat(vazio).hasSize(2);
        assertThat(vazio.getFirst().campo("Atendimento")).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ler(true, new String[][] {
                {"Atendimento", "Data Consumo", "Medico", "Cod Produto", "Valor Tot", "Vl. A Repassar", "Setor"},
                {"", "2026-01-05", "Medico A", "10102019", "100", "100", "Faturado"}}))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Atendimento ausente");
    }

    private java.util.List<LeitorPlanilhaVisitas.Linha> ler(boolean hospital, String[][] linhas) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Planilha2");
            for (int i = 0; i < linhas.length; i++) {
                var row = sheet.createRow(i);
                for (int j = 0; j < linhas[i].length; j++) row.createCell(j).setCellValue(linhas[i][j]);
            }
            workbook.write(output);
            return new LeitorPlanilhaVisitas().ler(new MockMultipartFile("arquivo", "planilha.xlsx", "application/octet-stream", output.toByteArray()), hospital);
        }
    }
}
