package br.com.faturamed.layout.api;

import br.com.faturamed.layout.LayoutImportacao;
import java.time.LocalDateTime;
import java.util.List;

public record LayoutImportacaoResponse(
        Long id,
        Long empresaId,
        String nome,
        String tipo,
        String delimitadorCsv,
        boolean primeiraLinhaCabecalho,
        boolean ativo,
        LocalDateTime criadoEm,
        List<LayoutCampoResponse> campos
) {

    static LayoutImportacaoResponse from(LayoutImportacao layout) {
        return new LayoutImportacaoResponse(
                layout.getId(),
                layout.getEmpresa().getId(),
                layout.getNome(),
                layout.getTipo().name(),
                layout.getDelimitadorCsv(),
                layout.isPrimeiraLinhaCabecalho(),
                layout.isAtivo(),
                layout.getCriadoEm(),
                layout.getCampos().stream().map(LayoutCampoResponse::from).toList()
        );
    }
}
