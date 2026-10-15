package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public record CobrancaLoteRequest(@NotNull @Size(min=1,max=500) List<@NotNull @Valid Selecao> visitas,
        @NotNull ContatoCobrancaRequest.Situacao situacao, LocalDate dataCobranca,
        @Size(max=2000) String resposta, @Size(max=2000) String observacoes, LocalDate proximoContato) {
    public record Selecao(@NotNull @Positive Integer linha,@NotNull @PositiveOrZero Long versao) {}
    public ContatoCobrancaRequest contato(long versao) {
        return new ContatoCobrancaRequest(situacao,dataCobranca,resposta,observacoes,proximoContato,versao);
    }
    public void validar(LocalDate hoje) {
        if(visitas==null||visitas.isEmpty()||visitas.size()>500)throw new IllegalArgumentException("Selecione de 1 a 500 visitas por lote");
        Set<Integer> linhas=new HashSet<>();
        for(var v:visitas) {
            if(v==null||v.linha()==null||v.linha()<=0||v.versao()==null||v.versao()<0)throw new IllegalArgumentException("Selecao de visita invalida");
            if(!linhas.add(v.linha()))throw new IllegalArgumentException("Nao repita a mesma linha no lote");
        }
        contato(0).validar(hoje);
    }
}
