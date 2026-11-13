package br.com.faturamed.conciliacao;

/** Eventos do Fechamento Faturado, ouvidos por outros modulos (ex.: avisos do Robo FaturaMed no chat). */
public final class FechamentoFaturadoEventos {
    private FechamentoFaturadoEventos() {}
    public record PlanilhaHospitalImportada(long hospitalId, String hospital, String competencia, int procedimentos, long usuarioId) {}
    public record ConciliacaoGerada(long relatorioId, String hospital, int procedimentos, int divergencias, long usuarioId) {}
}
