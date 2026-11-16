package br.com.faturamed.producao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visao geral da producao: o que fazer hoje, numeros do mes, situacao por hospital e mapa de calor de etiquetas.
 * hospitalId opcional restringe tudo a um hospital.
 */
@Service
public class PainelProducaoService {
    public record Tarefas(int lotesRascunho, int etiquetasPendentes, int etiquetasParaFechar, int aguardandoHospital, int atrasados,
            int venceHoje, int contestacoes, int aprovadosSemNf) {}
    public record Mes(BigDecimal fechadoMes, BigDecimal fechadoAnterior, BigDecimal nfMes, int etiquetasMes, int etiquetasAnterior,
            BigDecimal aReceberHospital) {}
    public record Hospital(long id, String nome, BigDecimal aguardando, BigDecimal aprovado, BigDecimal nfMes, int etiquetasParaFechar) {}
    public record Atencao(long fechamentoId, String hospital, BigDecimal total, LocalDate prazo, int diasUteis, boolean vistoPeloHospital) {}
    public record Dia(LocalDate dia, int etiquetas) {}
    public record Painel(Tarefas tarefas, Mes mes, List<Hospital> hospitais, List<Atencao> prazos, List<Dia> mapa, int prazoDias) {}

    private final JdbcTemplate jdbc;
    private final PrazoConferencia prazo;
    public PainelProducaoService(JdbcTemplate jdbc, PrazoConferencia prazo) { this.jdbc = jdbc; this.prazo = prazo; }

    private int inteiro(String sql, List<Object> args) { Integer v = jdbc.queryForObject(sql, Integer.class, args.toArray()); return v == null ? 0 : v; }
    private BigDecimal valor(String sql, List<Object> args) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args.toArray()); return v == null ? BigDecimal.ZERO : v; }
    private static List<Object> args(Long hospitalId, Object... antes) {
        List<Object> a = new ArrayList<>(Arrays.asList(antes));
        if (hospitalId != null) a.add(hospitalId);
        return a;
    }

    private static final String DISPONIVEL = """
            from producao_item i join producao_lote l on l.id=i.lote_id
            where l.status in ('CONFERIDO','ENVIADO')
              and not exists (select 1 from producao_fechamento_item fi where fi.item_id=i.id and fi.situacao='INCLUIDA')
              and (i.contestacao is null or i.contestacao='REINCLUIR')""";

    @Transactional(readOnly = true)
    public Painel painel(Long hospitalId) {
        LocalDate hoje = LocalDate.now();
        YearMonth mes = YearMonth.from(hoje), anterior = mes.minusMonths(1);
        String porLote = hospitalId == null ? "" : " and l.hospital_id=?";
        String porFechamento = hospitalId == null ? "" : " and f.hospital_id=?";

        List<Atencao> prazos = jdbc.query("""
                select f.id, h.nome, f.total, f.prazo_conferencia, f.visto_hospital_em is not null visto
                from producao_fechamento f join hospital_cadastro h on h.id=f.hospital_id
                where f.status='AGUARDANDO' and f.prazo_conferencia is not null""" + porFechamento + " order by f.prazo_conferencia, f.id",
                (r, n) -> new Atencao(r.getLong(1), r.getString(2), r.getBigDecimal(3), r.getObject(4, LocalDate.class),
                        PrazoConferencia.diasUteisAte(hoje, r.getObject(4, LocalDate.class)), r.getBoolean(5)), args(hospitalId).toArray());

        Tarefas tarefas = new Tarefas(
                inteiro("select count(*) from producao_lote l where l.status='RASCUNHO'" + porLote, args(hospitalId)),
                inteiro("select count(*) from producao_item i join producao_lote l on l.id=i.lote_id where l.status='RASCUNHO' and i.pendente_revisao" + porLote, args(hospitalId)),
                inteiro("select count(*) " + DISPONIVEL + porLote, args(hospitalId)),
                inteiro("select count(*) from producao_fechamento f where f.status='AGUARDANDO'" + porFechamento, args(hospitalId)),
                (int) prazos.stream().filter(p -> p.diasUteis() < 0).count(),
                (int) prazos.stream().filter(p -> p.diasUteis() == 0).count(),
                inteiro("select count(*) from producao_item i join producao_lote l on l.id=i.lote_id where i.contestacao='EM_ANALISE'" + porLote, args(hospitalId)),
                inteiro("select count(*) from producao_fechamento f where f.status='APROVADO'" + porFechamento, args(hospitalId)));

        Mes numeros = new Mes(
                valor("select sum(f.total) from producao_fechamento f where f.data_fim between ? and ?" + porFechamento, args(hospitalId, mes.atDay(1), mes.atEndOfMonth())),
                valor("select sum(f.total) from producao_fechamento f where f.data_fim between ? and ?" + porFechamento, args(hospitalId, anterior.atDay(1), anterior.atEndOfMonth())),
                valor("select sum(f.total) from producao_fechamento f where f.status='NF_EMITIDA' and f.nf_data between ? and ?" + porFechamento, args(hospitalId, mes.atDay(1), mes.atEndOfMonth())),
                inteiro("select count(*) from producao_item i join producao_lote l on l.id=i.lote_id where l.data_producao between ? and ?" + porLote, args(hospitalId, mes.atDay(1), mes.atEndOfMonth())),
                inteiro("select count(*) from producao_item i join producao_lote l on l.id=i.lote_id where l.data_producao between ? and ?" + porLote, args(hospitalId, anterior.atDay(1), anterior.atEndOfMonth())),
                valor("select sum(f.total) from producao_fechamento f where f.status in ('AGUARDANDO','APROVADO')" + porFechamento, args(hospitalId)));

        List<Hospital> hospitais = jdbc.query("""
                select h.id, h.nome,
                  coalesce((select sum(total) from producao_fechamento f where f.hospital_id=h.id and f.status='AGUARDANDO'),0) aguardando,
                  coalesce((select sum(total) from producao_fechamento f where f.hospital_id=h.id and f.status='APROVADO'),0) aprovado,
                  coalesce((select sum(total) from producao_fechamento f where f.hospital_id=h.id and f.status='NF_EMITIDA' and f.nf_data between ? and ?),0) nf,
                  (select count(*) """ + DISPONIVEL + """
                   and l.hospital_id=h.id) para_fechar
                from hospital_cadastro h
                where exists (select 1 from producao_lote l where l.hospital_id=h.id)""" + (hospitalId == null ? "" : " and h.id=?") + " order by h.nome",
                (r, n) -> new Hospital(r.getLong(1), r.getString(2), r.getBigDecimal(3), r.getBigDecimal(4), r.getBigDecimal(5), r.getInt(6)),
                args(hospitalId, mes.atDay(1), mes.atEndOfMonth()).toArray());

        LocalDate inicioMapa = hoje.minusWeeks(25).with(java.time.DayOfWeek.MONDAY);
        List<Dia> mapa = jdbc.query("""
                select l.data_producao, count(i.id) from producao_lote l join producao_item i on i.lote_id=l.id
                where l.data_producao between ? and ?""" + porLote + " group by 1 order by 1",
                (r, n) -> new Dia(r.getObject(1, LocalDate.class), r.getInt(2)), args(hospitalId, inicioMapa, hoje).toArray());

        return new Painel(tarefas, numeros, hospitais, prazos, mapa, prazo.dias());
    }
}
