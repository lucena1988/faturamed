package br.com.faturamed.seguranca;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PoliticaConteudoTest {
    @Test void semVideoMantemPoliticaRestrita() {
        String csp = SegurancaConfig.politicaConteudo("");
        assertTrue(csp.contains("script-src 'self';"));
        assertFalse(csp.contains("frame-src"));
    }

    @Test void comVideoLiberaSoOServidorJitsi() {
        String csp = SegurancaConfig.politicaConteudo("https://video.empresa.com.br");
        assertTrue(csp.contains("script-src 'self' https://video.empresa.com.br;"));
        assertTrue(csp.contains("frame-src https://video.empresa.com.br;"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
    }
}
