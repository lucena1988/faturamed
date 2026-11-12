package br.com.faturamed.procedimento;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;

/**
 * Le a tabela TUSS (ou AMB) em .xlsx/.xls/.csv. Procura o cabecalho com a coluna de codigo
 * ("Codigo do Termo", "Codigo") e a de descricao ("Termo", "Descricao", "Procedimento").
 * Datas de vigencia sao opcionais.
 */
public class LeitorTabelaTuss {
    public static List<CatalogoController.Procedimento> ler(String nome, byte[] conteudo) {
        String arquivo = Objects.toString(nome, "").toLowerCase(Locale.ROOT);
        List<List<String>> linhas;
        try {
            linhas = arquivo.endsWith(".csv") || arquivo.endsWith(".txt") ? csv(conteudo) : planilha(conteudo);
        } catch (Exception e) {
            throw new IllegalArgumentException("Nao foi possivel ler a tabela; envie .xlsx, .xls ou .csv", e);
        }
        for (int i = 0; i < Math.min(30, linhas.size()); i++) {
            var cab = linhas.get(i).stream().map(LeitorTabelaTuss::normalizar).toList();
            int codigo = indice(cab, c -> c.startsWith("codigo do termo") || c.equals("codigo") || c.startsWith("cod. tuss") || c.equals("codigo tuss") || c.equals("cod") || c.equals("id"));
            int descricao = indice(cab, c -> c.equals("termo") || c.startsWith("descricao") || c.equals("procedimento") || c.startsWith("descricao do termo") || c.equals("display_name"));
            if (codigo < 0 || descricao < 0) continue;
            int inicio = indice(cab, c -> c.contains("inicio") && c.contains("vigencia") && !c.contains("implantacao"));
            int fim = indice(cab, c -> c.contains("fim") && c.contains("vigencia") && !c.contains("implantacao"));
            Map<String, CatalogoController.Procedimento> porCodigo = new LinkedHashMap<>();
            for (int j = i + 1; j < linhas.size(); j++) {
                var l = linhas.get(j);
                String cod = valor(l, codigo).replaceAll("\\D", "");
                String desc = valor(l, descricao).trim();
                if (cod.isBlank() || desc.isBlank()) continue;
                if (desc.length() > 2000) desc = desc.substring(0, 2000);
                // Codigo repetido (versoes do termo): vale a ultima linha.
                LocalDate ini = data(valor(l, inicio)), ate = data(valor(l, fim));
                if (ini != null && ate != null && ate.isBefore(ini)) ate = null;
                porCodigo.put(cod, new CatalogoController.Procedimento(cod, desc, ini, ate));
            }
            if (porCodigo.isEmpty()) throw new IllegalArgumentException("Tabela sem procedimentos");
            return List.copyOf(porCodigo.values());
        }
        throw new IllegalArgumentException("Cabecalho nao encontrado: a tabela precisa das colunas de codigo e termo/descricao");
    }

    private static List<List<String>> planilha(byte[] conteudo) throws Exception {
        List<List<String>> linhas = new ArrayList<>();
        var formatador = new DataFormatter(new Locale("pt", "BR"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            for (Sheet aba : wb) {
                linhas.clear();
                for (Row row : aba) {
                    List<String> valores = new ArrayList<>();
                    for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) valores.add("");
                        else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
                            valores.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                        else valores.add(formatador.formatCellValue(cell));
                    }
                    linhas.add(valores);
                }
                // Primeira aba com dados.
                if (linhas.size() > 1) return linhas;
            }
        }
        return linhas;
    }

    private static List<List<String>> csv(byte[] conteudo) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(conteudo)).toString();
        } catch (CharacterCodingException e) {
            texto = new String(conteudo, Charset.forName("windows-1252"));
        }
        if (texto.startsWith("﻿")) texto = texto.substring(1);
        String primeira = texto.lines().findFirst().orElse("");
        char sep = primeira.chars().filter(ch -> ch == ';').count() >= primeira.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        List<List<String>> linhas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (aspas) {
                if (ch == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') { campo.append('"'); i++; }
                else if (ch == '"') aspas = false;
                else campo.append(ch);
            } else if (ch == '"') aspas = true;
            else if (ch == sep) { atual.add(campo.toString()); campo.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                atual.add(campo.toString()); campo.setLength(0); linhas.add(atual); atual = new ArrayList<>();
            } else campo.append(ch);
        }
        if (campo.length() > 0 || !atual.isEmpty()) { atual.add(campo.toString()); linhas.add(atual); }
        return linhas;
    }

    private static int indice(List<String> cab, java.util.function.Predicate<String> teste) {
        for (int i = 0; i < cab.size(); i++) if (teste.test(cab.get(i))) return i;
        return -1;
    }

    private static String valor(List<String> linha, int i) { return i < 0 || i >= linha.size() ? "" : Objects.toString(linha.get(i), ""); }

    static String normalizar(String valor) {
        return Normalizer.normalize(Objects.toString(valor, ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace(' ', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static LocalDate data(String valor) {
        String v = Objects.toString(valor, "").trim();
        if (v.isBlank()) return null;
        for (String formato : List.of("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy")) {
            try { return LocalDate.parse(v.length() > 10 && formato.startsWith("yyyy") ? v.substring(0, 10) : v, DateTimeFormatter.ofPattern(formato)); }
            catch (Exception ignored) {}
        }
        return null;
    }
}
