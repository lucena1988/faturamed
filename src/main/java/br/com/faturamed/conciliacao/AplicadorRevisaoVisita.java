package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AplicadorRevisaoVisita {
    public ConciliadorVisitas.Visita aplicar(ConciliadorVisitas.Visita original,
            ConciliadorVisitas.Relatorio atual, RevisaoVisitaRequest request) {
        return aplicar(original, atual, request, atual);
    }

    public ConciliadorVisitas.Visita aplicar(ConciliadorVisitas.Visita original,
            ConciliadorVisitas.Relatorio atual, RevisaoVisitaRequest request, ConciliadorVisitas.Relatorio base) {
        var anterior = atual.visitas().stream().filter(v -> v.linhaMedico() == original.linhaMedico()).findFirst().orElseThrow();
        var campos = anterior.camposRevisados();
        var atendimento = anterior.atendimento();
        var data = anterior.data();
        var candidatos = candidatos(base, atendimento, data);
        ConciliadorVisitas.RegistroHospital hospital = null;
        ConciliadorVisitas.Status status;
        String motivo;
        switch (request.acao()) {
            case CONFIRMAR_CORRESPONDENCIA -> {
                if (request.linhaHospital() == null) throw new IllegalArgumentException("Selecione o registro hospitalar");
                hospital = candidatos.stream().filter(h -> h.linha() == request.linhaHospital()).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Registro hospitalar nao pertence aos candidatos desta visita"));
                if (hospital.medico().isBlank() || hospital.codigo().isBlank() || hospital.valorTotal() == null || hospital.repasse() == null) {
                    throw new IllegalArgumentException("Registro hospitalar incompleto; mantenha a visita pendente ou divergente");
                }
                status = LeitorPlanilhaVisitas.normalizar(hospital.setor()).equals("faturado")
                        ? ConciliadorVisitas.Status.PAGA : ConciliadorVisitas.Status.PENDENTE;
                motivo = "Correspondencia confirmada na revisao: " + request.justificativa().trim();
                data = hospital.data();
            }
            case MANTER_PENDENTE -> {
                status = ConciliadorVisitas.Status.PENDENTE;
                motivo = "Pendente apos revisao: " + request.justificativa().trim();
            }
            case MANTER_DIVERGENTE -> {
                status = ConciliadorVisitas.Status.DIVERGENTE;
                motivo = "Divergente apos revisao: " + request.justificativa().trim();
            }
            case RESTAURAR_AUTOMATICO -> {
                status = original.status(); hospital = original.hospital(); motivo = original.motivo();
                campos = null; atendimento = original.atendimento(); data = original.data(); candidatos = original.candidatos();
            }
            case AJUSTAR_DADOS -> {
                var ajuste = request.ajuste();
                if (ajuste == null) throw new IllegalArgumentException("Informe os dados ajustados");
                atendimento = ajuste.atendimento().trim(); data = ajuste.data(); status = ajuste.status(); motivo = ajuste.motivo().trim();
                campos = new ConciliadorVisitas.CamposRevisados(ajuste.medico().trim(), ajuste.procedimento().trim(),
                        ajuste.codigoProcedimento() == null ? null : ajuste.codigoProcedimento().trim(), ajuste.valorHospital(), ajuste.repasse());
                candidatos = candidatos(base, atendimento, data);
                if (request.linhaHospital() != null) {
                    int linha = request.linhaHospital();
                    hospital = candidatos.stream().filter(h -> h.linha() == linha).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Registro hospitalar nao corresponde ao atendimento e data ajustados"));
                } else if (anterior.hospital() != null && anterior.hospital().atendimento().equals(atendimento)
                        && anterior.hospital().data().equals(data)) hospital = anterior.hospital();
                if (hospital != null && data == null) data = hospital.data();
            }
            default -> throw new IllegalArgumentException("Acao invalida");
        }
        if (request.acao() != RevisaoVisitaRequest.Acao.CONFIRMAR_CORRESPONDENCIA
                && request.acao() != RevisaoVisitaRequest.Acao.AJUSTAR_DADOS && request.linhaHospital() != null) {
            throw new IllegalArgumentException("Esta acao nao recebe um registro hospitalar");
        }
        if (request.acao() != RevisaoVisitaRequest.Acao.AJUSTAR_DADOS && request.ajuste() != null) {
            throw new IllegalArgumentException("Dados ajustados exigem a acao AJUSTAR_DADOS");
        }
        var revisada = new ConciliadorVisitas.Visita(original.linhaMedico(), atendimento, data,
                status, motivo, original.original(), hospital, candidatos, campos);
        if (status == ConciliadorVisitas.Status.PAGA && (hospital == null
                || !LeitorPlanilhaVisitas.normalizar(hospital.setor()).equals("faturado")
                || revisada.medicoEfetivo().isBlank() || revisada.procedimentoEfetivo().isBlank()
                || revisada.valorEfetivo() == null || revisada.repasseEfetivo() == null)) {
            throw new IllegalArgumentException("PAGA exige registro Faturado associado, medico, procedimento, valor e repasse preenchidos");
        }
        if (hospital != null) {
            int linha = hospital.linha();
            boolean reservado = atual.visitas().stream().anyMatch(v -> v.linhaMedico() != original.linhaMedico()
                    && v.hospital() != null && v.hospital().linha() == linha);
            if (reservado) throw new ConflitoRevisaoException("Registro hospitalar ja associado a outra linha do medico");
        }
        return revisada;
    }

    public ConciliadorVisitas.Relatorio recalcular(ConciliadorVisitas.Relatorio base,
            List<ConciliadorVisitas.Visita> visitas) {
        Map<ConciliadorVisitas.Status, Long> resumo = new EnumMap<>(ConciliadorVisitas.Status.class);
        for (var status : ConciliadorVisitas.Status.values()) resumo.put(status, visitas.stream().filter(v -> v.status() == status).count());
        BigDecimal repasse = visitas.stream().filter(v -> v.status() == ConciliadorVisitas.Status.PAGA)
                .map(ConciliadorVisitas.Visita::repasseEfetivo).reduce(BigDecimal.ZERO, BigDecimal::add);
        var chaves = new java.util.HashSet<ConciliadorVisitas.Chave>();
        visitas.forEach(v -> chaves.add(new ConciliadorVisitas.Chave(v.atendimento(), v.data())));
        var semProducao = registrosHospital(base).stream().filter(h ->
                !chaves.contains(new ConciliadorVisitas.Chave(h.atendimento(), h.data()))
                && !chaves.contains(new ConciliadorVisitas.Chave(h.atendimento(), null))).toList();
        return new ConciliadorVisitas.Relatorio(base.hospital(), base.arquivoMedico(), base.arquivoHospital(),
                base.regraStatus(), List.copyOf(visitas), semProducao, resumo, repasse);
    }

    public List<ConciliadorVisitas.RegistroHospital> candidatos(ConciliadorVisitas.Relatorio base, String atendimento, java.time.LocalDate data) {
        return registrosHospital(base).stream().filter(h -> h.atendimento().equals(atendimento)
                && (data == null || h.data().equals(data))).toList();
    }
    private List<ConciliadorVisitas.RegistroHospital> registrosHospital(ConciliadorVisitas.Relatorio base) {
        Map<Integer, ConciliadorVisitas.RegistroHospital> registros = new java.util.LinkedHashMap<>();
        base.hospitalSemProducao().forEach(h -> registros.put(h.linha(), h));
        base.visitas().forEach(v -> v.candidatos().forEach(h -> registros.put(h.linha(), h)));
        return List.copyOf(registros.values());
    }
}
