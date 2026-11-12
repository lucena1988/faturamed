package br.com.faturamed.conciliacao;

import java.util.regex.Pattern;

/** Tipo do procedimento conciliado no Fechamento Faturado. */
public enum TipoProcedimento {
    VISITA, EXAME, CONSULTA, OUTRO;

    /** Coluna opcional da planilha do hospital (ja normalizada) que informa o tipo. */
    public static final String CAMPO = "tipo";

    private static final Pattern PADRAO_EXAME = Pattern.compile(
            "\\b(exame|eco|ecocardio\\w*|eletro\\w*|ecg|holter|mapa|tomografia|angiotc|ressonancia|rmc|raio|rx|ultrassom\\w*|ultrasson\\w*|usg|"
            + "ergometric\\w*|ergometr\\w*|teste|laborat\\w*|doppler|endoscopia|colonoscopia|cintilografia|mamografia|densitometria|biopsia)\\b");

    public static TipoProcedimento classificar(String informado, String codigo, String descricao) {
        String tipo = LeitorPlanilhaVisitas.normalizar(informado);
        if (!tipo.isBlank()) {
            if (tipo.startsWith("visita")) return VISITA;
            if (tipo.startsWith("exame") || tipo.equals("sadt")) return EXAME;
            if (tipo.startsWith("consulta")) return CONSULTA;
            if (tipo.startsWith("outro")) return OUTRO;
        }
        String texto = LeitorPlanilhaVisitas.normalizar(descricao);
        if (texto.contains("visita")) return VISITA;
        if (texto.contains("consulta")) return CONSULTA;
        if (PADRAO_EXAME.matcher(texto).find()) return EXAME;
        String cod = codigo == null ? "" : codigo.replaceAll("\\D", "");
        // TUSS: 1.01.01.xx consultas, 1.01.02.xx visitas hospitalares, 4.xx.xx.xx procedimentos diagnosticos (SADT).
        if (cod.length() == 8) {
            if (cod.startsWith("101010")) return CONSULTA;
            if (cod.startsWith("101020")) return VISITA;
            if (cod.startsWith("4")) return EXAME;
        }
        return OUTRO;
    }

    public static TipoProcedimento de(String valor) {
        try { return valor == null || valor.isBlank() ? null : valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Tipo de procedimento invalido: " + valor); }
    }
}
