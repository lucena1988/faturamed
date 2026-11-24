package br.com.faturamed.conciliacao;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.binary.*;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LeitorPlanilhaVisitas {
    public record Linha(int numero, Map<String, String> campos) {
        public String campo(String nome) { return campos.getOrDefault(normalizar(nome), ""); }
    }
    private record Aba(String nome, List<Map<Integer, String>> linhas) {}

    public List<Linha> ler(MultipartFile arquivo, boolean hospital) {
        if (arquivo.isEmpty()) throw new IllegalArgumentException("Planilha vazia");
        String nome = Objects.toString(arquivo.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".xlsx") && !nome.endsWith(".xlsb")) {
            throw new IllegalArgumentException("Envie uma planilha .xlsx ou .xlsb");
        }
        try (InputStream input = arquivo.getInputStream()) {
            List<Aba> abas = nome.endsWith(".xlsb") ? lerBinario(input) : lerXlsx(input);
            // O modelo do hospital repete o detalhe na aba de apresentacao. O repasse (Vl. a Repassar ou Regra)
            // e opcional: sem ele, vale o repasse cadastrado para o procedimento.
            if (hospital) abas.sort(Comparator.comparing(a -> !a.nome().equalsIgnoreCase("Planilha2")));
            for (Aba aba : abas) {
                for (int i = 0; i < Math.min(50, aba.linhas().size()); i++) {
                    Map<Integer, String> cabecalho = hospital ? apelidar(aba.linhas().get(i)) : apelidarProducao(aba.linhas().get(i));
                    Set<String> campos = new HashSet<>();
                    cabecalho.values().forEach(v -> campos.add(normalizar(v)));
                    // A producao pode vir sem a coluna Atendimento; a data (Dt.) passa a identificar a visita.
                    boolean cabecalhoProducao = !hospital && (campos.contains("atendimento") || campos.contains("dt."));
                    if (!cabecalhoProducao && (!campos.contains("atendimento") || !hospital || !campos.contains("data consumo"))) continue;
                    // No layout alternativo (Nr atendimento, Dt procedimento...) nao ha coluna de status de faturamento.
                    Set<String> obrigatorios = layoutAlternativo(aba.linhas().get(i))
                            ? Set.of("medico", "cod produto", "valor tot") : Set.of("medico", "cod produto", "valor tot", "setor");
                    if (hospital && !campos.containsAll(obrigatorios)) {
                        throw new IllegalArgumentException("Cabecalho do hospital incompleto");
                    }
                    List<Linha> resultado = new ArrayList<>();
                    for (int j = i + 1; j < aba.linhas().size(); j++) {
                        var valores = aba.linhas().get(j);
                        if (valores.values().stream().allMatch(String::isBlank)) continue;
                        Map<String, String> linha = new LinkedHashMap<>();
                        cabecalho.forEach((col, label) -> {
                            if (!label.isBlank()) linha.put(normalizar(label), valores.getOrDefault(col, ""));
                        });
                        // Visita da producao sem atendimento aguarda o hospital e concilia por data e medico.
                        if (hospital && linha.getOrDefault("atendimento", "").isBlank()) {
                            throw new IllegalArgumentException("Atendimento ausente na linha " + (j + 1));
                        }
                        linha.putIfAbsent("atendimento", "");
                        resultado.add(new Linha(j + 1, linha));
                    }
                    if (resultado.isEmpty()) throw new IllegalArgumentException("Planilha sem registros");
                    return resultado;
                }
            }
            throw new IllegalArgumentException("Cabecalho de " + (hospital ? "hospital" : "medico") + " nao encontrado");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Nao foi possivel ler a planilha; confira o formato do arquivo", e);
        }
    }

    private List<Aba> lerXlsx(InputStream input) throws Exception {
        List<Aba> abas = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            for (Sheet sheet : workbook) {
                if (sheet.getLastRowNum() > 20000) throw new IllegalArgumentException("Limite de 20000 linhas por aba");
                List<Map<Integer, String>> linhas = new ArrayList<>();
                for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                    Map<Integer, String> valores = new LinkedHashMap<>();
                    Row row = sheet.getRow(i);
                    if (row != null) for (Cell cell : row) {
                        CellType tipo = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
                        String valor = switch (tipo) {
                            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                            case STRING -> cell.getStringCellValue();
                            case BLANK -> "";
                            default -> throw new IllegalArgumentException("Celula invalida: " + cell.getAddress());
                        };
                        valores.put(cell.getColumnIndex(), valor.trim());
                    }
                    linhas.add(valores);
                }
                abas.add(new Aba(sheet.getSheetName(), linhas));
            }
        }
        return abas;
    }

    private List<Aba> lerBinario(InputStream input) throws Exception {
        List<Aba> abas = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(input)) {
            XSSFBReader reader = new XSSFBReader(pkg);
            var strings = new XSSFBSharedStringsTable(pkg);
            var styles = reader.getXSSFBStylesTable();
            var sheets = (XSSFBReader.SheetIterator) reader.getSheetsData();
            DataFormatter formatter = new DataFormatter(Locale.US) {
                @Override
                public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904) {
                    if (DateUtil.isADateFormat(formatIndex, formatString)) {
                        return DateUtil.getLocalDateTime(value, use1904).toLocalDate().toString();
                    }
                    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
                }
            };
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    List<Map<Integer, String>> linhas = new ArrayList<>();
                    var handler = new XSSFSheetXMLHandler.SheetContentsHandler() {
                        private Map<Integer, String> atual;
                        public void startRow(int rowNum) {
                            if (rowNum > 20000) throw new IllegalArgumentException("Limite de 20000 linhas por aba");
                            while (linhas.size() <= rowNum) linhas.add(new LinkedHashMap<>());
                            atual = linhas.get(rowNum);
                        }
                        public void endRow(int rowNum) {}
                        public void cell(String ref, String value, XSSFComment comment) {
                            atual.put((int) new CellReference(ref).getCol(), Objects.toString(value, "").trim());
                        }
                    };
                    new XSSFBSheetHandler(sheet, styles, null, strings, handler, formatter, false).parse();
                    abas.add(new Aba(sheets.getSheetName(), linhas));
                }
            }
        }
        return abas;
    }

    // Layout de exportacao alternativo do hospital: cada coluna equivale a uma do modelo padrao.
    private static final Map<String, String> APELIDOS_HOSPITAL = Map.ofEntries(
            Map.entry("nr atendimento", "atendimento"),
            Map.entry("dt procedimento", "data consumo"),
            Map.entry("nm medico laudo", "medico"),
            Map.entry("cd procedimento", "cod produto"),
            Map.entry("ds procedimento", "produto"),
            Map.entry("vl procedimento", "valor tot"),
            Map.entry("valor", "valor tot"),
            Map.entry("repasse", "vl. a repassar"),
            Map.entry("convenio", "operadora"),
            Map.entry("nm paciente", "paciente"));

    private static String rotulo(String valor) {
        // Exportacoes com BOM UTF-16 lido como texto deixam "þÿ" no inicio da primeira coluna
        // (apos normalizar, o "ÿ" vira "y").
        return normalizar(valor).replaceFirst("^(﻿|þy)+", "").trim();
    }

    private static boolean layoutAlternativo(Map<Integer, String> cabecalho) {
        Set<String> rotulos = new HashSet<>();
        cabecalho.values().forEach(v -> rotulos.add(rotulo(v)));
        return !rotulos.contains("data consumo") && rotulos.contains("dt procedimento") && rotulos.contains("nr atendimento");
    }

    private static Map<Integer, String> apelidar(Map<Integer, String> cabecalho) {
        if (!layoutAlternativo(cabecalho)) return cabecalho;
        Map<Integer, String> resultado = new LinkedHashMap<>();
        cabecalho.forEach((col, label) -> resultado.put(col, APELIDOS_HOSPITAL.getOrDefault(rotulo(label), label)));
        return resultado;
    }


    // Exportacao de exames do sistema do hospital (Laudado por, Data execucao...) usada como producao:
    // sem a coluna Dt., a data de execucao identifica a visita e o medico que laudou, o profissional.
    private static Map<Integer, String> apelidarProducao(Map<Integer, String> cabecalho) {
        Set<String> rotulos = new HashSet<>();
        cabecalho.values().forEach(v -> rotulos.add(rotulo(v)));
        if (rotulos.contains("dt.") || !rotulos.contains("data execucao")) return cabecalho;
        boolean semMedico = !rotulos.contains("medico");
        String colunaMedico = rotulos.contains("laudado por") ? "laudado por" : "executado por";
        Map<Integer, String> resultado = new LinkedHashMap<>();
        cabecalho.forEach((col, label) -> {
            String r = rotulo(label);
            resultado.put(col, r.equals("data execucao") ? "Dt." : semMedico && r.equals(colunaMedico) ? "Medico" : label);
        });
        return resultado;
    }
    static String normalizar(String valor) {
        return Normalizer.normalize(Objects.toString(valor, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('\u00a0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static LocalDate data(String valor, int linha) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Data invalida na linha " + linha);
        }
        String texto = valor.replace('\u00a0', ' ').trim();

        // 1. Numero serial de data do Excel (ex.: 46024 ou 46024.5)
        if (texto.matches("^[0-9]+(?:\\.[0-9]+)?$")) {
            try {
                double num = Double.parseDouble(texto);
                if (num >= 1.0 && num <= 100000.0 && DateUtil.isValidExcelDate(num)) {
                    return DateUtil.getLocalDateTime(num).toLocalDate();
                }
            } catch (Exception ignored) {}
        }

        // 2. Separacao de horario quando presente (ex.: 30/10/2026 00:00:00 ou 2026-10-30T14:30:00)
        String parteData = texto;
        if (texto.contains(" ")) {
            int sep = texto.indexOf(' ');
            String rem = texto.substring(sep + 1).trim();
            if (rem.matches("^[0-9]{1,2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]+)?)?(?:\\s*(?:AM|PM|am|pm))?$")) {
                parteData = texto.substring(0, sep).trim();
            }
        } else if (texto.contains("T") || texto.contains("t")) {
            int sep = texto.indexOf('T') != -1 ? texto.indexOf('T') : texto.indexOf('t');
            String rem = texto.substring(sep + 1).trim();
            if (rem.matches("^[0-9]{1,2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]+)?)?(?:Z|[+-][0-9]{2}:?[0-9]{2})?$")) {
                parteData = texto.substring(0, sep).trim();
            }
        }

        // 3. Formatos com ano de 4 digitos (estritos)
        for (String formato : List.of(
                "uuuu-MM-dd",
                "dd/MM/uuuu",
                "d/M/uuuu",
                "dd-MM-uuuu",
                "d-M-uuuu",
                "dd.MM.uuuu",
                "d.M.uuuu",
                "uuuu/MM/dd",
                "uuuu.MM.dd")) {
            try {
                return LocalDate.parse(parteData, java.time.format.DateTimeFormatter.ofPattern(formato)
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } catch (java.time.format.DateTimeParseException ignored) {}
        }

        // 4. Formatos com ano de 2 digitos (seculo 21: 2000-2099)
        for (String prefixo : List.of("dd/MM/", "d/M/", "dd-MM-", "d-M-", "dd.MM.", "d.M.")) {
            try {
                var formatter = new java.time.format.DateTimeFormatterBuilder()
                        .appendPattern(prefixo)
                        .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 2, 2000)
                        .toFormatter()
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT);
                return LocalDate.parse(parteData, formatter);
            } catch (java.time.format.DateTimeParseException ignored) {}
        }

        throw new IllegalArgumentException("Data invalida na linha " + linha);
    }

    static String identificador(String valor) {
        String texto = valor.trim();
        if (texto.matches("[0-9]+\\.0+")) return texto.substring(0, texto.indexOf('.'));
        return texto;
    }
}
