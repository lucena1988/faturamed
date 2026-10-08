package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ConciliadorVisitas {
    public enum Status { PAGA, PENDENTE, DIVERGENTE }
    public record Chave(String atendimento, LocalDate data) {}
    public record RegistroHospital(int linha, String atendimento, LocalDate data, String paciente,
            String convenio, String medico, String codigo, String procedimento, String conta,
            String setor, BigDecimal valorTotal, BigDecimal regra, BigDecimal repasse) {}
    public record Visita(int linhaMedico, String atendimento, LocalDate data, Status status, String motivo,
            Map<String, String> original, RegistroHospital hospital, List<RegistroHospital> candidatos,
            CamposRevisados camposRevisados) {
        public Visita(int linhaMedico, String atendimento, LocalDate data, Status status, String motivo,
                Map<String, String> original, RegistroHospital hospital, List<RegistroHospital> candidatos) {
            this(linhaMedico, atendimento, data, status, motivo, original, hospital, candidatos, null);
        }
        @com.fasterxml.jackson.annotation.JsonProperty(value = "medico", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String medicoEfetivo() { return camposRevisados != null ? camposRevisados.medico()
                : hospital != null ? hospital.medico() : original.getOrDefault("medico", ""); }
        @com.fasterxml.jackson.annotation.JsonProperty(value = "procedimento", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String procedimentoEfetivo() { return camposRevisados != null ? camposRevisados.procedimento()
                : hospital != null ? hospital.procedimento() : original.getOrDefault("procedimento/mat-med", ""); }
        @com.fasterxml.jackson.annotation.JsonProperty(value = "codigoProcedimento", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String codigoEfetivo() { return camposRevisados != null ? camposRevisados.codigoProcedimento() : hospital == null ? null : hospital.codigo(); }
        @com.fasterxml.jackson.annotation.JsonProperty(value = "valorHospital", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public BigDecimal valorEfetivo() { return camposRevisados != null ? camposRevisados.valorHospital() : hospital == null ? null : hospital.valorTotal(); }
        @com.fasterxml.jackson.annotation.JsonProperty(value = "repasse", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public BigDecimal repasseEfetivo() { return camposRevisados != null ? camposRevisados.repasse() : hospital == null ? null : hospital.repasse(); }
    }
    public record CamposRevisados(String medico, String procedimento, String codigoProcedimento,
            BigDecimal valorHospital, BigDecimal repasse) {}
    public record Relatorio(String hospital, String arquivoMedico, String arquivoHospital,
            String regraStatus, List<Visita> visitas, List<RegistroHospital> hospitalSemProducao,
            Map<Status, Long> resumo, BigDecimal repasseCorrespondente) {}

    public Relatorio conciliar(String hospital, String arquivoMedico, String arquivoHospital,
            List<LeitorPlanilhaVisitas.Linha> medico, List<LeitorPlanilhaVisitas.Linha> hospitalLinhas) {
        Map<Chave, List<RegistroHospital>> porChave = new LinkedHashMap<>();
        for (var linha : hospitalLinhas) {
            var registro = new RegistroHospital(linha.numero(),
                    LeitorPlanilhaVisitas.identificador(linha.campo("Atendimento")),
                    LeitorPlanilhaVisitas.data(linha.campo("Data Consumo"), linha.numero()),
                    linha.campo("Paciente"), linha.campo("Operadora"), linha.campo("Medico"),
                    LeitorPlanilhaVisitas.identificador(linha.campo("Cod Produto")), linha.campo("Produto"),
                    LeitorPlanilhaVisitas.identificador(linha.campo("Conta Paciente")), linha.campo("Setor"),
                    numero(linha.campo("Valor Tot"), linha.numero()), numero(linha.campo("Regra"), linha.numero()),
                    numero(linha.campo("Vl. A Repassar"), linha.numero()));
            porChave.computeIfAbsent(new Chave(registro.atendimento(), registro.data()), k -> new ArrayList<>()).add(registro);
        }
        Map<Chave, Long> contagens = new HashMap<>();
        for (var linha : medico) contagens.merge(chaveMedico(linha), 1L, Long::sum);
        List<Visita> visitas = new ArrayList<>();
        for (var linha : medico) {
            Chave chave = chaveMedico(linha);
            var candidatos = porChave.getOrDefault(chave, List.of());
            RegistroHospital unico = candidatos.size() == 1 && contagens.get(chave) == 1 ? candidatos.getFirst() : null;
            Status status;
            String motivo;
            if (contagens.get(chave) > 1) {
                status = Status.DIVERGENTE;
                motivo = "Atendimento e data repetidos na producao; medico/procedimento nao identificados";
            } else if (candidatos.isEmpty()) {
                status = Status.PENDENTE;
                motivo = "Sem correspondente no arquivo do hospital; pode constar em outra competencia";
            } else if (candidatos.size() > 1) {
                status = Status.DIVERGENTE;
                motivo = "Mais de um registro hospitalar para atendimento e data; conferir medico, procedimento e conta";
            } else if (unico.medico().isBlank() || unico.codigo().isBlank() || unico.valorTotal() == null || unico.repasse() == null) {
                status = Status.DIVERGENTE;
                motivo = "Registro hospitalar com dados incompletos";
            } else if (conflita(linha, unico)) {
                status = Status.DIVERGENTE;
                motivo = "Dados preenchidos na producao divergem do hospital";
            } else if (unico.regra() != null && unico.valorTotal().multiply(unico.regra()).subtract(unico.repasse()).abs()
                    .compareTo(new BigDecimal("0.01")) > 0) {
                status = Status.DIVERGENTE;
                motivo = "Repasse informado diverge de Valor Tot x Regra";
            } else if (LeitorPlanilhaVisitas.normalizar(unico.setor()).equals("faturado")) {
                status = Status.PAGA;
                motivo = "Correspondencia unica no faturamento do hospital";
            } else {
                status = Status.PENDENTE;
                motivo = "Correspondencia encontrada, mas setor do hospital nao indica Faturado";
            }
            visitas.add(new Visita(linha.numero(), chave.atendimento(), chave.data(), status, motivo,
                    linha.campos(), unico, List.copyOf(candidatos)));
        }
        var semProducao = porChave.entrySet().stream().filter(e -> !contagens.containsKey(e.getKey()))
                .flatMap(e -> e.getValue().stream()).toList();
        Map<Status, Long> resumo = new EnumMap<>(Status.class);
        for (Status status : Status.values()) resumo.put(status, visitas.stream().filter(v -> v.status() == status).count());
        BigDecimal repasse = visitas.stream().filter(v -> v.status() == Status.PAGA)
                .map(v -> v.hospital().repasse()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Relatorio(hospital, arquivoMedico, arquivoHospital,
                "PAGA significa correspondencia unica no setor Faturado do hospital; nao comprova recebimento financeiro.",
                List.copyOf(visitas), semProducao, resumo, repasse);
    }

    private Chave chaveMedico(LeitorPlanilhaVisitas.Linha linha) {
        return new Chave(LeitorPlanilhaVisitas.identificador(linha.campo("Atendimento")),
                LeitorPlanilhaVisitas.data(linha.campo("Dt."), linha.numero()));
    }

    private boolean conflita(LeitorPlanilhaVisitas.Linha linha, RegistroHospital hospital) {
        String medico = linha.campo("Medico");
        String procedimento = linha.campo("Procedimento/Mat-Med");
        BigDecimal valor = numero(linha.campo("Valor Orig"), linha.numero());
        return (!medico.isBlank() && !LeitorPlanilhaVisitas.normalizar(medico).equals(LeitorPlanilhaVisitas.normalizar(hospital.medico())))
                || (!procedimento.isBlank() && !procedimento.equals(hospital.codigo())
                    && !LeitorPlanilhaVisitas.normalizar(procedimento).equals(LeitorPlanilhaVisitas.normalizar(hospital.procedimento())))
                || (valor != null && valor.compareTo(hospital.valorTotal()) != 0);
    }

    private static BigDecimal numero(String valor, int linha) {
        if (valor.isBlank()) return null;
        try { return new BigDecimal(valor); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Valor numerico invalido na linha " + linha); }
    }
}
