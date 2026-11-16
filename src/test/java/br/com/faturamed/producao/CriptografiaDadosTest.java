package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CriptografiaDadosTest {
    private static final String CHAVE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void cifraEDecifraTextoComIvAleatorio() {
        var c = new CriptografiaDados(CHAVE);
        String a = c.cifrar("55788888476425540015"), b = c.cifrar("55788888476425540015");
        assertTrue(a.startsWith(CriptografiaDados.PREFIXO));
        assertNotEquals(a, b, "IV aleatorio: mesmo texto gera cifras diferentes");
        assertEquals("55788888476425540015", c.decifrar(a));
        assertEquals(a, c.cifrar(a), "nao cifra duas vezes");
        assertTrue(a.length() <= 200, "cabe na coluna");
    }

    @Test
    void cifraArquivoEAceitaValoresAntigosEmClaro() {
        var c = new CriptografiaDados(CHAVE);
        byte[] foto = {1, 2, 3, 4, 5};
        byte[] cifrada = c.cifrarArquivo(foto);
        assertTrue(CriptografiaDados.ehCifrado(cifrada));
        assertArrayEquals(foto, c.decifrarArquivo(cifrada));
        assertArrayEquals(foto, c.decifrarArquivo(foto), "arquivo antigo em claro continua legivel");
        assertEquals("123", c.decifrar("123"));
    }

    @Test
    void semChaveGravaEmClaroEOcultaCifrados() {
        var semChave = new CriptografiaDados("");
        assertFalse(semChave.ativa());
        assertEquals("123", semChave.cifrar("123"));
        String cifrado = new CriptografiaDados(CHAVE).cifrar("123");
        assertEquals("••••", semChave.decifrar(cifrado));
    }

    @Test
    void chaveErradaNaoQuebra() {
        String cifrado = new CriptografiaDados(CHAVE).cifrar("segredo");
        byte[] outra = new byte[32]; outra[0] = 9;
        assertEquals("••••", new CriptografiaDados(Base64.getEncoder().encodeToString(outra)).decifrar(cifrado));
    }
}
