package br.com.faturamed.producao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Trechos reais de saida do Tesseract nas fotos de exemplo (com os erros de leitura originais). */
class InterpretadorEtiquetasTest {

    @Test
    void lêEtiquetaDaAgendaRedeDor() {
        String texto = """
                rece Sor AGENDA DE ECOCARDIOGRAMA
                1º Men: 5837388? - 26/10/2026 09:42 — Nr. Preger: 121905716 DX Prever: 2810/2026
                Atend: 58376460 - 26/10/2026 10:35 — Nr. Proscr: 131908847 Dt.Prescr: 26/10/2026
                Paciente : Juliana Benedini Galli
                DN: 12/08/1963 — SADT Cardiologia (ITM) | |
                MD. : Paola Cardoso Preto Nr.Guia:
                Convenio :Sul America   Plano : Especial 1
                Carteirinha:55788888476425540015 Apos: 1967
                Alend: 58373882 — 26/10/2026 09:42 — Nr. Prescr: 131904716 Dt.Prescr: 26/10/2026
                | Paciente - Neide Alola de Carvalho
                """;
        List<ProducaoService.Item> itens = InterpretadorEtiquetas.interpretar(texto);
        assertEquals(2, itens.size());
        ProducaoService.Item j = itens.get(0);
        assertEquals("58376460", j.nrAtendimento());
        assertEquals("26/10/2026 10:35", j.dataHoraAtendimento());
        assertEquals("131908847", j.nrPrescricao());
        assertEquals("Juliana Benedini Galli", j.paciente());
        assertEquals("12/08/1963", j.dataNascimento());
        assertEquals("SADT Cardiologia (ITM)", j.procedimento());
        assertEquals("Paola Cardoso Preto", j.medicoSolicitante());
        assertEquals("Sul America", j.convenio());
        assertEquals("Especial 1", j.plano());
        assertEquals("55788888476425540015", j.carteirinha());
        assertTrue(j.pendenteRevisao(), "OCR local sempre exige conferencia");
        assertEquals("58373882", itens.get(1).nrAtendimento());
        assertEquals("Neide Alola de Carvalho", itens.get(1).paciente());
    }

    @Test
    void lêEtiquetaDeInternacaoEMarcaDuvidasComoPendentes() {
        String texto = """
                Atend: 57786900 DLEntr: 01/10/2026 - 01:11
                Cod - Conv: 3 — Amil Piano: Amil S760 QP
                Cart:093606432 — Quia: 1401274 — Senha: 202600646011
                Jorge Andre Salles
                Nome_Social:
                DT Nasc: 31/12/1964 - Pront: 6449360
                Filiacao 1: Izabel Zacharias Andre Salles
                Atend: ; TOSA OLEntr: Cas = Mo.
                Slona Pereira S ampalo
                """;
        List<ProducaoService.Item> itens = InterpretadorEtiquetas.interpretar(texto);
        assertEquals(1, itens.size(), "bloco sem atendimento nem paciente reconhecivel e descartado");
        ProducaoService.Item i = itens.get(0);
        assertEquals("57786900", i.nrAtendimento());
        assertEquals("01/10/2026 01:11", i.dataHoraAtendimento());
        assertEquals("3", i.convenioCodigo());
        assertEquals("Amil", i.convenio());
        assertEquals("Amil S760 QP", i.plano());
        assertEquals("093606432", i.carteirinha());
        assertEquals("1401274", i.guia());
        assertEquals("202600646011", i.senha());
        assertEquals("Jorge Andre Salles", i.paciente());
        assertEquals("31/12/1964", i.dataNascimento());
        assertEquals("6449360", i.prontuario());
        assertEquals("Izabel Zacharias Andre Salles", i.filiacao());
        assertTrue(i.pendenteRevisao());
    }

    @Test
    void atendimentoIlegivelFicaPendente() {
        List<ProducaoService.Item> itens = InterpretadorEtiquetas.interpretar("Mend: SBSTABRO — 26/10/2026\nPaciente : Neide Aloia de Carvalho\n");
        assertEquals(1, itens.size());
        assertEquals("", itens.get(0).nrAtendimento());
        assertTrue(itens.get(0).pendenteRevisao());
    }
}
