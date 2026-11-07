package br.com.faturamed.producao;

import java.util.*;
import java.util.regex.*;

/**
 * Transforma o texto bruto do OCR em etiquetas. Tolerante aos erros tipicos do Tesseract
 * ("Atend" lido como "Alend"/"Mend", "Guia" como "Gula"/"Quia" etc.). Cada etiqueta comeca na linha do "Atend".
 * Suporta os dois modelos conhecidos: agenda Rede D'Or (Atend/Nr.Prescr/Paciente/DN/MD/Convenio/Carteirinha)
 * e etiqueta de internacao (Atend/Dt.Entr/Cod-Conv/Cart/Guia/Senha/DT Nasc/Pront/Filiacao).
 */
final class InterpretadorEtiquetas {
    private InterpretadorEtiquetas() {}

    private static final int I = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern INICIO = Pattern.compile("(?:^|[^a-z])[a-z]{0,3}[eo][nm]d\\s*[:;.,]", I);
    private static final Pattern NUMERO_ATEND = Pattern.compile("[eo][nm]d\\s*[:;.,]\\s*(\\d[\\d ]{6,9}\\d)", I);
    private static final Pattern DATA_HORA = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s*[-—–=]?\\s*(\\d{2}:\\d{2})?");
    private static final Pattern PRESCRICAO = Pattern.compile("N[rt][.,]?\\s*Pr\\w*\\s*[:;]\\s*(\\d{8,10})", I);
    private static final Pattern PACIENTE = Pattern.compile("aciente\\s*[:;\\-]?\\s*(.+)", I);
    private static final Pattern NASCIMENTO = Pattern.compile("(?:\\bDN|N[ae]s[ce]\\w*)\\s*[:;.,]?\\s*(\\d{2}/\\d{2}/\\d{4})", I);
    private static final Pattern PRONTUARIO = Pattern.compile("Pro[nm]t\\w*\\s*[:;.,]?\\s*(\\d{4,})", I);
    private static final Pattern CONV_CODIGO = Pattern.compile("Con[vy]\\w*\\s*[:;]\\s*(\\d{1,4})\\s*[-—–=]\\s*(.+?)\\s+P[li1]a[nm]o\\s*[:;]\\s*(.+)", I);
    private static final Pattern CONVENIO = Pattern.compile("Conv[eê]nio\\s*[:;]?\\s*(.+?)(?:\\s{2,}|\\s+P[li1]a[nm]o|$)", I);
    private static final Pattern PLANO = Pattern.compile("P[li1]a[nm]o\\s*[:;]\\s*(.+?)(?:\\s{2,}|$)", I);
    private static final Pattern CARTEIRINHA = Pattern.compile("Cart\\w*\\s*[:;]?\\s*(\\d[\\d ]{6,}\\d)", I);
    private static final Pattern GUIA = Pattern.compile("[GQ][uo][il1l]a\\s*[:;]\\s*(\\d{4,})", I);
    private static final Pattern SENHA = Pattern.compile("S[eo][nm]ha\\s*[:;]\\s*([A-Z0-9]{5,})", I);
    private static final Pattern PROCEDIMENTO = Pattern.compile("(SADT\\s.+?\\))", I);
    private static final Pattern MEDICO = Pattern.compile("\\bMD\\s*[.,]?\\s*[:;]\\s*(.+?)(?:\\s+N[rt][.,]?\\s*G|\\s{2,}|$)", I);
    private static final Pattern FILIACAO = Pattern.compile("F[il1]{1,2}\\w*\\s*1\\s*[:;]\\s*(.+)", I);
    private static final Pattern NOME = Pattern.compile("^[A-ZÀ-Ú][a-zà-ú]+(?:\\s+(?:[A-ZÀ-Ú][a-zà-ú]+|d[aeo]s?)){1,8}$");

    static List<ProducaoService.Item> interpretar(String texto) {
        List<List<String>> blocos = new ArrayList<>();
        List<String> atual = null;
        for (String bruta : texto.split("\\R")) {
            String linha = bruta.replaceAll("[|“”\"]", " ").replaceAll("\\s+", " ").trim();
            if (linha.isEmpty()) continue;
            if (INICIO.matcher(linha).find()) { atual = new ArrayList<>(); blocos.add(atual); }
            if (atual != null) atual.add(linha);
        }
        List<ProducaoService.Item> itens = new ArrayList<>();
        for (List<String> bloco : blocos) {
            ProducaoService.Item item = etiqueta(bloco);
            if (item != null) itens.add(item);
        }
        return itens;
    }

    private static ProducaoService.Item etiqueta(List<String> linhas) {
        String primeira = linhas.get(0);
        String atendimento = grupo(NUMERO_ATEND, primeira).replace(" ", "");
        if (!atendimento.matches("\\d{7,10}")) atendimento = "";
        Matcher dh = DATA_HORA.matcher(primeira);
        String dataHora = dh.find() ? (dh.group(1) + (dh.group(2) == null ? "" : " " + dh.group(2))) : "";
        String prescricao = "", paciente = "", nascimento = "", prontuario = "", convenio = "", codigo = "", plano = "",
                carteirinha = "", guia = "", senha = "", procedimento = "", medico = "", filiacao = "";
        boolean depoisCart = false;
        for (String l : linhas) {
            prescricao = primeiro(prescricao, PRESCRICAO, l);
            paciente = primeiro(paciente, PACIENTE, l);
            nascimento = primeiro(nascimento, NASCIMENTO, l);
            prontuario = primeiro(prontuario, PRONTUARIO, l);
            Matcher cc = CONV_CODIGO.matcher(l);
            if (codigo.isEmpty() && cc.find()) { codigo = cc.group(1); convenio = cc.group(2).trim(); plano = cc.group(3).trim(); }
            convenio = primeiro(convenio, CONVENIO, l);
            plano = primeiro(plano, PLANO, l);
            carteirinha = primeiro(carteirinha, CARTEIRINHA, l).replace(" ", "");
            guia = primeiro(guia, GUIA, l);
            senha = primeiro(senha, SENHA, l);
            procedimento = primeiro(procedimento, PROCEDIMENTO, l);
            medico = primeiro(medico, MEDICO, l);
            filiacao = primeiro(filiacao, FILIACAO, l);
            // modelo de internacao: o nome vem sem rotulo, logo apos a linha da carteirinha
            if (paciente.isEmpty() && depoisCart && NOME.matcher(l).matches()) paciente = l;
            if (l.matches("(?i).*Cart\\w*\\s*[:;].*")) depoisCart = true;
        }
        if (atendimento.isEmpty() && paciente.isEmpty()) return null;
        // OCR local erra digitos (ex.: 5 lido como 6): toda etiqueta lida precisa ser conferida com a foto
        boolean confiavel = false;
        return new ProducaoService.Item(null, atendimento, dataHora, limitar(paciente, 180), prontuario, nascimento, limitar(filiacao, 180),
                limitar(convenio, 120), codigo, limitar(plano, 120), limitar(carteirinha, 40), limitar(guia, 40), limitar(senha, 40),
                prescricao, limitar(procedimento, 180), limitar(medico, 180), "OCR", !confiavel, null, null);
    }

    private static String grupo(Pattern p, String l) { Matcher m = p.matcher(l); return m.find() ? m.group(1).trim() : ""; }
    private static String primeiro(String atual, Pattern p, String l) { return atual.isEmpty() ? grupo(p, l) : atual; }
    private static String limitar(String v, int max) { return v.length() > max ? v.substring(0, max) : v; }
}
