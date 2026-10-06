package br.com.faturamed.importacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarImportacaoRequest(
        @NotNull Long empresaId,
        Long unidadeId,
        Long layoutImportacaoId,
        @NotBlank @Size(max = 30) String tipo,
        @NotBlank @Size(max = 255) String nomeArquivo
) {
}
