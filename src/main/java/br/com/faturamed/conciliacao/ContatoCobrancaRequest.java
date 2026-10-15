package br.com.faturamed.conciliacao;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ContatoCobrancaRequest(@NotNull Situacao situacao, LocalDate dataCobranca,
        @Size(max=2000) String resposta, @Size(max=2000) String observacoes,
        LocalDate proximoContato, @NotNull @PositiveOrZero Long versao) {
    public enum Situacao { NAO_COBRADA, ENVIADA, AGUARDANDO_RETORNO, RETORNO_RECEBIDO }
    public void validar(LocalDate hoje) {
        if(situacao==null||versao==null||versao<0)throw new IllegalArgumentException("Informe situacao e versao do acompanhamento");
        if(situacao!=Situacao.NAO_COBRADA&&dataCobranca==null)throw new IllegalArgumentException("Informe a data da cobranca");
        if(situacao==Situacao.NAO_COBRADA&&dataCobranca!=null)throw new IllegalArgumentException("Nao cobrada deve ficar sem data de cobranca");
        if(dataCobranca!=null&&dataCobranca.isAfter(hoje))throw new IllegalArgumentException("Data da cobranca nao pode ser futura");
        if(proximoContato!=null&&dataCobranca!=null&&proximoContato.isBefore(dataCobranca))throw new IllegalArgumentException("Proximo contato nao pode ser anterior a cobranca");
        if(situacao==Situacao.RETORNO_RECEBIDO&&(resposta==null||resposta.isBlank()))throw new IllegalArgumentException("Registre a resposta do hospital");
    }
}
