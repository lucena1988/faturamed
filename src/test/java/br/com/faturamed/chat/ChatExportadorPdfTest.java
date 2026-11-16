package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatExportadorPdfTest {
    @Test void geraPdfComAcentosEmojisRespostasEAnexos() throws Exception {
        var anexo = new ChatService.Anexo(1, "guia.pdf", "application/pdf", 100);
        var resposta = new ChatService.Resposta(1, "Ana", "Lote 112 voltou?");
        var reacao = new ChatService.Reacao("👍", List.of(2L), List.of("Bruno"));
        var mensagens = List.of(
            new ChatService.Mensagem(1, 1, 1, "Ana", "Lote 112 voltou? Atenção à conferência 🎉", Instant.now(), List.of(), null, null, List.of(), false, false, false, List.of()),
            new ChatService.Mensagem(2, 1, 2, "Bruno", "Voltou sim, segue a guia", Instant.now(), List.of(anexo), null, resposta, List.of(reacao), true, false, false, List.of()),
            new ChatService.Mensagem(3, 1, 2, "Bruno", "", Instant.now(), List.of(), null, null, List.of(), false, true, false, List.of()));
        byte[] pdf = ChatExportadorPdf.gerar("Conversa de teste", mensagens, "Ana");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 500);
    }

    @Test void quebraConversasLongasEmVariasPaginas() throws Exception {
        var muitas = java.util.stream.IntStream.range(0, 200).mapToObj(i ->
            new ChatService.Mensagem(i, 1, 1, "Ana", "Mensagem número " + i + " com um texto razoavelmente comprido para ocupar a linha toda e quebrar.",
                Instant.now(), List.of(), null, null, List.of(), false, false, false, List.of())).toList();
        byte[] pdf = ChatExportadorPdf.gerar("Longa", muitas, "Ana");
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) { assertTrue(doc.getNumberOfPages() > 3); }
    }
}
