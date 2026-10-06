package br.com.faturamed.importacao.api;

import br.com.faturamed.importacao.Importacao;
import java.time.LocalDateTime;

public record ImportacaoResponse(
        Long id,
        Long empresaId,
        String tipo,
        String nomeArquivo,
        String status,
        int totalLinhas,
        int totalProcessadas,
        int totalErros,
        LocalDateTime criadoEm
) {

    static ImportacaoResponse from(Importacao importacao) {
        return new ImportacaoResponse(
                importacao.getId(),
                importacao.getEmpresa().getId(),
                importacao.getTipo().name(),
                importacao.getNomeArquivo(),
                importacao.getStatus().name(),
                importacao.getTotalLinhas(),
                importacao.getTotalProcessadas(),
                importacao.getTotalErros(),
                importacao.getCriadoEm()
        );
    }
}
