package br.com.faturamed.chat;

import java.io.IOException;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validacao dos anexos do chat. O tipo vem da extensao e e conferido pela assinatura do arquivo
 * (nunca do Content-Type enviado pelo navegador). SVG e HTML ficam de fora para evitar scripts embutidos.
 */
public final class ChatAnexos {
    static final int LIMITE_BYTES = 10 * 1024 * 1024;
    static final int LIMITE_POR_MENSAGEM = 5;

    public record Arquivo(String nome, String tipo, byte[] conteudo) {
        public boolean imagem() { return tipo.startsWith("image/"); }
    }

    private record Formato(String tipo, String assinatura) {}

    private static final Map<String, Formato> FORMATOS = Map.ofEntries(
        Map.entry("png", new Formato("image/png", "PNG")),
        Map.entry("jpg", new Formato("image/jpeg", "JPG")),
        Map.entry("jpeg", new Formato("image/jpeg", "JPG")),
        Map.entry("gif", new Formato("image/gif", "GIF")),
        Map.entry("webp", new Formato("image/webp", "WEBP")),
        Map.entry("pdf", new Formato("application/pdf", "PDF")),
        Map.entry("docx", new Formato("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "ZIP")),
        Map.entry("xlsx", new Formato("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ZIP")),
        Map.entry("pptx", new Formato("application/vnd.openxmlformats-officedocument.presentationml.presentation", "ZIP")),
        Map.entry("odt", new Formato("application/vnd.oasis.opendocument.text", "ZIP")),
        Map.entry("ods", new Formato("application/vnd.oasis.opendocument.spreadsheet", "ZIP")),
        Map.entry("doc", new Formato("application/msword", "OLE")),
        Map.entry("xls", new Formato("application/vnd.ms-excel", "OLE")),
        Map.entry("ppt", new Formato("application/vnd.ms-powerpoint", "OLE")),
        Map.entry("csv", new Formato("text/csv", "TEXTO")),
        Map.entry("txt", new Formato("text/plain", "TEXTO")),
        // mensagens de voz gravadas no navegador (Chrome/Edge/Firefox: webm ou ogg com Opus; Safari: mp4/m4a)
        Map.entry("webm", new Formato("audio/webm", "WEBM")),
        Map.entry("ogg", new Formato("audio/ogg", "OGG")),
        Map.entry("m4a", new Formato("audio/mp4", "MP4")),
        Map.entry("mp3", new Formato("audio/mpeg", "MP3"))
    );

    private ChatAnexos() {}

    static List<Arquivo> validar(List<MultipartFile> arquivos) throws IOException {
        List<MultipartFile> lista = arquivos == null ? List.of() : arquivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (lista.size() > LIMITE_POR_MENSAGEM) throw new IllegalArgumentException("Envie no máximo " + LIMITE_POR_MENSAGEM + " arquivos por mensagem");
        List<Arquivo> validos = new ArrayList<>();
        for (MultipartFile a : lista) {
            if (a.getSize() > LIMITE_BYTES) throw new IllegalArgumentException("O arquivo " + nomeSeguro(a.getOriginalFilename()) + " passa de 10 MB");
            validos.add(validar(a.getOriginalFilename(), a.getBytes()));
        }
        return validos;
    }

    public static Arquivo validar(String nomeOriginal, byte[] conteudo) {
        String nome = nomeSeguro(nomeOriginal);
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        Formato formato = FORMATOS.get(ext);
        if (formato == null) throw new IllegalArgumentException("Tipo de arquivo não permitido: " + nome
                + ". Use imagem (PNG, JPG, GIF, WEBP), PDF, Word, Excel, PowerPoint, CSV, TXT ou áudio");
        if (conteudo.length == 0) throw new IllegalArgumentException("O arquivo " + nome + " está vazio");
        if (conteudo.length > LIMITE_BYTES) throw new IllegalArgumentException("O arquivo " + nome + " passa de 10 MB");
        if (!confere(formato.assinatura(), conteudo)) throw new IllegalArgumentException("O conteúdo de " + nome + " não corresponde à extensão");
        return new Arquivo(nome, formato.tipo(), conteudo);
    }

    static boolean exibirNoNavegador(String tipo) {
        return tipo.startsWith("image/") || tipo.startsWith("audio/") || tipo.equals("application/pdf");
    }

    /** Nome sem caminho e sem caracteres de controle, limitado a 200 caracteres. */
    static String nomeSeguro(String nome) {
        String n = nome == null ? "" : nome.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?:]", "_").strip();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) n = "arquivo";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static boolean confere(String assinatura, byte[] b) {
        return switch (assinatura) {
            case "PNG" -> comeca(b, 0x89, 0x50, 0x4E, 0x47);
            case "JPG" -> comeca(b, 0xFF, 0xD8, 0xFF);
            case "GIF" -> comeca(b, 'G', 'I', 'F', '8');
            case "WEBP" -> comeca(b, 'R', 'I', 'F', 'F') && b.length > 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "PDF" -> comeca(b, '%', 'P', 'D', 'F');
            case "ZIP" -> comeca(b, 'P', 'K', 3, 4);
            case "OLE" -> comeca(b, 0xD0, 0xCF, 0x11, 0xE0);
            case "TEXTO" -> semBytesNulos(b);
            case "WEBM" -> comeca(b, 0x1A, 0x45, 0xDF, 0xA3);
            case "OGG" -> comeca(b, 'O', 'g', 'g', 'S');
            case "MP4" -> b.length > 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            case "MP3" -> comeca(b, 'I', 'D', '3') || (b.length > 1 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0);
            default -> false;
        };
    }

    private static boolean comeca(byte[] b, int... esperado) {
        if (b.length < esperado.length) return false;
        for (int i = 0; i < esperado.length; i++) if ((b[i] & 0xFF) != esperado[i]) return false;
        return true;
    }

    private static boolean semBytesNulos(byte[] b) {
        for (int i = 0; i < Math.min(b.length, 4096); i++) if (b[i] == 0) return false;
        return true;
    }
}
