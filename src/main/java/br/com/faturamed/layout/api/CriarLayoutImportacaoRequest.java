package br.com.faturamed.layout.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CriarLayoutImportacaoRequest(
        @NotNull Long empresaId,
        @NotBlank @Size(max = 180) String nome,
        @NotBlank @Size(max = 30) String tipo,
        @Size(max = 5) String delimitadorCsv,
        boolean primeiraLinhaCabecalho,
        @Valid @NotEmpty List<CriarLayoutCampoRequest> campos
) {
}
