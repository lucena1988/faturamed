package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        assertThatThrownBy(() -> ler(true, new String[][] {
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

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-10-30",
            "30/10/2026",
            "30-10-2026",
            "30.10.2026",
            "2026/10/30",
            "2026.10.30",
            "30/10/2026 00:00:00",
            "30/10/2026 00:00",
            "30/10/2026 14:35:10",
            "2026-10-30 00:00:00",
            "2026-10-30T00:00:00",
            "2026-10-30T14:30:00Z",
            "30-10-2026 00:00:00",
            "30.10.2026 08:30:00",
            "30/10/26",
            "30/10/26 00:00:00",
            "30-10-26",
            " 30/10/2026 ",
            "30/10/2026\u00a000:00:00"
    })
    @DisplayName("CH-2026-0004: Valida formatos padroes e variacoes de data e horario para 30/10/2026")
    void interpretaDatasComHorarioEVariacoesPadrao(String valor) {
        LocalDate data = LeitorPlanilhaVisitas.data(valor, 12);
        assertThat(data).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    @DisplayName("CH-2026-0004: Aceita datas com um digito de dia e mes")
    void aceitaDatasComUmDigito() {
        assertThat(LeitorPlanilhaVisitas.data("1/5/2026", 12)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(LeitorPlanilhaVisitas.data("1/5/2026 00:00:00", 12)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(LeitorPlanilhaVisitas.data("01/05/2026", 12)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(LeitorPlanilhaVisitas.data("1-5-2026", 12)).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("CH-2026-0004: Aceita serial date numerico do Excel")
    void aceitaNumeroSerialExcel() {
        // 46024 no Excel representa 2026-01-02
        assertThat(LeitorPlanilhaVisitas.data("46024", 12)).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(LeitorPlanilhaVisitas.data("46024.0", 12)).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(LeitorPlanilhaVisitas.data("46024.5", 12)).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "31/02/2026",
            "31/02/2026 00:00:00",
            "2026-02-31",
            "2026-13-01",
            "32/10/2026",
            "",
            "   ",
            "nao_e_uma_data"
    })
    @DisplayName("CH-2026-0004: Rejeita estritamente datas invalidas mantendo mensagem de erro")
    void rejeitaDatasInvalidas(String valor) {
        assertThatThrownBy(() -> LeitorPlanilhaVisitas.data(valor, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data invalida na linha 12");
    }

    @Test
    @DisplayName("CH-2026-0004: Importacao de planilha hospitalar com coluna Data Consumo contendo data com horario")
    void importaPlanilhaHospitalComDataEHorario() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Planilha2");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Atendimento");
            header.createCell(1).setCellValue("Data Consumo");
            header.createCell(2).setCellValue("Medico");
            header.createCell(3).setCellValue("Cod Produto");
            header.createCell(4).setCellValue("Valor Tot");
            header.createCell(5).setCellValue("Vl. A Repassar");
            header.createCell(6).setCellValue("Setor");

            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("99001");
            row1.createCell(1).setCellValue("30/10/2026 00:00:00");
            row1.createCell(2).setCellValue("Dr. Teste");
            row1.createCell(3).setCellValue("1010");
            row1.createCell(4).setCellValue("100.00");
            row1.createCell(5).setCellValue("80.00");
            row1.createCell(6).setCellValue("UTI");

            workbook.write(output);
            var linhas = new LeitorPlanilhaVisitas().ler(new MockMultipartFile("hospital", "hospital.xlsx",
                    "application/octet-stream", output.toByteArray()), true);
            assertThat(linhas).hasSize(1);
            assertThat(linhas.getFirst().campo("Atendimento")).isEqualTo("99001");
            assertThat(linhas.getFirst().campo("Data Consumo")).isEqualTo("30/10/2026 00:00:00");

            LocalDate data = LeitorPlanilhaVisitas.data(linhas.getFirst().campo("Data Consumo"), linhas.getFirst().numero());
            assertThat(data).isEqualTo(LocalDate.of(2026, 10, 30));
        }
    }
}
