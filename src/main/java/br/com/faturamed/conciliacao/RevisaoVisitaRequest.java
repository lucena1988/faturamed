package br.com.faturamed.conciliacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RevisaoVisitaRequest(
        @NotNull Acao acao,
        @Positive Integer linhaHospital,
        @NotBlank @Size(max = 180) String responsavel,
        @NotBlank @Size(min = 5, max = 2000) String justificativa,
        @NotNull @PositiveOrZero Long versao,
        @Valid Ajuste ajuste) {
    public RevisaoVisitaRequest(Acao acao, Integer linhaHospital, String responsavel, String justificativa, Long versao) {
        this(acao, linhaHospital, responsavel, justificativa, versao, null);
    }
    public record Ajuste(@NotNull LocalDate data, @NotBlank @Size(max = 80) String atendimento,
            @NotNull @Size(max = 180) String medico, @NotNull @Size(max = 2000) String procedimento,
            @Size(max = 40) String codigoProcedimento,
            @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal valorHospital,
            @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal repasse,
            @NotNull ConciliadorVisitas.Status status, @NotBlank @Size(max = 2000) String motivo) {}
    public enum Acao {
        CONFIRMAR_CORRESPONDENCIA, MANTER_PENDENTE, MANTER_DIVERGENTE, RESTAURAR_AUTOMATICO, AJUSTAR_DADOS
    }
}
