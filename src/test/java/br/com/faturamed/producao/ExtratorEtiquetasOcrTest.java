package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExtratorEtiquetasOcrTest {
    private final ExtratorEtiquetasOcr extrator = new ExtratorEtiquetasOcr("", "modelo", "http://localhost", "tesseract", new LeitorTesseract("tesseract-inexistente", "por"));

    @Test
    void semChaveFicaIndisponivel() {
        assertFalse(extrator.disponivel());
        assertThrows(IllegalArgumentException.class, () -> extrator.extrair(new byte[]{1}, "image/jpeg"));
    }

    @Test
    void interpretaEtiquetasMantendoRepetidasEMarcandoPendentes() throws Exception {
        String resposta = """
            {"content":[{"type":"tool_use","name":"registrar_etiquetas","input":{
              "cabecalho":{"medico":"Mayara","periodo":"manha","tipoAgenda":"Ecocardiograma"},
              "etiquetas":[
                {"nrAtendimento":"58373882","paciente":"Neide Aloia de Carvalho","convenio":"Advance","plano":"Gold"},
                {"nrAtendimento":"58373882","paciente":"Neide Aloia de Carvalho","convenio":"Advance","plano":"Gold"},
                {"nrAtendimento":"5837?","paciente":"Juliana Benedini Galli"},
                {"nrAtendimento":"58376460","paciente":"Juliana Benedini Galli","ilegivel":true}]}}]}""";
        ExtratorEtiquetasOcr.Resultado r = extrator.interpretar(new ObjectMapper().readTree(resposta));
        assertEquals(4, r.itens().size());
        assertEquals("58373882", r.itens().get(1).nrAtendimento());
        assertFalse(r.itens().get(0).pendenteRevisao());
        assertTrue(r.itens().get(2).pendenteRevisao());
        assertTrue(r.itens().get(3).pendenteRevisao());
        assertEquals("OCR", r.itens().get(0).origem());
        assertEquals("Ecocardiograma", r.cabecalho().tipoAgenda());
    }
}
