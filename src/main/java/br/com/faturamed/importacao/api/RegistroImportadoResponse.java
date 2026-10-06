package br.com.faturamed.importacao.api;

import br.com.faturamed.importacao.RegistroImportado;

public record RegistroImportadoResponse(
        Long id,
        Long importacaoId,
        int numeroLinha,
        String origem,
        String pacienteNome,
        String procedimentoCodigo,
        String chaveCruzamento
) {

    static RegistroImportadoResponse from(RegistroImportado registro) {
        return new RegistroImportadoResponse(
                registro.getId(),
                registro.getImportacao().getId(),
                registro.getNumeroLinha(),
                registro.getOrigem().name(),
                registro.getPacienteNome(),
                registro.getProcedimentoCodigo(),
                registro.getChaveCruzamento()
        );
    }
}
