package br.com.faturamed.layout.api;

import br.com.faturamed.layout.LayoutCampo;

public record LayoutCampoResponse(
        Long id,
        String campoPadronizado,
        String colunaOrigem,
        boolean obrigatorio,
        String formato
) {

    static LayoutCampoResponse from(LayoutCampo campo) {
        return new LayoutCampoResponse(
                campo.getId(),
                campo.getCampoPadronizado(),
                campo.getColunaOrigem(),
                campo.isObrigatorio(),
                campo.getFormato()
        );
    }
}
