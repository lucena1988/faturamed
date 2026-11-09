package br.com.faturamed.producao;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Responde perguntas simples em portugues na busca (Ctrl+K), sem IA externa:
 *  - "quanto o Morumbi faturou em outubro?"      -> totais de fechamento do hospital no mes
 *  - "lotes pendentes do Dr Pompeu"              -> quantidade + atalho para a lista filtrada
 *  - "regras do Itaim"                           -> atalho para as regras de valor do hospital
 */
@Component
public class AssistenteBusca {
    public record Resposta(String titulo, String detalhe, String url) {}

    private static final List<String> MESES = List.of("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro");
    private static final Set<String> IGNORAR = Set.of("hospital", "hospitais", "sao", "luiz", "santa", "santo", "medico", "medica",
            "doutor", "doutora", "dr", "dra", "sem", "atendimento", "teste", "rede");

    private static final int MAX_PERGUNTA = 200;
    private static final Pattern LOTE = Pattern.compile("\\b(lote|lotes|folha|folhas)\\b");
    private static final Pattern PENDENTE = Pattern.compile("\\b(pendente|pendentes|rascunho|rascunhos|aberto|abertos)\\b");
    private static final Pattern CONFERIDO = Pattern.compile("\\bconferid");
    private static final Pattern ENVIADO = Pattern.compile("\\b(enviad|fechad)");
    private static final Pattern VALOR = Pattern.compile("\\b(quanto|quantos|valor|total|faturou|faturamento|fechou|fechamento|fechamentos|deve|nf)\\b");
    private static final List<Pattern> PADROES_MES = MESES.stream().map(m -> Pattern.compile("\\b" + m + "\\b")).toList();

    private final JdbcTemplate jdbc;
    public AssistenteBusca(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static boolean tem(Pattern p, String q) { return p.matcher(q).find(); }

    public Optional<Resposta> responder(String pergunta) {
        String q = normalizar(pergunta == null ? "" : pergunta.length() > MAX_PERGUNTA ? pergunta.substring(0, MAX_PERGUNTA) : pergunta);
        if (q.split(" ").length < 2) return Optional.empty();
        Optional<Map.Entry<Long, String>> hospital = melhor(q, "select id, nome from hospital_cadastro where ativo");
        Optional<Map.Entry<Long, String>> medico = melhor(q, "select id, nome from medico_cadastro where ativo");

        if (q.contains("regra")) {
            return Optional.of(new Resposta("Regras de valor" + hospital.map(h -> " — " + h.getValue()).orElse(""),
                    "Abrir as regras cadastradas" + hospital.map(h -> " deste hospital").orElse(""),
                    "fechamento-producao.html?aba=regras" + hospital.map(h -> "&hospitalId=" + h.getKey()).orElse("")));
        }
        if (tem(LOTE, q)) {
            String status = tem(PENDENTE, q) ? "RASCUNHO" : tem(CONFERIDO, q) ? "CONFERIDO" : tem(ENVIADO, q) ? "ENVIADO" : "";
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder("select count(*) lotes, coalesce(sum((select count(*) from producao_item i where i.lote_id=l.id)),0) etiquetas from producao_lote l where 1=1");
            if (!status.isEmpty()) { sql.append(" and l.status=?"); args.add(status); }
            hospital.ifPresent(h -> { sql.append(" and l.hospital_id=?"); args.add(h.getKey()); });
            medico.ifPresent(m -> { sql.append(" and exists(select 1 from producao_lote_medico lm where lm.lote_id=l.id and lm.medico_id=?)"); args.add(m.getKey()); });
            Map<String, Object> r = jdbc.queryForMap(sql.toString(), args.toArray());
            String rotulo = switch (status) { case "RASCUNHO" -> " em rascunho"; case "CONFERIDO" -> " conferido(s)"; case "ENVIADO" -> " enviado(s)"; default -> ""; };
            String filtro = hospital.map(h -> " · " + h.getValue()).orElse("") + medico.map(m -> " · " + m.getValue()).orElse("");
            String url = "producao.html?" + String.join("&", List.of(
                    status.isEmpty() ? "" : "status=" + status,
                    hospital.map(h -> "hospitalId=" + h.getKey()).orElse(""),
                    medico.map(m -> "medicoId=" + m.getKey()).orElse("")).stream().filter(s -> !s.isEmpty()).toList());
            return Optional.of(new Resposta(r.get("lotes") + " lote(s)" + rotulo, r.get("etiquetas") + " etiqueta(s)" + filtro, url));
        }
        if (tem(VALOR, q)) {
            YearMonth mes = mes(q);
            List<Object> args = new ArrayList<>(List.of(mes.atDay(1), mes.atEndOfMonth()));
            String filtroHospital = "";
            if (hospital.isPresent()) { filtroHospital = " and hospital_id=?"; args.add(hospital.get().getKey()); }
            Map<String, Object> r = jdbc.queryForMap("""
                    select count(*) qtd, coalesce(sum(total),0) total,
                           coalesce(sum(total) filter (where status='AGUARDANDO'),0) aguardando,
                           coalesce(sum(total) filter (where status='APROVADO'),0) aprovado,
                           coalesce(sum(total) filter (where status='NF_EMITIDA'),0) nf
                    from producao_fechamento where data_fim between ? and ?""" + filtroHospital, args.toArray());
            String nomeMes = MESES.get(mes.getMonthValue() - 1).replace("marco", "março") + "/" + mes.getYear();
            return Optional.of(new Resposta(
                    brl((BigDecimal) r.get("total")) + " em fechamentos" + hospital.map(h -> " — " + h.getValue()).orElse("") + " (" + nomeMes + ")",
                    r.get("qtd") + " fechamento(s): aguardando hospital " + brl((BigDecimal) r.get("aguardando")) + " · aprovado "
                            + brl((BigDecimal) r.get("aprovado")) + " · NF emitida " + brl((BigDecimal) r.get("nf")),
                    "fechamento-producao.html" + hospital.map(h -> "?hospitalId=" + h.getKey()).orElse("")));
        }
        return Optional.empty();
    }

    /** Escolhe o cadastro cujo nome tem mais palavras "fortes" presentes na pergunta. */
    private Optional<Map.Entry<Long, String>> melhor(String q, String sql) {
        Set<String> palavras = new HashSet<>(Arrays.asList(q.split(" ")));
        Map.Entry<Long, String> melhor = null;
        int melhorPontos = 0;
        for (Map<String, Object> linha : jdbc.queryForList(sql)) {
            String nome = (String) linha.get("nome");
            int pontos = 0;
            for (String p : normalizar(nome).split(" ")) if (p.length() >= 4 && !IGNORAR.contains(p) && palavras.contains(p)) pontos++;
            if (pontos > melhorPontos) { melhorPontos = pontos; melhor = Map.entry(((Number) linha.get("id")).longValue(), nome); }
        }
        return Optional.ofNullable(melhor);
    }

    static YearMonth mes(String q) {
        YearMonth atual = YearMonth.now();
        if (q.contains("mes passado") || q.contains("ultimo mes")) return atual.minusMonths(1);
        Matcher ano = Pattern.compile("\\b(20\\d{2})\\b").matcher(q);
        for (int i = 0; i < MESES.size(); i++)
            if (tem(PADROES_MES.get(i), q)) {
                YearMonth m = YearMonth.of(ano.find() ? Integer.parseInt(ano.group(1)) : atual.getYear(), i + 1);
                return m.isAfter(atual) && !ano.reset().find() ? m.minusYears(1) : m;
            }
        return atual;
    }

    static String normalizar(String v) {
        return Normalizer.normalize(v == null ? "" : v, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String brl(BigDecimal v) {
        return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v == null ? BigDecimal.ZERO : v);
    }

}
