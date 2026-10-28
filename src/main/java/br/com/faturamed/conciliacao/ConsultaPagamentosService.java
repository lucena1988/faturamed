package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import br.com.faturamed.shared.RecursoNaoEncontradoException;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ConsultaPagamentosService {
    public enum Situacao { TODOS, SEM_ESTORNO, PARCIALMENTE_ESTORNADO, ESTORNADO, CONFERENCIA }
    public record Alocacao(long lancamento,int linha,String atendimento,LocalDate dataVisita,String codigo,BigDecimal valor,BigDecimal estornos,BigDecimal liquido) {}
    public record Evento(long id,int linha,LocalDate data,BigDecimal valor,Long estornoDe,String observacoes,String responsavel,Instant criadoEm) {}
    public record Pagamento(long id,long relatorioOrigem,long medicoId,String medico,String referencia,LocalDate data,
            BigDecimal valorOriginal,BigDecimal estornos,BigDecimal liquido,Situacao situacao,String observacoes,String responsavel,
            Instant criadoEm,List<Alocacao> visitas,List<Evento> historico) {}
    public record Resultado(String hospital,List<Pagamento> pagamentos,BigDecimal valorOriginal,BigDecimal estornos,BigDecimal liquido) {}
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    public ConsultaPagamentosService(JdbcTemplate jdbc,RevisaoVisitasService revisoes){this.jdbc=jdbc;this.revisoes=revisoes;}
    private Resultado carregar(long id) throws Exception {
        var base=revisoes.carregar(id).atual();
        Long raiz=jdbc.queryForObject("""
            with recursive cadeia as (select id,anterior_id from conciliacao_visitas where id=?
            union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id)
            select id from cadeia where anterior_id is null
            """,Long.class,id);
        var pagamentos=jdbc.queryForList("select p.*,m.nome as medico from pagamento_visitas p join medico_cadastro m on m.id=p.medico_id where p.relatorio_raiz_id=? order by p.data_recebimento desc,p.id desc",raiz);
        var eventos=jdbc.queryForList("select r.* from recebimento_visita r join pagamento_visitas p on p.id=r.pagamento_id where p.relatorio_raiz_id=? order by r.id",raiz);
        Map<Long,List<Map<String,Object>>> porPagamento=new HashMap<>();for(var e:eventos)porPagamento.computeIfAbsent(numero(e,"pagamento_id"),k->new ArrayList<>()).add(e);
        var lista=pagamentos.stream().map(p->montar(p,porPagamento.getOrDefault(numero(p,"id"),List.of()))).toList();
        return resultado(base.hospital(),lista);
    }
    public Resultado listar(long id,String referencia,Long medicoId,LocalDate de,LocalDate ate,Situacao situacao) throws Exception {
        if(referencia==null||referencia.length()>120)throw new IllegalArgumentException("Referencia deve ter ate 120 caracteres");
        if(medicoId!=null&&medicoId<=0)throw new IllegalArgumentException("Medico invalido");
        if(de!=null&&ate!=null&&de.isAfter(ate))throw new IllegalArgumentException("Periodo do pagamento invertido");
        var base=carregar(id);String busca=referencia.trim().toLowerCase(Locale.ROOT);
        return resultado(base.hospital(),base.pagamentos().stream().filter(p->p.referencia().toLowerCase(Locale.ROOT).contains(busca))
                .filter(p->medicoId==null||p.medicoId()==medicoId).filter(p->de==null||!p.data().isBefore(de)).filter(p->ate==null||!p.data().isAfter(ate))
                .filter(p->situacao==Situacao.TODOS||p.situacao()==situacao).toList());
    }
    public Pagamento detalhar(long id,long pagamento) throws Exception {
        return carregar(id).pagamentos().stream().filter(p->p.id()==pagamento).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Pagamento nao encontrado neste relatorio"));
    }
    static Pagamento montar(Map<String,Object> p,List<Map<String,Object>> rows) {
        Map<Long,BigDecimal> estornos=new HashMap<>();List<Evento> historico=new ArrayList<>();boolean invalido=false;BigDecimal totalEstornos=BigDecimal.ZERO;
        for(var r:rows){Long origem=r.get("estorno_de")==null?null:numero(r,"estorno_de");BigDecimal valor=(BigDecimal)r.get("valor");
            if(valor.signum()<0)totalEstornos=totalEstornos.subtract(valor);
            if(origem!=null){estornos.merge(origem,valor.negate(),BigDecimal::add);if(valor.signum()>=0)invalido=true;}else if(valor.signum()<=0)invalido=true;
            historico.add(new Evento(numero(r,"id"),((Number)r.get("linha_medico")).intValue(),data(r.get("data_recebimento")),valor,origem,texto(r,"observacoes"),texto(r,"responsavel"),instant(r.get("criado_em"))));
        }
        List<Alocacao> visitas=new ArrayList<>();Set<Long> origens=new HashSet<>();BigDecimal alocado=BigDecimal.ZERO;
        for(var r:rows)if(r.get("estorno_de")==null){long evento=numero(r,"id");origens.add(evento);BigDecimal valor=(BigDecimal)r.get("valor"),estorno=estornos.getOrDefault(evento,BigDecimal.ZERO);
            if(estorno.signum()<0||estorno.compareTo(valor)>0)invalido=true;alocado=alocado.add(valor);
            visitas.add(new Alocacao(evento,((Number)r.get("linha_medico")).intValue(),texto(r,"atendimento"),data(r.get("data_visita")),texto(r,"codigo_procedimento"),valor,estorno,valor.subtract(estorno)));
        }
        BigDecimal original=(BigDecimal)p.get("valor_total");if(alocado.compareTo(original)!=0||!origens.containsAll(estornos.keySet()))invalido=true;
        Situacao situacao=invalido?Situacao.CONFERENCIA:totalEstornos.signum()==0?Situacao.SEM_ESTORNO:totalEstornos.compareTo(original)==0?Situacao.ESTORNADO:Situacao.PARCIALMENTE_ESTORNADO;
        BigDecimal liquido=historico.stream().map(Evento::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Pagamento(numero(p,"id"),numero(p,"relatorio_id"),numero(p,"medico_id"),texto(p,"medico"),texto(p,"referencia"),data(p.get("data_recebimento")),original,totalEstornos,liquido,situacao,texto(p,"observacoes"),texto(p,"responsavel"),instant(p.get("criado_em")),List.copyOf(visitas),List.copyOf(historico));
    }
    private static Resultado resultado(String hospital,List<Pagamento> lista){BigDecimal original=BigDecimal.ZERO,estornos=BigDecimal.ZERO,liquido=BigDecimal.ZERO;for(var p:lista){original=original.add(p.valorOriginal());estornos=estornos.add(p.estornos());liquido=liquido.add(p.liquido());}return new Resultado(hospital,lista,original,estornos,liquido);}
    private static long numero(Map<String,Object> r,String campo){return ((Number)r.get(campo)).longValue();}
    private static String texto(Map<String,Object> r,String campo){return Objects.toString(r.get(campo),"");}
    private static LocalDate data(Object v){return v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v;}
    private static Instant instant(Object v){return v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();}
}
