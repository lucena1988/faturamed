package br.com.faturamed.layout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarLayoutCampoRequest(
        @NotBlank @Size(max = 80) String campoPadronizado,
        @NotBlank @Size(max = 20) String colunaOrigem,
        boolean obrigatorio,
        @Size(max = 80) String formato
) {
}
