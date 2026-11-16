package br.com.faturamed.producao;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OCR local: trata a foto (cinza, ampliacao, binarizacao adaptativa contra sombra) e chama o executavel do Tesseract.
 * Requer o Tesseract instalado com o idioma portugues (por). Caminho configuravel em faturamed.ocr.tesseract.
 */
@Component
public class LeitorTesseract {
    private final String executavel;
    private final String idioma;
    private final String psm = System.getProperty("faturamed.ocr.psm", "6");
    private volatile Boolean disponivel;

    public LeitorTesseract(@Value("${faturamed.ocr.tesseract:${TESSERACT_PATH:tesseract}}") String executavel,
            @Value("${faturamed.ocr.idioma:por}") String idioma) {
        this.executavel = executavel;
        this.idioma = idioma;
    }

    public boolean disponivel() {
        if (disponivel == null) {
            try {
                Process p = new ProcessBuilder(executavel, "--list-langs").redirectErrorStream(true).start();
                String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                disponivel = p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0 && saida.lines().anyMatch(l -> l.trim().equals(idioma));
            } catch (IOException e) {
                disponivel = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return disponivel;
    }

    public String ler(byte[] imagem) {
        if (!disponivel()) throw new IllegalArgumentException("Tesseract nao encontrado no servidor. Lance as etiquetas manualmente");
        Path arquivo = null;
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagem));
            if (original == null) throw new IllegalArgumentException("Nao foi possivel abrir a imagem enviada");
            arquivo = Files.createTempFile("producao-ocr", ".png");
            ImageIO.write(tratar(original), "png", arquivo.toFile());
            Process p = new ProcessBuilder(List.of(executavel, arquivo.toString(), "stdout", "-l", idioma, "--psm", psm))
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String texto = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(180, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalArgumentException("Leitura da foto demorou demais"); }
            if (p.exitValue() != 0) throw new IllegalArgumentException("Falha na leitura da foto. Lance as etiquetas manualmente");
            return texto;
        } catch (IOException e) {
            throw new IllegalArgumentException("Falha na leitura da foto. Lance as etiquetas manualmente");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Leitura da foto interrompida");
        } finally {
            if (arquivo != null) try { Files.deleteIfExists(arquivo); } catch (IOException ignorado) { }
        }
    }

    /** Cinza + ampliacao + limiar adaptativo (media local), que remove sombras e o fundo das etiquetas. */
    static BufferedImage tratar(BufferedImage original) {
        double escala = Math.min(2.5, 5000.0 / Math.max(original.getWidth(), original.getHeight()));
        int w = (int) Math.round(original.getWidth() * Math.max(1, escala));
        int h = (int) Math.round(original.getHeight() * Math.max(1, escala));
        BufferedImage cinza = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        var g = cinza.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        var raster = cinza.getRaster();
        long[] integral = new long[(w + 1) * (h + 1)];
        int[] linha = new int[w];
        for (int y = 0; y < h; y++) {
            raster.getSamples(0, y, w, 1, 0, linha);
            long soma = 0;
            for (int x = 0; x < w; x++) {
                soma += linha[x];
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + soma;
            }
        }
        int raio = Math.max(15, w / 40);
        BufferedImage saida = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        var out = saida.getRaster();
        int[] linhaSaida = new int[w];
        for (int y = 0; y < h; y++) {
            raster.getSamples(0, y, w, 1, 0, linha);
            int y0 = Math.max(0, y - raio), y1 = Math.min(h, y + raio + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - raio), x1 = Math.min(w, x + raio + 1);
                long area = (long) (x1 - x0) * (y1 - y0);
                long s = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0];
                linhaSaida[x] = linha[x] * area * 100 < s * 82 ? 0 : 1;
            }
            out.setSamples(0, y, w, 1, 0, linhaSaida);
        }
        return endireitar(saida);
    }

    /** Corrige a inclinacao da folha: testa angulos de -8 a 8 graus e escolhe o que deixa as linhas de texto mais "nitidas". */
    static BufferedImage endireitar(BufferedImage bin) {
        int w = bin.getWidth(), h = bin.getHeight();
        int passo = Math.max(1, Math.max(w, h) / 1200);
        List<int[]> pretos = new java.util.ArrayList<>();
        var r = bin.getRaster();
        for (int y = 0; y < h; y += passo) for (int x = 0; x < w; x += passo) if (r.getSample(x, y, 0) == 0) pretos.add(new int[]{x, y});
        double melhor = 0, melhorScore = -1;
        for (double a = -8; a <= 8.001; a += 0.25) {
            double rad = Math.toRadians(a), sin = Math.sin(rad), cos = Math.cos(rad);
            int[] linhas = new int[(int) ((w + h) / passo) + 2];
            int deslocamento = (int) (w / passo);
            for (int[] p : pretos) {
                int yr = (int) ((p[1] * cos - p[0] * sin) / passo) + deslocamento;
                if (yr >= 0 && yr < linhas.length) linhas[yr]++;
            }
            double score = 0;
            for (int i = 1; i < linhas.length; i++) { double d = linhas[i] - linhas[i - 1]; score += d * d; }
            if (score > melhorScore) { melhorScore = score; melhor = a; }
        }
        if (Math.abs(melhor) < 0.2) return bin;
        BufferedImage girada = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        var g = girada.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.rotate(Math.toRadians(-melhor), w / 2.0, h / 2.0);
        g.drawImage(bin, 0, 0, null);
        g.dispose();
        return girada;
    }
}
