package br.com.faturamed.producao;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Busca rapida (Ctrl+K): etiquetas/folhas de producao, fechamentos, medicos e hospitais. */
@RestController
@RequestMapping("/api/busca")
public class BuscaGlobalController {
    public record Resultado(String tipo, String titulo, String detalhe, String url) {}

    private final JdbcTemplate jdbc;
    public BuscaGlobalController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Resultado> buscar(@RequestParam String q) {
        String termo = q == null ? "" : q.trim();
        if (termo.length() < 2) return List.of();
        String like = "%" + termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String digitos = termo.replaceAll("\\D", "");
        List<Resultado> r = new ArrayList<>();
        r.addAll(jdbc.query("""
                select i.nr_atendimento, i.paciente, l.id lote, l.data_producao, h.nome hospital
                from producao_item i join producao_lote l on l.id=i.lote_id join hospital_cadastro h on h.id=l.hospital_id
                where ((? <> '' and i.nr_atendimento like ?) or i.paciente ilike ? escape '\\')
                order by l.data_producao desc limit 8""", (rs, n) -> new Resultado("Etiqueta",
                (rs.getString("paciente").isBlank() ? "Atendimento " + rs.getString("nr_atendimento") : rs.getString("paciente")),
                "Atend. " + rs.getString("nr_atendimento") + " · " + rs.getString("hospital") + " · "
                        + rs.getObject("data_producao", java.time.LocalDate.class).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "producao.html?folha=" + rs.getLong("lote")), digitos.length() >= 3 ? digitos : "", digitos + "%", like));
        if (!digitos.isEmpty() && digitos.length() <= 9)
            r.addAll(jdbc.query("""
                    select f.id, h.nome hospital, f.status, f.total from producao_fechamento f join hospital_cadastro h on h.id=f.hospital_id
                    where f.id=? or f.nf_numero=?""", (rs, n) -> new Resultado("Fechamento", "Fechamento nº " + rs.getLong("id"),
                    rs.getString("hospital") + " · R$ " + rs.getBigDecimal("total"), "fechamento-producao.html?fechamento=" + rs.getLong("id")),
                    Long.parseLong(digitos), digitos));
        r.addAll(jdbc.query("select nome, crm, uf from medico_cadastro where nome ilike ? escape '\\' or crm like ? order by nome limit 5",
                (rs, n) -> new Resultado("Médico", rs.getString("nome"), "CRM " + rs.getString("crm") + "/" + rs.getString("uf"), "index.html#medicos"),
                like, termo + "%"));
        r.addAll(jdbc.query("select nome, cidade, uf from hospital_cadastro where nome ilike ? escape '\\' order by nome limit 5",
                (rs, n) -> new Resultado("Hospital", rs.getString("nome"), (rs.getString("cidade") + " " + rs.getString("uf")).trim(), "index.html#hospitais"),
                like));
        return r;
    }
}
