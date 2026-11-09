package br.com.faturamed.producao;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Criptografia de dados sensiveis em repouso (carteirinha, guia, senha de autorizacao e foto da folha).
 * AES-256-GCM com IV aleatorio por valor. Chave em FATURAMED_CHAVE_DADOS (32 bytes em Base64).
 * Textos cifrados levam o prefixo "enc:v1:"; arquivos levam o cabecalho magico FMENC1.
 * Sem chave configurada, grava em claro e avisa no log (ambiente local). Leitura aceita valores antigos em claro.
 */
@Component
public class CriptografiaDados {
    private static final Logger LOG = LoggerFactory.getLogger(CriptografiaDados.class);
    static final String PREFIXO = "enc:v1:";
    private static final byte[] MAGICO = "FMENC1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV = 12, TAG = 128;

    private final SecretKeySpec chave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CriptografiaDados(@Value("${faturamed.dados.chave:${FATURAMED_CHAVE_DADOS:}}") String chaveBase64) {
        SecretKeySpec k = null;
        if (chaveBase64 != null && !chaveBase64.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(chaveBase64.trim());
            if (bytes.length != 32) throw new IllegalStateException("FATURAMED_CHAVE_DADOS deve ter 32 bytes (Base64 de 256 bits)");
            k = new SecretKeySpec(bytes, "AES");
        } else {
            LOG.warn("FATURAMED_CHAVE_DADOS nao configurada: carteirinha, guia, senha e fotos da producao serao gravadas SEM criptografia");
        }
        this.chave = k;
    }

    public boolean ativa() { return chave != null; }

    public String cifrar(String texto) {
        if (texto == null || texto.isEmpty() || chave == null || texto.startsWith(PREFIXO)) return texto;
        return PREFIXO + Base64.getEncoder().encodeToString(cifrarBytesBrutos(texto.getBytes(StandardCharsets.UTF_8)));
    }

    public String decifrar(String valor) {
        if (valor == null || !valor.startsWith(PREFIXO)) return valor;
        if (chave == null) return "••••";
        try {
            return new String(decifrarBytesBrutos(Base64.getDecoder().decode(valor.substring(PREFIXO.length()))), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            LOG.error("Falha ao decifrar dado sensivel (chave diferente da usada na gravacao?)");
            return "••••";
        }
    }

    public byte[] cifrarArquivo(byte[] dados) {
        if (dados == null || chave == null || ehCifrado(dados)) return dados;
        byte[] c = cifrarBytesBrutos(dados);
        return ByteBuffer.allocate(MAGICO.length + c.length).put(MAGICO).put(c).array();
    }

    public byte[] decifrarArquivo(byte[] dados) {
        if (dados == null || !ehCifrado(dados)) return dados;
        if (chave == null) throw new IllegalStateException("Arquivo criptografado e FATURAMED_CHAVE_DADOS nao configurada");
        return decifrarBytesBrutos(Arrays.copyOfRange(dados, MAGICO.length, dados.length));
    }

    static boolean ehCifrado(byte[] dados) {
        return dados.length > MAGICO.length && Arrays.equals(Arrays.copyOf(dados, MAGICO.length), MAGICO);
    }

    private byte[] cifrarBytesBrutos(byte[] claro) {
        try {
            byte[] iv = new byte[IV];
            aleatorio.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG, iv));
            byte[] cifrado = c.doFinal(claro);
            return ByteBuffer.allocate(IV + cifrado.length).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar dado sensivel", e);
        }
    }

    private byte[] decifrarBytesBrutos(byte[] pacote) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG, pacote, 0, IV));
            return c.doFinal(pacote, IV, pacote.length - IV);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar dado sensivel", e);
        }
    }
}
