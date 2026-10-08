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
            // O modelo do hospital repete o detalhe na aba de apresentacao.
            if (hospital) abas.sort(Comparator.comparing(a -> !a.nome().equalsIgnoreCase("Planilha2")));
            for (Aba aba : abas) {
                for (int i = 0; i < Math.min(50, aba.linhas().size()); i++) {
                    Map<Integer, String> cabecalho = aba.linhas().get(i);
                    Set<String> campos = new HashSet<>();
                    cabecalho.values().forEach(v -> campos.add(normalizar(v)));
                    if (!campos.contains("atendimento") || (hospital && !campos.contains("data consumo"))) continue;
                    if (hospital && !campos.containsAll(Set.of("medico", "cod produto", "valor tot", "vl. a repassar", "setor"))) {
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
                        if (linha.getOrDefault("atendimento", "").isBlank()) {
                            throw new IllegalArgumentException("Atendimento ausente na linha " + (j + 1));
                        }
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

    static String normalizar(String valor) {
        return Normalizer.normalize(Objects.toString(valor, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('\u00a0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static LocalDate data(String valor, int linha) {
        for (String formato : List.of("uuuu-MM-dd", "dd/MM/uuuu", "d/M/uuuu")) {
            try {
                return LocalDate.parse(valor, java.time.format.DateTimeFormatter.ofPattern(formato)
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
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
