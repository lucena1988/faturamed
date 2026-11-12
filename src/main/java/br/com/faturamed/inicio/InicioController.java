package br.com.faturamed.inicio;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import br.com.faturamed.conciliacao.RevisaoVisitasService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Situacao do procedimento no Fechamento Faturado (precisa de v = visita e b = vinculo). */
    private static final String SITUACAO_FF = """
            case when v.decisao = 'EM_ANALISE'
                   or exists (select 1 from visita_base_hospitalar r where r.anterior_id = v.id and r.decisao = 'EM_ANALISE') then 'divergente'
                 when b.visita_id is not null and coalesce(
                   (select depois->>'status' from revisao_visita where conciliacao_id = b.relatorio_id and linha_medico = b.linha_producao order by id desc limit 1),
                   (select item->>'status' from conciliacao_visitas c, jsonb_array_elements(c.relatorio->'visitas') item
                     where c.id = b.relatorio_id and (item->>'linhaMedico')::integer = b.linha_producao)) = 'DIVERGENTE' then 'divergente'
                 when b.visita_id is not null then 'conciliado'
                 else 'aguardando' end""";

    /**
     * Fechamento Faturado: procedimentos da base hospitalar por intervalo e situacao
     * (conciliado, aguardando producao, divergente), com filtro de hospital e de exame/procedimento.
     * exame: vazio (todos), "tipo:EXAME" (todos de um tipo) ou "cod:40101010" (procedimento especifico).
     * periodo "tudo" agrupa por mes desde o primeiro procedimento. Em dias usa a data do procedimento;
     * em horas e minutos, o momento da importacao do arquivo.
     */
    @GetMapping("/api/inicio/fechamento-faturado")
    public Map<String, Object> fechamentoFaturado(@RequestParam(required = false) String periodo,
            @RequestParam(required = false) Long hospitalId, @RequestParam(required = false) String exame) {
        var p = Periodo.ler(periodo, new Periodo(15, 'd'));
        var filtro = new StringBuilder();
        List<Object> args = new java.util.ArrayList<>();
        if (hospitalId != null) { filtro.append(" and v.hospital_id = ?"); args.add(hospitalId); }
        String ex = exame == null ? "" : exame.trim();
        if (ex.startsWith("tipo:")) { filtro.append(" and v.tipo_procedimento = ?"); args.add(ex.substring(5).toUpperCase()); }
        else if (ex.startsWith("cod:")) {
            filtro.append(" and regexp_replace(trim(coalesce(v.campos->>'cod produto','')), '^([0-9]+)\\.0+$', '\\1') = ?");
            args.add(ex.substring(4).trim());
        }
        String validos = "from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id = v.arquivo_id"
                + " left join vinculo_base_hospitalar b on b.visita_id = v.id"
                + " where v.decisao in ('ORIGINAL','VISITA_DISTINTA','EM_ANALISE') " + filtro;
        // Datas do primeiro e do ultimo procedimento com estes filtros (ajuda quando o periodo nao tem dados).
        var limites = jdbc.queryForMap("select min(v.data_visita) as primeira, max(v.data_visita) as ultima " + validos, args.toArray());
        var r = new LinkedHashMap<String, Object>();
        r.put("primeiraData", limites.get("primeira"));
        r.put("ultimaData", limites.get("ultima"));
        String colunas = """
                count(base.momento) as total,
                  count(*) filter (where situacao = 'conciliado') as conciliado,
                  count(*) filter (where situacao = 'aguardando') as aguardando,
                  count(*) filter (where situacao = 'divergente') as divergente""";
        var tipos = new LinkedHashMap<String, Long>();
        if ("tudo".equalsIgnoreCase(Objects.toString(periodo, "").trim())) {
            r.put("agrupamento", "mes");
            if (limites.get("primeira") == null) { r.put("serie", List.of()); r.put("porTipo", tipos); return r; }
            List<Object> argsMes = new java.util.ArrayList<>(args); argsMes.add(limites.get("primeira")); argsMes.add(limites.get("ultima"));
            r.put("serie", jdbc.queryForList("""
                    with base as (select date_trunc('month', v.data_visita)::date as momento, %s as situacao %s)
                    select to_char(d, 'MM/YYYY') as dia, %s
                    from generate_series(date_trunc('month', ?::date), date_trunc('month', greatest(current_date, ?::date)), interval '1 month') d
                    left join base on base.momento = d::date group by d order by d""".formatted(SITUACAO_FF, validos, colunas), argsMes.toArray()));
            jdbc.query("select v.tipo_procedimento, count(*) " + validos + " group by 1 order by 2 desc", rs -> { tipos.put(rs.getString(1), rs.getLong(2)); }, args.toArray());
            r.put("porTipo", tipos);
            r.put("composicao", composicao(validos, args));
            return r;
        }
        boolean dias = p.unidade() == 'd';
        String momento = dias ? "v.data_visita" : "date_trunc('%s', a.criado_em)".formatted(p.passo());
        String series = dias
                ? "generate_series(current_date - (?::int - 1), current_date, interval '1 day')"
                : "generate_series(date_trunc('%1$s', now()) - (?::int - 1) * interval '1 %1$s', date_trunc('%1$s', now()), interval '1 %1$s')".formatted(p.passo());
        List<Object> argsSerie = new java.util.ArrayList<>(args); argsSerie.add(p.quantidade());
        r.put("serie", jdbc.queryForList("""
                with base as (select %s as momento, %s as situacao %s)
                select %s as dia, %s
                from %s d left join base on base.momento = d%s
                group by d order by d""".formatted(momento, SITUACAO_FF, validos, dias ? "to_char(d, 'DD/MM')" : "to_char(d, 'HH24:MI')",
                colunas, series, dias ? "::date" : ""), argsSerie.toArray()));
        List<Object> argsTipo = new java.util.ArrayList<>(args); argsTipo.add(dias ? (Object) p.quantidade() : (Object) p.minutos());
        jdbc.query("select v.tipo_procedimento, count(*) " + validos + " and "
                + (dias ? "v.data_visita >= current_date - (?::int - 1)" : "a.criado_em >= now() - ? * interval '1 minute'") + " group by 1 order by 2 desc",
                rs -> { tipos.put(rs.getString(1), rs.getLong(2)); }, argsTipo.toArray());
        r.put("porTipo", tipos);
        r.put("composicao", composicao(validos + " and " + (dias ? "v.data_visita >= current_date - (?::int - 1)" : "a.criado_em >= now() - ? * interval '1 minute'"), argsTipo));
        return r;
    }

    /**
     * Composicao por tipo: quantidade, conciliados, valor do hospital, repasse informado na planilha
     * (Vl. a Repassar ou Valor Tot x Regra) e quantos nao trazem repasse (usam o repasse cadastrado).
     */
    private List<Map<String, Object>> composicao(String validos, List<Object> args) {
        String num = "nullif(regexp_replace(trim(coalesce(v.campos->>'%s','')), '[^0-9.\\-]', '', 'g'), '')::numeric";
        String valor = num.formatted("valor tot"), repasse = num.formatted("vl. a repassar"), regra = num.formatted("regra");
        return jdbc.queryForList("""
                select v.tipo_procedimento as tipo, count(*) as total,
                  count(*) filter (where (%1$s) = 'conciliado') as conciliado,
                  coalesce(sum(%2$s), 0) as valor,
                  coalesce(sum(coalesce(%3$s, %2$s * case when %4$s > 1 then %4$s / 100 else %4$s end)), 0) as repasse_planilha,
                  count(*) filter (where %3$s is null and %4$s is null) as sem_repasse_planilha
                %5$s group by 1 order by 2 desc""".formatted(SITUACAO_FF, valor, repasse, regra, validos), args.toArray());
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
