package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConciliadorVisitasTest {
    private final ConciliadorVisitas motor = new ConciliadorVisitas();

    @Test
    void completaCorrespondenciaUnicaSemAlterarOriginal() {
        var medico = medico(2, "2026-01-01");
        var resultado = conferir(List.of(medico), List.of(hospital(2, "Medico A", "Conta1", "2026-01-01")));
        var visita = resultado.visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(visita.hospital().medico()).isEqualTo("Medico A");
        assertThat(visita.original()).doesNotContainKey("medico");
        assertThat(resultado.repasseCorrespondente()).isEqualByComparingTo("88.264");
    }

    @Test
    void naoEscolhePrimeiroMedicoQuandoHaDuasVisitasNoHospital() {
        var resultado = conferir(List.of(medico(2, "2026-01-01")), List.of(
                hospital(2, "Medico A", "Conta1", "2026-01-01"),
                hospital(3, "Medico B", "Conta1", "2026-01-01")));
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(resultado.visitas().getFirst().hospital()).isNull();
        assertThat(resultado.visitas().getFirst().candidatos()).hasSize(2);
        assertThat(resultado.repasseCorrespondente()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void contasDiferentesTambemExigemConferencia() {
        var resultado = conferir(List.of(medico(2, "2026-01-01")), List.of(
                hospital(2, "Medico A", "Conta1", "2026-01-01"),
                hospital(3, "Medico A", "Conta2", "2026-01-01")));
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
    }

    @Test
    void preservaLinhasRepetidasSemDuplicarPagamento() {
        var resultado = conferir(List.of(medico(2, "2026-01-01"), medico(3, "2026-01-01")),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-01")));
        assertThat(resultado.visitas()).hasSize(2).allMatch(v -> v.status() == ConciliadorVisitas.Status.DIVERGENTE);
        assertThat(resultado.repasseCorrespondente()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void datasDiferentesDoMesmoAtendimentoNaoSeCorrespondem() {
        var resultado = conferir(List.of(medico(2, "2026-01-01")),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-02")));
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        assertThat(resultado.hospitalSemProducao()).hasSize(1);
    }

    @Test
    void naoMarcaPagaQuandoSetorNaoEFaturado() {
        var campos = new java.util.HashMap<>(hospital(2, "Medico A", "Conta1", "2026-01-01").campos());
        campos.put("setor", "Em analise");
        var resultado = conferir(List.of(medico(2, "2026-01-01")), List.of(new LeitorPlanilhaVisitas.Linha(2, campos)));
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
    }

    @Test
    void valorInformadoPeloMedicoDiferenteDoHospitalEDivergente() {
        var campos = new java.util.HashMap<>(medico(2, "2026-01-01").campos());
        campos.put("valor orig", "120");
        var resultado = conferir(List.of(new LeitorPlanilhaVisitas.Linha(2, campos)),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-01")));
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
    }

    private ConciliadorVisitas.Relatorio conferir(List<LeitorPlanilhaVisitas.Linha> medico, List<LeitorPlanilhaVisitas.Linha> hospital) {
        return motor.conciliar("Hospital teste", "medico.xlsx", "hospital.xlsb", medico, hospital);
    }

    @Test
    void aceitaSomenteAtendimentoEPreencheDataDoHospital() {
        var resultado = conferir(List.of(new LeitorPlanilhaVisitas.Linha(2, Map.of("atendimento", "00123"))),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-02")));
        var visita = resultado.visitas().getFirst();
        assertThat(visita.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(visita.data()).isEqualTo(java.time.LocalDate.of(2026, 1, 2));
        assertThat(visita.original()).containsOnlyKeys("atendimento");
    }

    @Test
    void semDataNaoEscolheEntreDatasDistintas() {
        var resultado = conferir(List.of(new LeitorPlanilhaVisitas.Linha(2, Map.of("atendimento", "00123"))),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-02"), hospital(3, "Medico B", "Conta1", "2026-02-03")));
        assertThat(resultado.visitas().getFirst().data()).isNull();
        assertThat(resultado.visitas().getFirst().candidatos()).hasSize(2);
        assertThat(resultado.visitas().getFirst().status()).isEqualTo(ConciliadorVisitas.Status.DIVERGENTE);
    }

    @Test
    void linhasComESemDataNaoReutilizamMesmoRegistro() {
        var resultado = conferir(List.of(new LeitorPlanilhaVisitas.Linha(2, Map.of("atendimento", "00123")), medico(3, "2026-01-02")),
                List.of(hospital(2, "Medico A", "Conta1", "2026-01-02")));
        assertThat(resultado.visitas()).allMatch(v -> v.hospital() == null && v.status() == ConciliadorVisitas.Status.DIVERGENTE);
    }

    @Test
    void repetidosSemCorrespondenciaContinuamPendentes() {
        var resultado = conferir(List.of(new LeitorPlanilhaVisitas.Linha(2, Map.of("atendimento", "ausente")),
                new LeitorPlanilhaVisitas.Linha(3, Map.of("atendimento", "ausente"))), List.of());
        assertThat(resultado.visitas()).hasSize(2).allMatch(v -> v.data() == null && v.status() == ConciliadorVisitas.Status.PENDENTE);
    }

    private LeitorPlanilhaVisitas.Linha medico(int linha, String data) {
        return new LeitorPlanilhaVisitas.Linha(linha, Map.of("atendimento", "00123", "dt.", data));
    }

    private LeitorPlanilhaVisitas.Linha hospital(int linha, String medico, String conta, String data) {
        return new LeitorPlanilhaVisitas.Linha(linha, Map.of("atendimento", "00123", "data consumo", data,
                "medico", medico, "conta paciente", conta, "cod produto", "10102019", "produto", "Visita hospitalar",
                "valor tot", "103.84", "regra", "0.85", "vl. a repassar", "88.264", "setor", "Faturado"));
    }
}
