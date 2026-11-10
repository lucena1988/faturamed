package br.com.faturamed.inicio;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import br.com.faturamed.conciliacao.RevisaoVisitasService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resumo rapido de pendencias exibido na tela de inicio (inicio.html). */
@RestController
public class InicioController {
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    public InicioController(JdbcTemplate jdbc, RevisaoVisitasService revisoes) { this.jdbc = jdbc; this.revisoes = revisoes; }

    /**
     * Periodo informado na tela como numero + unidade: "30m" (minutos), "2h" (horas) ou "15d" (dias).
     * Cada unidade tem um limite para o grafico nao ficar com barras demais.
     */
    public record Periodo(int quantidade, char unidade) {
        private static final Pattern FORMATO = Pattern.compile("(\\d{1,4})([mhd])");
        public static Periodo ler(String texto, Periodo padrao) {
            if (texto == null || texto.isBlank()) return padrao;
            var m = FORMATO.matcher(texto.trim().toLowerCase());
            if (!m.matches()) return padrao;
            char u = m.group(2).charAt(0);
            int max = switch (u) { case 'm' -> 120; case 'h' -> 72; default -> 90; };
            return new Periodo(Math.max(1, Math.min(max, Integer.parseInt(m.group(1)))), u);
        }
        /** Duracao do periodo em minutos (usada em "now() - N minutos"). */
        public long minutos() { return switch (unidade) { case 'm' -> quantidade; case 'h' -> quantidade * 60L; default -> quantidade * 1440L; }; }
        String passo() { return switch (unidade) { case 'm' -> "minute"; case 'h' -> "hour"; default -> "day"; }; }
    }

    @GetMapping("/api/inicio/resumo")
    public Map<String, Object> resumo(@RequestParam(required = false) String producao,
            @RequestParam(required = false) String visitas,
            @RequestParam(required = false) String lotes,
            @RequestParam(required = false) String chamados) throws Exception {
        var periodoProducao = Periodo.ler(producao, new Periodo(15, 'd'));
        var r = new LinkedHashMap<String, Object>();
        // mesma regra do Dashboard: visitas nao pagas do relatorio mais recente (com revisoes aplicadas)
        List<Long> ultimo = jdbc.queryForList("select max(id) from conciliacao_visitas", Long.class);
        long atencao = 0, divergentes = 0, pagas = 0; String hospital = null;
        if (!ultimo.isEmpty() && ultimo.get(0) != null) {
            var relatorio = revisoes.carregar(ultimo.get(0)).atual();
            hospital = relatorio.hospital();
            for (var v : relatorio.visitas()) {
                if (v.status() != ConciliadorVisitas.Status.PAGA) atencao++; else pagas++;
                if (v.status() == ConciliadorVisitas.Status.DIVERGENTE) divergentes++;
            }
        }
        r.put("visitasAtencao", atencao);
        r.put("visitasDivergentes", divergentes);
        r.put("relatorioHospital", hospital);
        var periodoVisitas = Periodo.ler(visitas, null);
        if (periodoVisitas != null) ciclo(r, periodoVisitas);
        r.put("chamadosAbertos", contar("select count(*) from chamado_suporte where status in ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO')"));
        r.put("chamadosBloqueados", contar("select count(*) from chamado_suporte where bloqueado and status in ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO')"));
        r.put("lotesPendentes", contar("select count(*) from producao_lote where status <> 'ENVIADO'"));
        r.put("solicitacoesPendentes", contar("select count(*) from solicitacao_medico where resposta is null"));
        // dados dos graficos da tela de inicio
        r.put("visitasPagas", pagas);
        r.put("visitasPendentes", atencao - divergentes);
        r.put("lotesPorStatus", agruparNoPeriodo("producao_lote", Periodo.ler(lotes, null)));
        r.put("chamadosPorStatus", agruparNoPeriodo("chamado_suporte", Periodo.ler(chamados, null)));
        r.put("chamadosSla", slaChamados(Periodo.ler(chamados, null)));
        r.put("lotesPorDia", serieProducao(periodoProducao));
        return r;
    }

    /**
     * Lotes por intervalo. Em dias usa a data de producao do lote; em horas e minutos usa o momento
     * em que o lote foi registrado, pois a data de producao nao tem hora.
     */
    private List<Map<String, Object>> serieProducao(Periodo p) {
        if (p.unidade() == 'd') return jdbc.queryForList("""
                select to_char(d, 'DD/MM') as dia, count(l.id) as total,
                  count(l.id) filter (where l.status = 'RASCUNHO') as rascunho,
                  count(l.id) filter (where l.status = 'CONFERIDO') as conferido,
                  count(l.id) filter (where l.status = 'ENVIADO') as enviado
                from generate_series(current_date - (? - 1), current_date, interval '1 day') d
                left join producao_lote l on l.data_producao = d::date
                group by d order by d""", p.quantidade());
        String passo = p.passo();
        return jdbc.queryForList("""
                select to_char(d, 'HH24:MI') as dia, count(l.id) as total,
                  count(l.id) filter (where l.status = 'RASCUNHO') as rascunho,
                  count(l.id) filter (where l.status = 'CONFERIDO') as conferido,
                  count(l.id) filter (where l.status = 'ENVIADO') as enviado
                from generate_series(date_trunc('%1$s', now()) - (? - 1) * interval '1 %1$s', date_trunc('%1$s', now()), interval '1 %1$s') d
                left join producao_lote l on date_trunc('%1$s', l.criado_em) = d
                group by d order by d""".formatted(passo), p.quantidade());
    }

    /**
     * Chamados com prazo de SLA (cancelados ficam de fora) e quantos estao ou ficaram dentro do prazo:
     * resolvido/fechado ate o prazo, ou ainda aberto e com o prazo nao vencido.
     */
    private Map<String, Long> slaChamados(Periodo p) {
        String sql = """
                select count(*) filter (where prazo_sla_em is not null) as total,
                  count(*) filter (where prazo_sla_em is not null and coalesce(resolvido_em, fechado_em, now()) <= prazo_sla_em) as no_prazo
                from chamado_suporte where status <> 'CANCELADO'""";
        var m = new LinkedHashMap<String, Long>();
        org.springframework.jdbc.core.RowCallbackHandler ler = rs -> { m.put("total", rs.getLong(1)); m.put("noPrazo", rs.getLong(2)); };
        if (p == null) jdbc.query(sql, ler);
        else jdbc.query(sql + " and criado_em >= now() - ? * interval '1 minute'", ler, p.minutos());
        m.putIfAbsent("total", 0L); m.putIfAbsent("noPrazo", 0L);
        return m;
    }

    /** Contagem por situacao dos registros criados no periodo (sem periodo: todos). */
    private Map<String, Long> agruparNoPeriodo(String tabela, Periodo p) {
        var m = new LinkedHashMap<String, Long>();
        if (p == null) jdbc.query("select status, count(*) from " + tabela + " group by status", rs -> { m.put(rs.getString(1), rs.getLong(2)); });
        else jdbc.query("select status, count(*) from " + tabela + " where criado_em >= now() - ? * interval '1 minute' group by status",
                rs -> { m.put(rs.getString(1), rs.getLong(2)); }, p.minutos());
        return m;
    }

    /** Ciclo das visitas somando as versoes atuais dos relatorios gerados no periodo. */
    private void ciclo(Map<String, Object> r, Periodo p) throws Exception {
        long pagas = 0, divergentes = 0, outras = 0;
        var ids = jdbc.queryForList("""
                select c.id from conciliacao_visitas c
                where c.criado_em >= now() - ? * interval '1 minute'
                  and not exists (select 1 from conciliacao_visitas n where n.anterior_id = c.id)""", Long.class, p.minutos());
        for (long id : ids) for (var v : revisoes.carregar(id).atual().visitas()) {
            if (v.status() == ConciliadorVisitas.Status.PAGA) pagas++;
            else if (v.status() == ConciliadorVisitas.Status.DIVERGENTE) divergentes++;
            else outras++;
        }
        r.put("visitasPagasPeriodo", pagas);
        r.put("visitasDivergentesPeriodo", divergentes);
        r.put("visitasPendentesPeriodo", outras);
        r.put("relatoriosPeriodo", ids.size());
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
