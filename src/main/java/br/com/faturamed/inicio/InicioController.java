package br.com.faturamed.inicio;

import br.com.faturamed.conciliacao.ConciliadorVisitas;
import br.com.faturamed.conciliacao.RevisaoVisitasService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/api/inicio/resumo")
    public Map<String, Object> resumo(@RequestParam(required = false) Integer diasVisitas,
            @RequestParam(defaultValue = "15") int diasProducao) throws Exception {
        diasProducao = Math.max(1, Math.min(90, diasProducao));
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
        if (diasVisitas != null) ciclo(r, Math.max(1, Math.min(90, diasVisitas)));
        r.put("chamadosAbertos", contar("select count(*) from chamado_suporte where status in ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO')"));
        r.put("chamadosBloqueados", contar("select count(*) from chamado_suporte where bloqueado and status in ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO')"));
        r.put("lotesPendentes", contar("select count(*) from producao_lote where status <> 'ENVIADO'"));
        r.put("solicitacoesPendentes", contar("select count(*) from solicitacao_medico where resposta is null"));
        // dados dos graficos da tela de inicio
        r.put("visitasPagas", pagas);
        r.put("visitasPendentes", atencao - divergentes);
        r.put("lotesPorStatus", agrupar("select status, count(*) from producao_lote group by status"));
        r.put("chamadosPorStatus", agrupar("select status, count(*) from chamado_suporte group by status"));
        r.put("lotesPorDia", jdbc.queryForList("""
                select to_char(d, 'DD/MM') as dia, count(l.id) as total
                from generate_series(current_date - (? - 1), current_date, interval '1 day') d
                left join producao_lote l on l.data_producao = d::date
                group by d order by d""", diasProducao));
        return r;
    }


    /** Ciclo das visitas somando as versoes atuais dos relatorios gerados nos ultimos N dias. */
    private void ciclo(Map<String, Object> r, int dias) throws Exception {
        long pagas = 0, divergentes = 0, outras = 0;
        var ids = jdbc.queryForList("""
                select c.id from conciliacao_visitas c
                where c.criado_em >= current_date - (? - 1)
                  and not exists (select 1 from conciliacao_visitas n where n.anterior_id = c.id)""", Long.class, dias);
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
    private Map<String, Long> agrupar(String sql) {
        var m = new LinkedHashMap<String, Long>();
        jdbc.query(sql, rs -> { m.put(rs.getString(1), rs.getLong(2)); });
        return m;
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
