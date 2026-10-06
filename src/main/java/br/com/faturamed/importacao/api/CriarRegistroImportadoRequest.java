package br.com.faturamed.importacao.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CriarRegistroImportadoRequest(
        @Min(1) int numeroLinha,
        @NotBlank @Size(max = 30) String origem,
        @Size(max = 180) String pacienteNome,
        @Size(max = 40) String pacienteDocumento,
        @Size(max = 80) String atendimentoCodigo,
        @Size(max = 80) String guiaCodigo,
        LocalDate dataAtendimento,
        @Size(max = 180) String medicoNome,
        @Size(max = 40) String medicoDocumento,
        @NotNull @Size(max = 80) String procedimentoCodigo,
        @Size(max = 255) String procedimentoNome,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        JsonNode dadosOriginais
) {
}
