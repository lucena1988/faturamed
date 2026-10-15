package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class PrioridadesCobranca {
    public enum Filtro { TODAS, NAO_COBRADAS, HOJE, ATRASADAS, AGUARDANDO_RETORNO }
    public FilaCobranca.Resultado aplicar(FilaCobranca.Resultado fila,List<AcompanhamentoCobrancaService.Resumo> contatos,Filtro filtro) {
        Map<Integer,AcompanhamentoCobrancaService.Contato> porLinha=new HashMap<>();
        contatos.forEach(c->porLinha.put(c.linha(),c.atual()));
        Map<Filtro,Long> contadores=new EnumMap<>(Filtro.class);
        for(var tipo:Filtro.values())contadores.put(tipo,fila.visitas().stream().filter(v->aceita(tipo,porLinha.get(v.linha()),fila.referencia())).count());
        Comparator<FilaCobranca.Item> ordem=Comparator.comparing(v->proximo(porLinha.get(v.linha())),Comparator.nullsLast(Comparator.naturalOrder()));
        var visitas=fila.visitas().stream().filter(v->aceita(filtro,porLinha.get(v.linha()),fila.referencia()))
                .sorted(ordem.thenComparing(FilaCobranca.Item::data,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(FilaCobranca.Item::linha)).toList();
        return new FilaCobranca.Resultado(fila.hospital(),fila.referencia(),fila.totalSemCorrespondencia(),fila.medicos(),visitas,
                visitas.stream().map(FilaCobranca.Item::repasse).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),
                visitas.stream().filter(v->v.repasse()==null).count(),visitas.stream().filter(v->v.medico().isBlank()).count(),
                visitas.stream().filter(v->v.data()==null).count(),Map.copyOf(contadores));
    }
    private LocalDate proximo(AcompanhamentoCobrancaService.Contato contato) { return contato==null?null:contato.proximoContato(); }
    private boolean aceita(Filtro filtro,AcompanhamentoCobrancaService.Contato contato,LocalDate hoje) {
        var situacao=contato==null?ContatoCobrancaRequest.Situacao.NAO_COBRADA:contato.situacao();
        LocalDate data=proximo(contato);
        return switch(filtro) {
            case TODAS -> true;
            case NAO_COBRADAS -> situacao==ContatoCobrancaRequest.Situacao.NAO_COBRADA;
            case AGUARDANDO_RETORNO -> situacao==ContatoCobrancaRequest.Situacao.AGUARDANDO_RETORNO;
            case HOJE -> data!=null&&data.equals(hoje);
            case ATRASADAS -> data!=null&&data.isBefore(hoje);
        };
    }
}
