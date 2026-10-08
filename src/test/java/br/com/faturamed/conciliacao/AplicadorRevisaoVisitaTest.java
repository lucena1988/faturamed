package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AplicadorRevisaoVisitaTest {
    private final AplicadorRevisaoVisita aplicador = new AplicadorRevisaoVisita();

    @Test
    void confirmaCandidatoPreservandoOriginalERecalculandoRepasse() {
        var original = visita(2);
        var revista = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        assertThat(revista.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(revista.hospital().linha()).isEqualTo(10);
        assertThat(original.hospital()).isNull();
        assertThat(revista.original()).isEqualTo(original.original());
        assertThat(revista.candidatos()).isEqualTo(original.candidatos());
        var atualizado = aplicador.recalcular(relatorio(List.of(original)), List.of(revista));
        assertThat(atualizado.resumo().get(ConciliadorVisitas.Status.PAGA)).isEqualTo(1);
        assertThat(atualizado.repasseCorrespondente()).isEqualByComparingTo("85.085");
    }

    @Test
    void impedeMesmoRegistroHospitalarEmDuasLinhasDoMedico() {
        var primeira = aplicador.aplicar(visita(2), relatorio(List.of(visita(2), visita(3))),
                request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        var atual = relatorio(List.of(primeira, visita(3)));
        assertThatThrownBy(() -> aplicador.aplicar(visita(3), atual, request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10)))
                .isInstanceOf(ConflitoRevisaoException.class);
        var segunda = aplicador.aplicar(visita(3), atual, request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 11));
        assertThat(aplicador.recalcular(atual, List.of(primeira, segunda)).repasseCorrespondente()).isEqualByComparingTo("170.170");
    }

    @Test
    void rejeitaRegistroForaDosCandidatos() {
        assertThatThrownBy(() -> aplicador.aplicar(visita(2), relatorio(List.of(visita(2))),
                request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 99))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manterPendenteLiberaAssociacaoEExcluiRepassePago() {
        var original = visita(2);
        var confirmada = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        var pendente = aplicador.aplicar(original, relatorio(List.of(confirmada)), request(RevisaoVisitaRequest.Acao.MANTER_PENDENTE, null));
        assertThat(pendente.status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        assertThat(pendente.hospital()).isNull();
        assertThat(aplicador.recalcular(relatorio(List.of(original)), List.of(pendente)).repasseCorrespondente()).isEqualByComparingTo("0");
    }

    @Test
    void restaurarRetornaAoResultadoAutomatico() {
        var original = visita(2);
        var confirmada = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        var restaurada = aplicador.aplicar(original, relatorio(List.of(confirmada)), request(RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO, null));
        assertThat(restaurada).isEqualTo(original);
    }

    @Test
    void confirmacaoNaoMarcaPagaForaDoSetorFaturado() {
        var h = hospital(10, "Em analise", "Medico A");
        var original = new ConciliadorVisitas.Visita(2, "123", LocalDate.of(2026, 1, 1), ConciliadorVisitas.Status.DIVERGENTE,
                "Conferir", Map.of(), null, List.of(h));
        var revista = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        assertThat(revista.status()).isEqualTo(ConciliadorVisitas.Status.PENDENTE);
        assertThat(revista.hospital()).isEqualTo(h);
    }

    @Test
    void rejeitaConfirmacaoDeRegistroIncompleto() {
        var original = new ConciliadorVisitas.Visita(2, "123", LocalDate.of(2026, 1, 1), ConciliadorVisitas.Status.DIVERGENTE,
                "Conferir", Map.of(), null, List.of(hospital(10, "Faturado", "")));
        assertThatThrownBy(() -> aplicador.aplicar(original, relatorio(List.of(original)),
                request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    private RevisaoVisitaRequest request(RevisaoVisitaRequest.Acao acao, Integer linha) {
        return new RevisaoVisitaRequest(acao, linha, "Revisor teste", "Conferencia documental realizada", 0L);
    }

    @Test
    void preencheDadosFaltantesSemAlterarOrigem() {
        var original = visita(2);
        var ajuste = new RevisaoVisitaRequest.Ajuste(original.data(), original.atendimento(), "Medico corrigido", "Visita revisada",
                "10102019", new BigDecimal("120"), new BigDecimal("102"), ConciliadorVisitas.Status.PENDENTE, "Aguardando faturamento");
        var revista = aplicador.aplicar(original, relatorio(List.of(original)),
                new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.AJUSTAR_DADOS, null, "Revisor", "Campos conferidos", 0L, ajuste));
        assertThat(revista.medicoEfetivo()).isEqualTo("Medico corrigido");
        assertThat(revista.repasseEfetivo()).isEqualByComparingTo("102");
        assertThat(revista.hospital()).isNull();
        assertThat(revista.original()).isEqualTo(original.original());
        assertThat(original.camposRevisados()).isNull();
    }

    @Test
    void corrigeValorPagoPreservandoValorDoHospital() {
        var original = visita(2);
        var ajuste = new RevisaoVisitaRequest.Ajuste(original.data(), original.atendimento(), "Medico A", "Visita",
                "10102019", new BigDecimal("150"), new BigDecimal("127.50"), ConciliadorVisitas.Status.PAGA, "Valor corrigido");
        var revista = aplicador.aplicar(original, relatorio(List.of(original)),
                new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.AJUSTAR_DADOS, 10, "Revisor", "Valor contratual conferido", 0L, ajuste));
        assertThat(revista.hospital().repasse()).isEqualByComparingTo("85.085");
        assertThat(aplicador.recalcular(relatorio(List.of(original)), List.of(revista)).repasseCorrespondente()).isEqualByComparingTo("127.50");
        assertThat(aplicador.aplicar(original, relatorio(List.of(revista)), request(RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO, null)))
                .isEqualTo(original);
    }

    @Test
    void naoPermiteStatusPagoSemRegistroFaturado() {
        var original = visita(2);
        var ajuste = new RevisaoVisitaRequest.Ajuste(original.data(), original.atendimento(), "Medico A", "Visita",
                "10102019", new BigDecimal("120"), new BigDecimal("102"), ConciliadorVisitas.Status.PAGA, "Valor preenchido");
        assertThatThrownBy(() -> aplicador.aplicar(original, relatorio(List.of(original)),
                new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.AJUSTAR_DADOS, null, "Revisor", "Dados preenchidos", 0L, ajuste)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mudarChaveRemoveVinculoAnterior() {
        var original = visita(2);
        var confirmada = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        var ajuste = new RevisaoVisitaRequest.Ajuste(LocalDate.of(2026, 1, 2), "456", "Medico A", "Visita",
                "10102019", null, null, ConciliadorVisitas.Status.PENDENTE, "Data e atendimento corrigidos");
        var revista = aplicador.aplicar(original, relatorio(List.of(confirmada)),
                new RevisaoVisitaRequest(RevisaoVisitaRequest.Acao.AJUSTAR_DADOS, null, "Revisor", "Chave corrigida", 0L, ajuste));
        assertThat(revista.hospital()).isNull(); assertThat(revista.candidatos()).isEmpty();
        assertThat(aplicador.recalcular(relatorio(List.of(original)), List.of(revista)).hospitalSemProducao()).hasSize(2);
    }
    private ConciliadorVisitas.Visita visita(int linha) {
        return new ConciliadorVisitas.Visita(linha, "123", LocalDate.of(2026, 1, 1), ConciliadorVisitas.Status.DIVERGENTE,
                "Conferir", Map.of("atendimento", "123"), null,
                List.of(hospital(10, "Faturado", "Medico A"), hospital(11, "Faturado", "Medico B")));
    }

    @Test
    void confirmaCandidatoEPreencheDataQuandoProducaoNaoTemData() {
        var modelo = visita(2);
        var original = new ConciliadorVisitas.Visita(2, "123", null, modelo.status(), modelo.motivo(),
                modelo.original(), null, modelo.candidatos());
        var revista = aplicador.aplicar(original, relatorio(List.of(original)), request(RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA, 10));
        assertThat(revista.data()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(revista.medicoEfetivo()).isEqualTo("Medico A");
        assertThat(revista.status()).isEqualTo(ConciliadorVisitas.Status.PAGA);
        assertThat(aplicador.aplicar(original, relatorio(List.of(revista)), request(RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO, null)))
                .isEqualTo(original);
    }
    private ConciliadorVisitas.RegistroHospital hospital(int linha, String setor, String medico) {
        return new ConciliadorVisitas.RegistroHospital(linha, "123", LocalDate.of(2026, 1, 1), "Paciente teste", "Convenio teste",
                medico, "10102019", "Visita", "C1", setor, new BigDecimal("100.10"), new BigDecimal("0.85"), new BigDecimal("85.085"));
    }
    private ConciliadorVisitas.Relatorio relatorio(List<ConciliadorVisitas.Visita> visitas) {
        return new ConciliadorVisitas.Relatorio("Hospital teste", "producao.xlsx", "hospital.xlsb", "Faturado", visitas,
                List.of(), Map.of(), BigDecimal.ZERO);
    }
}
