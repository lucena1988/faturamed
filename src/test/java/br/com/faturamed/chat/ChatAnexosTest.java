package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChatAnexosTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
    static final byte[] PDF = "%PDF-1.7 teste".getBytes(StandardCharsets.US_ASCII);
    static final byte[] XLSX = {'P', 'K', 3, 4, 20, 0, 0, 0};

    @Test void aceitaFormatosPermitidosComTipoPelaExtensao() {
        assertEquals("image/png", ChatAnexos.validar("print.png", PNG).tipo());
        assertEquals("application/pdf", ChatAnexos.validar("guia.PDF", PDF).tipo());
        assertTrue(ChatAnexos.validar("planilha.xlsx", XLSX).tipo().contains("spreadsheet"));
        assertEquals("text/csv", ChatAnexos.validar("dados.csv", "a;b\n1;2".getBytes(StandardCharsets.UTF_8)).tipo());
    }

    @Test void recusaExtensaoPerigosaOuConteudoDisfarcado() {
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("ataque.svg", "<svg/>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("pagina.html", "<script>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("programa.exe", new byte[]{'M', 'Z'}));
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("falso.png", PDF));
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("binario.txt", new byte[]{1, 0, 2}));
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("vazio.pdf", new byte[0]));
    }

    @Test void aceitaAudioGravadoNoNavegadorConferindoAssinatura() {
        assertEquals("audio/webm", ChatAnexos.validar("audio_5s_x.webm", new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1}).tipo());
        assertEquals("audio/ogg", ChatAnexos.validar("audio_5s_x.ogg", "OggS\0\2".getBytes(StandardCharsets.ISO_8859_1)).tipo());
        assertEquals("audio/mp4", ChatAnexos.validar("audio_5s_x.m4a", new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A'}).tipo());
        assertThrows(IllegalArgumentException.class, () -> ChatAnexos.validar("falso.webm", PDF));
        assertTrue(ChatAnexos.exibirNoNavegador("audio/webm"));
    }

    @Test void limpaNomeDoArquivo() {
        assertEquals("relatorio.pdf", ChatAnexos.nomeSeguro("C:\\Users\\fulano\\relatorio.pdf"));
        assertEquals("a_b.pdf", ChatAnexos.nomeSeguro("../../a\"b.pdf"));
        assertEquals("arquivo", ChatAnexos.nomeSeguro(null));
    }

    @Test void soImagemEPdfAbremNoNavegador() {
        assertTrue(ChatAnexos.exibirNoNavegador("image/png"));
        assertTrue(ChatAnexos.exibirNoNavegador("application/pdf"));
        assertFalse(ChatAnexos.exibirNoNavegador("text/csv"));
    }
}
