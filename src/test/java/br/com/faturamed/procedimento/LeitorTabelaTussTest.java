package br.com.faturamed.procedimento;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class LeitorTabelaTussTest {
    @Test
    void leCsvDaAnsComCabecalhoDepoisDoTitulo() {
        String csv = "Tabela 22 - Procedimentos e eventos em saude\n"
                + "Código do Termo;Termo;Data de início de vigência;Data de fim de vigência\n"
                + "10101012;Consulta em consultório;01/01/2014;\n"
                + "40.10.101-0;ECG convencional de até 12 derivações;01/01/2014;\n"
                + "40101010;\"ECG convencional; versao nova\";01/06/2020;\n";
        var lista = LeitorTabelaTuss.ler("tuss.csv", csv.getBytes(Charset.forName("windows-1252")));
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).descricao()).isEqualTo("Consulta em consultório");
        assertThat(lista.get(1).codigo()).isEqualTo("40101010");
        assertThat(lista.get(1).descricao()).isEqualTo("ECG convencional; versao nova");
        assertThat(lista.get(1).inicioVigencia()).isEqualTo(LocalDate.of(2020, 6, 1));
    }

    @Test
    void leExportacaoComIdEDisplayName() {
        String csv = "﻿id;source;display_name;extras_fim_vigencia;extras_fim_implantacao;extras_inicio_vigencia\n"
                + "\"41001320\";\"tuss-22\";\"Tomossíntese digital mamária\";\"-\";\"2014-08-31\";\"2026-10-01\"\n"
                + "\"50001043\";\"tuss-22\";\"Atendimento fisioterapêutico\";\"2026-12-31\";\"2016-03-31\";\"2016-01-01\"\n";
        var lista = LeitorTabelaTuss.ler("tuss-22.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).descricao()).isEqualTo("Tomossíntese digital mamária");
        assertThat(lista.get(0).inicioVigencia()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(lista.get(0).fimVigencia()).isNull();
        assertThat(lista.get(1).fimVigencia()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void leXlsx() throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var aba = wb.createSheet("TUSS");
            var cab = aba.createRow(0); cab.createCell(0).setCellValue("Codigo"); cab.createCell(1).setCellValue("Descrição");
            var l = aba.createRow(1); l.createCell(0).setCellValue(40304361); l.createCell(1).setCellValue("Hemograma com contagem de plaquetas");
            wb.write(out);
            var lista = LeitorTabelaTuss.ler("tuss.xlsx", out.toByteArray());
            assertThat(lista).singleElement().satisfies(p -> {
                assertThat(p.codigo()).isEqualTo("40304361");
                assertThat(p.descricao()).startsWith("Hemograma");
            });
        }
    }

    @Test
    void recusaArquivoSemCabecalho() {
        assertThatThrownBy(() -> LeitorTabelaTuss.ler("x.csv", "a;b\n1;2\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cabecalho");
    }
}
