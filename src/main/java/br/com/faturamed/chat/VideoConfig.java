package br.com.faturamed.chat;

import java.util.Locale;
import java.util.regex.Pattern;

/** Dominio do servidor Jitsi proprio usado nas videochamadas do chat. */
public final class VideoConfig {
    private static final Pattern DOMINIO = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+(:\\d{1,5})?");

    private VideoConfig() {}

    /** Aceita "video.empresa.com.br" ou "https://video.empresa.com.br/"; devolve "" se vazio ou invalido. */
    public static String normalizar(String valor) {
        if (valor == null) return "";
        String d = valor.strip().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("/+$", "");
        return DOMINIO.matcher(d).matches() ? d : "";
    }
}
