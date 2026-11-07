package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Diagnostico manual: OCR_AMOSTRAS=<pasta com .jpg> e Tesseract instalado. Imprime as etiquetas lidas de cada foto. */
class LeitorTesseractFotosTest {
    @Test
    void imprimeEtiquetasDasFotos() throws Exception {
        String pasta = System.getenv("OCR_AMOSTRAS");
        LeitorTesseract leitor = new LeitorTesseract("tesseract", "por");
        assumeTrue(pasta != null && leitor.disponivel(), "sem fotos de amostra ou sem Tesseract");
        try (Stream<Path> fotos = Files.list(Path.of(pasta))) {
            for (Path foto : fotos.filter(f -> f.toString().endsWith(".jpg")).sorted().toList()) {
                String texto = leitor.ler(Files.readAllBytes(foto));
                if (System.getenv("OCR_SAIDA") != null) {
                    Files.writeString(Path.of(System.getenv("OCR_SAIDA"), foto.getFileName() + ".txt"), texto);
                    javax.imageio.ImageIO.write(LeitorTesseract.tratar(javax.imageio.ImageIO.read(foto.toFile())), "png", Path.of(System.getenv("OCR_SAIDA"), foto.getFileName() + ".png").toFile());
                }
                var itens = InterpretadorEtiquetas.interpretar(texto);
                System.out.println("== " + foto.getFileName() + ": " + itens.size() + " etiquetas");
                itens.forEach(i -> System.out.println((i.pendenteRevisao() ? "  [REVISAR] " : "  [ok]      ") + i.nrAtendimento()
                        + " | " + i.paciente() + " | " + i.convenio() + " " + i.plano() + " | cart " + i.carteirinha() + " | " + i.procedimento()));
            }
        }
    }
}
