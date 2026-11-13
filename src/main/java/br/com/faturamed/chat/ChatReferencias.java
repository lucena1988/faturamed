package br.com.faturamed.chat;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cartoes de referencia no chat: "#lote 130", "#chamado 12", "#relatorio 6" e "#div 6/10002"
 * (ou os links das telas correspondentes) viram um resumo com status e link.
 */
@Service
public class ChatReferencias {
    public record Referencia(String chave, String tipo, String titulo, String detalhe, String status, String cor, String link) {}

    /** chave = "tipo:valor", ex.: lote:130, chamado:12, relatorio:6, div:6/10002 */
    private static final Pattern CHAVE = Pattern.compile("(lote|chamado|relatorio|div):([0-9]{1,12}(?:/[0-9A-Za-z.\\-]{1,40})?)");
    private final JdbcTemplate jdbc;
    public ChatReferencias(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Referencia> resolver(List<String> chaves) {
        List<Referencia> lista = new ArrayList<>();
        if (chaves == null) return lista;
        for (String bruta : new LinkedHashSet<>(chaves.subList(0, Math.min(chaves.size(), 20)))) {
            var m = CHAVE.matcher(Objects.toString(bruta, "").trim());
            if (!m.matches()) continue;
            try {
                Referencia r = switch (m.group(1)) {
                    case "lote" -> lote(Long.parseLong(m.group(2)));
                    case "chamado" -> chamado(Long.parseLong(m.group(2)));
                    case "relatorio" -> relatorio(Long.parseLong(m.group(2)));
                    default -> divergencia(m.group(2));
                };
                if (r != null) lista.add(r);
            } catch (NumberFormatException ignorado) { }
        }
        return lista;
    }

    private Referencia lote(long id) {
        var l = jdbc.queryForList("""
                select l.id, l.status, l.data_producao, l.periodo, h.nome as hospital,
                  (select count(*) from producao_item i where i.lote_id = l.id) as itens
                from producao_lote l join hospital_cadastro h on h.id = l.hospital_id where l.id = ?""", id);
        if (l.isEmpty()) return null;
        var r = l.getFirst();
        String status = String.valueOf(r.get("status"));
        return new Referencia("lote:" + id, "Lote de produção", "Lote #" + id + " · " + r.get("hospital"),
                data(r.get("data_producao")) + " · " + periodo(r.get("periodo")) + " · " + r.get("itens") + " item(ns)",
                switch (status) { case "ENVIADO" -> "Enviado"; case "CONFERIDO" -> "Conferido"; default -> "Rascunho"; },
                switch (status) { case "ENVIADO" -> "verde"; case "CONFERIDO" -> "azul"; default -> "ambar"; },
                "producao.html?folha=" + id);
    }

    private Referencia chamado(long id) {
        var l = jdbc.queryForList("select id, protocolo, titulo, status, responsavel_nome from chamado_suporte where id = ?", id);
        if (l.isEmpty()) return null;
        var r = l.getFirst();
        String status = String.valueOf(r.get("status"));
        return new Referencia("chamado:" + id, "Chamado", r.get("protocolo") + " · " + r.get("titulo"),
                r.get("responsavel_nome") == null ? "Sem responsável" : "Com " + r.get("responsavel_nome"),
                status.replace('_', ' ').toLowerCase(Locale.ROOT).replaceFirst("^.", status.substring(0, 1)),
                switch (status) { case "RESOLVIDO", "FECHADO" -> "verde"; case "CANCELADO" -> "cinza"; case "ABERTO" -> "rosa"; default -> "ambar"; },
                "chamados.html?chamado=" + id);
    }

    private Referencia relatorio(long id) {
        var l = jdbc.queryForList("""
                select id, relatorio->>'hospital' as hospital, relatorio->>'arquivoMedico' as arquivo,
                  coalesce((relatorio->'resumo'->>'DIVERGENTE')::int, 0) as divergentes,
                  coalesce((relatorio->'resumo'->>'PAGA')::int, 0) as pagas,
                  jsonb_array_length(coalesce(relatorio->'visitas', '[]'::jsonb)) as total
                from conciliacao_visitas where id = ?""", id);
        if (l.isEmpty()) return null;
        var r = l.getFirst();
        int div = ((Number) r.get("divergentes")).intValue();
        return new Referencia("relatorio:" + id, "Conciliação", "Conciliação #" + id + " · " + r.get("hospital"),
                r.get("total") + " procedimento(s) · " + r.get("pagas") + " pago(s)" + (r.get("arquivo") == null ? "" : " · " + r.get("arquivo")),
                div > 0 ? div + " divergente(s)" : "Sem divergências", div > 0 ? "rosa" : "verde",
                "conciliacao.html?relatorio=" + id);
    }

    private Referencia divergencia(String valor) {
        String[] p = valor.split("/", 2);
        if (p.length < 2) return relatorio(Long.parseLong(p[0]));
        long id = Long.parseLong(p[0]);
        var l = jdbc.queryForList("""
                select c.relatorio->>'hospital' as hospital, v->>'status' as status, v->>'motivo' as motivo, v->>'data' as data
                from conciliacao_visitas c, jsonb_array_elements(c.relatorio->'visitas') v
                where c.id = ? and v->>'atendimento' = ? limit 1""", id, p[1]);
        if (l.isEmpty()) return null;
        var r = l.getFirst();
        String status = String.valueOf(r.get("status"));
        return new Referencia("div:" + valor, "Atendimento", "Atendimento " + p[1] + " · " + r.get("hospital"),
                Objects.toString(r.get("motivo"), ""),
                switch (status) { case "PAGA" -> "Pago"; case "PENDENTE" -> "Pendente"; default -> "Divergente"; },
                switch (status) { case "PAGA" -> "verde"; case "PENDENTE" -> "ambar"; default -> "rosa"; },
                "conciliacao.html?relatorio=" + id + "&atendimento=" + p[1]);
    }

    private static String data(Object d) {
        String s = Objects.toString(d, "");
        return s.length() >= 10 ? s.substring(8, 10) + "/" + s.substring(5, 7) + "/" + s.substring(0, 4) : s;
    }
    private static String periodo(Object p) {
        return switch (Objects.toString(p, "")) { case "MANHA" -> "Manhã"; case "TARDE" -> "Tarde"; case "NOITE" -> "Noite"; default -> "Integral"; };
    }
}
