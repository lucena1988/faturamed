package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PagamentoDistribuidoRequest(@NotNull @Positive Long medicoId,@NotNull LocalDate data,
        @NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal valorTotal,
        @NotBlank @Size(max=120) String referencia,@NotNull @Size(max=2000) String observacoes,
        @NotNull @Size(min=1,max=500) List<@NotNull @Valid Alocacao> visitas,@NotNull @PositiveOrZero Long versaoConciliacao) {
    public PagamentoDistribuidoRequest(Long medicoId,LocalDate data,BigDecimal valorTotal,String referencia,String observacoes,List<Alocacao> visitas) {
        this(medicoId,data,valorTotal,referencia,observacoes,visitas,0L);
    }
    public record Alocacao(@NotNull @Positive Integer linha,@NotNull @PositiveOrZero Long versao,
            @NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal valor) {}
    public void validar(LocalDate hoje) {
        if(versaoConciliacao==null||versaoConciliacao<0)throw new IllegalArgumentException("Informe a versao da conciliacao");
        if(medicoId==null||medicoId<=0)throw new IllegalArgumentException("Selecione o medico cadastrado");
        if(referencia==null||referencia.isBlank()||referencia.length()>120)throw new IllegalArgumentException("Informe referencia de ate 120 caracteres");
        if(observacoes==null||observacoes.length()>2000)throw new IllegalArgumentException("Observacoes invalidas");
        RecebimentoService.validar(new RecebimentoService.Dados(valorTotal,data,observacoes,0L),hoje);
        if(visitas==null||visitas.isEmpty()||visitas.size()>500)throw new IllegalArgumentException("Selecione de 1 a 500 visitas");
        Set<Integer> linhas=new HashSet<>();BigDecimal soma=BigDecimal.ZERO;
        for(var v:visitas) {
            if(v==null||v.linha()==null||v.linha()<=0||v.versao()==null||v.versao()<0)throw new IllegalArgumentException("Selecao de visita invalida");
            if(!linhas.add(v.linha()))throw new IllegalArgumentException("Linha repetida na distribuicao");
            RecebimentoService.validar(new RecebimentoService.Dados(v.valor(),data,observacoes,v.versao()),hoje);soma=soma.add(v.valor());
        }
        if(soma.compareTo(valorTotal)!=0)throw new IllegalArgumentException("Total distribuido precisa ser igual ao valor do pagamento");
    }
}
