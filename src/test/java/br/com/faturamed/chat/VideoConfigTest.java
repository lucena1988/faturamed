package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VideoConfigTest {
    @Test void normalizaDominio() {
        assertEquals("video.empresa.com.br", VideoConfig.normalizar(" https://Video.Empresa.com.br/ "));
        assertEquals("video.empresa.com.br:8443", VideoConfig.normalizar("video.empresa.com.br:8443"));
        assertEquals("", VideoConfig.normalizar(null));
        assertEquals("", VideoConfig.normalizar(""));
    }

    @Test void recusaValorQuePoderiaInjetarNaCsp() {
        assertEquals("", VideoConfig.normalizar("video.empresa.com; script-src *"));
        assertEquals("", VideoConfig.normalizar("*"));
        assertEquals("", VideoConfig.normalizar("localhost"));
    }
}
