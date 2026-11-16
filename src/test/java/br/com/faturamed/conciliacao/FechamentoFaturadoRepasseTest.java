package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fechamento Faturado: qualquer procedimento, repasse da planilha com fallback para o cadastro. */
class FechamentoFaturadoRepasseTest {
    private final ConciliadorVisitas motor = new ConciliadorVisitas();

    private static LeitorPlanilhaVisitas.Linha hospital(Map<String, String> extras) {
        var campos = new HashMap<>(Map.of("atendimento", "00123", "data consumo", "2026-01-01", "medico", "Medico A",
                "conta paciente", "Conta1", "cod produto", "40101010", "produto", "ECG convencional",
                "valor tot", "200", "setor", "Faturado"));
        campos.putAll(extras);
        return new LeitorPlanilhaVisitas.Linha(2, campos);
    }

    private ConciliadorVisitas.Relatorio conferir(LeitorPlanilhaVisitas.Linha hospital) {
        return motor.conciliar("H", "p.xlsx", "h.xlsx", List.of(new LeitorPlanilhaVisitas.Linha(2, Map.of("atendimento", "00123", "dt.", "2026-01-01"))), List.of(hospital));
    }

    private static RepasseProcedimentoService.Repasse regra(long id, Long hospitalId, String codigo, String tipo, String percentual, String fixo) {
        return new RepasseProcedimentoService.Repasse(id, hospitalId, null, codigo, tipo, percentual == null ? null : new BigDecimal(percentual),
                fixo == null ? null : new BigDecimal(fixo), LocalDate.of(2000, 1, 1), null, true, "");
    }

    @Test
    void repasseInformadoNaPlanilhaTemPrioridade() {
        var r = conferir(hospital(Map.of("vl. a repassar", "150", "regra", "0.75")));
        var v = r.visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(v.repasseEfetivo()).isEqualByComparingTo("150");
        assertThat(v.hospital().origemRepasse()).isEqualTo("PLANILHA");
        assertThat(v.hospital().tipo()).isEqualTo("EXAME");
    }

    @Test
    void somenteRegraNaPlanilhaCalculaValorTotVezesPercentual() {
        var fracao = conferir(hospital(Map.of("regra", "0.7"))).visitas().getFirst();
        assertThat(fracao.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(fracao.repasseEfetivo()).isEqualByComparingTo("140");
        var percentual = conferir(hospital(Map.of("regra", "70"))).visitas().getFirst();
        assertThat(percentual.repasseEfetivo()).isEqualByComparingTo("140");
    }

    @Test
    void semRepasseNaPlanilhaNemCadastroFicaDivergente() {
        var v = conferir(hospital(Map.of())).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(v.motivo()).contains("sem repasse cadastrado");
    }

    @Test
    void semRepasseNaPlanilhaUsaCadastroPorCodigo() {
        var linha = RepasseProcedimentoService.aplicar(7L, hospital(Map.of()), List.of(
                regra(1, null, null, "EXAME", "50", null),
                regra(2, null, "40101010", null, "60", null)));
        var v = conferir(linha).visitas().getFirst();
        assertThat(v.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(v.repasseEfetivo()).isEqualByComparingTo("120");
        assertThat(v.hospital().origemRepasse()).isEqualTo("CADASTRO");
    }

    @Test
    void complementoNaoMexeEmLinhaQueJaTemRepasse() {
        var original = hospital(Map.of("vl. a repassar", "150"));
        assertThat(RepasseProcedimentoService.semRepasse(original)).isFalse();
        assertThat(RepasseProcedimentoService.semRepasse(hospital(Map.of("regra", "0.5")))).isFalse();
        assertThat(RepasseProcedimentoService.semRepasse(hospital(Map.of()))).isTrue();
    }

    @Test
    void escolheDoMaisEspecificoParaOMaisGenerico() {
        var regras = List.of(regra(1, null, null, "EXAME", "10", null), regra(2, 7L, null, "EXAME", "20", null),
                regra(3, null, "40101010", null, "30", null), regra(4, 7L, "40101010", null, null, "99"));
        var data = LocalDate.of(2026, 1, 1);
        assertThat(RepasseProcedimentoService.escolher(7L, "40101010", "EXAME", data, regras).orElseThrow().id()).isEqualTo(4);
        assertThat(RepasseProcedimentoService.escolher(8L, "40101010", "EXAME", data, regras).orElseThrow().id()).isEqualTo(3);
        assertThat(RepasseProcedimentoService.escolher(7L, "40999999", "EXAME", data, regras).orElseThrow().id()).isEqualTo(2);
        assertThat(RepasseProcedimentoService.escolher(8L, "40999999", "EXAME", data, regras).orElseThrow().id()).isEqualTo(1);
        assertThat(RepasseProcedimentoService.escolher(8L, "10101012", "CONSULTA", data, regras)).isEmpty();
    }

    @Test
    void valorFixoEVigencia() {
        var vencida = new RepasseProcedimentoService.Repasse(1L, null, null, null, "EXAME", null, new BigDecimal("80"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), true, "");
        assertThat(RepasseProcedimentoService.aplicar(null, hospital(Map.of()), List.of(vencida)).campo("Vl. A Repassar")).isBlank();
        var vigente = regra(2, null, null, "EXAME", null, "80");
        assertThat(RepasseProcedimentoService.aplicar(null, hospital(Map.of()), List.of(vencida, vigente)).campo("Vl. A Repassar")).isEqualTo("80");
    }

    @Test
    void classificaTipoDoProcedimento() {
        assertThat(TipoProcedimento.classificar("", "10102019", "Visita hospitalar")).isEqualTo(TipoProcedimento.VISITA);
        assertThat(TipoProcedimento.classificar("", "10101012", "Consulta em consultorio")).isEqualTo(TipoProcedimento.CONSULTA);
        assertThat(TipoProcedimento.classificar("", "", "Ecocardiograma transtoracico")).isEqualTo(TipoProcedimento.EXAME);
        assertThat(TipoProcedimento.classificar("", "40304361", "Hemograma")).isEqualTo(TipoProcedimento.EXAME);
        assertThat(TipoProcedimento.classificar("Consulta", "999", "Qualquer")).isEqualTo(TipoProcedimento.CONSULTA);
        assertThat(TipoProcedimento.classificar("", "123", "Taxa de sala")).isEqualTo(TipoProcedimento.OUTRO);
    }

    @Test
    void relatorioAntigoSemTipoContinuaComoVisita() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var antigo = "{\"linha\":2,\"atendimento\":\"1\",\"data\":\"2026-01-01\",\"paciente\":\"\",\"convenio\":\"\",\"medico\":\"A\",\"codigo\":\"1\","
                + "\"procedimento\":\"Taxa\",\"conta\":\"\",\"setor\":\"Faturado\",\"valorTotal\":10,\"regra\":null,\"repasse\":8}";
        var registro = json.readValue(antigo, ConciliadorVisitas.RegistroHospital.class);
        assertThat(registro.tipo()).isEqualTo("VISITA");
        assertThat(registro.origemRepasse()).isEqualTo("PLANILHA");
    }
}
