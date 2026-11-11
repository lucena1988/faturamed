package br.com.faturamed.chat;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Exporta o historico de uma conversa do chat em PDF (auditoria ou anexo de chamado). */
final class ChatExportadorPdf {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final float MARGEM = 50, LARGURA = PDRectangle.A4.getWidth() - 2 * MARGEM;

    private ChatExportadorPdf() {}

    static byte[] gerar(String conversa, List<ChatService.Mensagem> mensagens, String exportadoPor) throws IOException {
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont italico = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            var pagina = new Pagina(doc);
            pagina.linha("FaturaChat · " + conversa, negrito, 15, new Color(25, 77, 96));
            pagina.linha("Exportado por " + exportadoPor + " em " + DATA_HORA.format(java.time.Instant.now())
                    + " · " + mensagens.size() + " mensagem(ns)", regular, 9, Color.GRAY);
            pagina.espaco(10);
            for (var m : mensagens) {
                pagina.espaco(6);
                String cabecalho = m.autorNome() + "  ·  " + DATA_HORA.format(m.criadoEm()) + (m.editada() ? "  (editada)" : "");
                pagina.linha(cabecalho, negrito, 10, new Color(36, 103, 125));
                if (m.resposta() != null) pagina.paragrafo("↪ Em resposta a " + m.resposta().autorNome() + ": " + m.resposta().texto(), italico, 9, Color.GRAY);
                if (m.apagada()) pagina.paragrafo("Mensagem apagada", italico, 10, Color.GRAY);
                else if (!m.texto().isEmpty()) pagina.paragrafo(m.texto(), regular, 10, Color.BLACK);
                for (var a : m.anexos())
                    pagina.paragrafo((a.tipo().startsWith("audio/") ? "[Mensagem de voz] " : "[Anexo] ") + a.nome(), italico, 9, new Color(90, 90, 90));
                for (var r : m.reacoes())
                    pagina.paragrafo("Reação " + r.emoji() + ": " + String.join(", ", r.nomes()), italico, 8, Color.GRAY);
            }
            pagina.fechar();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /** Controla a pagina atual e quebra para a proxima quando o texto chega ao fim. */
    private static final class Pagina {
        private final PDDocument doc;
        private PDPageContentStream fluxo;
        private float y;

        Pagina(PDDocument doc) throws IOException { this.doc = doc; nova(); }

        private void nova() throws IOException {
            if (fluxo != null) fluxo.close();
            PDPage p = new PDPage(PDRectangle.A4);
            doc.addPage(p);
            fluxo = new PDPageContentStream(doc, p);
            y = PDRectangle.A4.getHeight() - MARGEM;
        }

        void espaco(float altura) throws IOException { y -= altura; if (y < MARGEM) nova(); }

        void linha(String texto, PDFont fonte, float tamanho, Color cor) throws IOException {
            espaco(tamanho * 1.4f);
            fluxo.beginText();
            fluxo.setFont(fonte, tamanho);
            fluxo.setNonStrokingColor(cor);
            fluxo.newLineAtOffset(MARGEM, y);
            fluxo.showText(seguro(texto, fonte));
            fluxo.endText();
        }

        void paragrafo(String texto, PDFont fonte, float tamanho, Color cor) throws IOException {
            for (String parte : texto.split("\\R")) for (String l : quebrar(seguro(parte, fonte), fonte, tamanho)) linha(l, fonte, tamanho, cor);
        }

        void fechar() throws IOException { fluxo.close(); }
    }

    private static List<String> quebrar(String texto, PDFont fonte, float tamanho) throws IOException {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split(" ")) {
            String teste = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (fonte.getStringWidth(teste) / 1000 * tamanho > LARGURA && !atual.isEmpty()) {
                linhas.add(atual.toString());
                atual = new StringBuilder(palavra);
            } else atual = new StringBuilder(teste);
        }
        linhas.add(atual.toString());
        return linhas;
    }

    /** As fontes padrao do PDF nao tem emojis: troca o que a fonte nao consegue desenhar. */
    private static String seguro(String texto, PDFont fonte) {
        StringBuilder sb = new StringBuilder();
        texto.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            try { fonte.encode(c); sb.append(c); }
            catch (Exception e) { if (cp == '↪') sb.append("->"); else if (!Character.isWhitespace(cp)) sb.append(cp > 0xFFFF ? "" : "?"); else sb.append(' '); }
        });
        return sb.toString();
    }
}
