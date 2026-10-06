package br.com.faturamed.importacao;

import br.com.faturamed.domain.OrigemRegistro;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistroConferencia(
        Long id,
        OrigemRegistro origem,
        String pacienteDocumento,
        String pacienteNome,
        String atendimentoCodigo,
        LocalDate dataAtendimento,
        String medicoNome,
        String procedimentoCodigo,
        String procedimentoNome,
        BigDecimal quantidade,
        BigDecimal valorTotal
) {

    public String chavePrincipal() {
        return normalizar(pacienteDocumento)
                + "|"
                + normalizarData(dataAtendimento)
                + "|"
                + normalizar(procedimentoCodigo);
    }

    public String chaveDuplicidade() {
        return chavePrincipal() + "|" + normalizar(medicoNome);
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase();
    }

    private static String normalizarData(LocalDate valor) {
        if (valor == null) {
            return "";
        }
        return valor.toString();
    }
}
