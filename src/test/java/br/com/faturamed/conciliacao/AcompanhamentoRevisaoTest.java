package br.com.faturamed.conciliacao;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AcompanhamentoRevisaoTest {
    private final AcompanhamentoRevisao motor = new AcompanhamentoRevisao();
    private ConciliadorVisitas.RegistroHospital hospital(String medico, String setor, String repasse) {
        return new ConciliadorVisitas.RegistroHospital(10,"123",LocalDate.of(2026,1,1),"Paciente","Convenio",medico,
                "10102019","Visita","Conta",setor,new BigDecimal("100"),new BigDecimal("0.85"),repasse==null?null:new BigDecimal(repasse));
    }
    private ConciliadorVisitas.Visita visita(ConciliadorVisitas.Status status, ConciliadorVisitas.RegistroHospital h) {
        return new ConciliadorVisitas.Visita(2,"123",h==null?null:h.data(),status,"Motivo livre",Map.of(),h,h==null?List.of():List.of(h));
    }
    private AcompanhamentoRevisao.Item analisar(ConciliadorVisitas.Visita visita, RevisaoVisitaRequest.Acao acao) {
        var r=new ConciliadorVisitas.Relatorio("Hospital","m.xlsx","h.xlsx","Faturado",List.of(visita),List.of(),Map.of(),BigDecimal.ZERO);
        var historico=acao==null?List.<RevisaoVisitasService.Revisao>of():List.of(new RevisaoVisitasService.Revisao(1,2,acao,"Revisor","Conferido",Instant.now(),visita,visita));
        return motor.analisar(new RevisaoVisitasService.Estado(r,r,historico,1)).visitas().getFirst();
    }
    @Test void pagamentoAutomaticoCompletoNaoExigeRevisaoManual() {
        var item=analisar(visita(ConciliadorVisitas.Status.PAGA,hospital("Medico","Faturado","85")),null);
        assertThat(item.origem()).isEqualTo(AcompanhamentoRevisao.Origem.AUTOMATICA_IMPORTACAO);
        assertThat(item.precisaConferencia()).isFalse();
    }
    @Test void pendenteConferidaPodeSerEnviadaSemConfundirComPagamento() {
        var v=visita(ConciliadorVisitas.Status.PENDENTE,hospital("Medico","Em analise","85"));
        assertThat(analisar(v,null).precisaConferencia()).isTrue();
        var item=analisar(v,RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA);
        assertThat(item.categoria()).isEqualTo(AcompanhamentoRevisao.Categoria.AGUARDANDO_FATURAMENTO);
        assertThat(item.precisaConferencia()).isFalse();
    }
    @Test void repasseInconsistenteContinuaPrecisandoConferenciaAposRevisao() {
        var item=analisar(visita(ConciliadorVisitas.Status.DIVERGENTE,hospital("Medico","Faturado","90")),RevisaoVisitaRequest.Acao.AJUSTAR_DADOS);
        assertThat(item.categoria()).isEqualTo(AcompanhamentoRevisao.Categoria.VALOR_DIVERGENTE);
        assertThat(item.precisaConferencia()).isTrue();
    }
    @Test void dadosFaltantesNaoFicamProntosApenasPorqueHouveRevisao() {
        var item=analisar(visita(ConciliadorVisitas.Status.PENDENTE,hospital("","Faturado",null)),RevisaoVisitaRequest.Acao.AJUSTAR_DADOS);
        assertThat(item.categoria()).isEqualTo(AcompanhamentoRevisao.Categoria.DADOS_FALTANTES);
        assertThat(item.medicoIdentificado()).isFalse();assertThat(item.precisaConferencia()).isTrue();
    }
    @Test void restauracaoRetornaFilaNaoRevisada() {
        var item=analisar(visita(ConciliadorVisitas.Status.PENDENTE,null),RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO);
        assertThat(item.origem()).isEqualTo(AcompanhamentoRevisao.Origem.NAO_REVISADA);
        assertThat(item.categoria()).isEqualTo(AcompanhamentoRevisao.Categoria.SEM_CORRESPONDENCIA);
    }
    @Test void variosCandidatosSemVinculoSaoAmbiguidadeNaoValorDivergente() {
        var a=hospital("Medico A","Faturado","85");
        var b=new ConciliadorVisitas.RegistroHospital(11,a.atendimento(),a.data(),a.paciente(),a.convenio(),"Medico B",a.codigo(),a.procedimento(),a.conta(),a.setor(),a.valorTotal(),a.regra(),a.repasse());
        var v=new ConciliadorVisitas.Visita(2,"123",null,ConciliadorVisitas.Status.DIVERGENTE,"Conferir",Map.of(),null,List.of(a,b));
        assertThat(analisar(v,null).categoria()).isEqualTo(AcompanhamentoRevisao.Categoria.AMBIGUIDADE);
    }
}
