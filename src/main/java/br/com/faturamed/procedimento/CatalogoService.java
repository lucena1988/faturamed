package br.com.faturamed.procedimento;

import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoService {
    private final JdbcTemplate jdbc;

    public CatalogoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> importar(CatalogoController.ImportarCatalogo request) {
        var codigos = new HashSet<String>();
        for (var procedimento : request.procedimentos()) {
            if (!codigos.add(procedimento.codigo())) {
                throw new IllegalArgumentException("Codigo repetido: " + procedimento.codigo());
            }
            if (procedimento.inicioVigencia() != null && procedimento.fimVigencia() != null
                    && procedimento.fimVigencia().isBefore(procedimento.inicioVigencia())) {
                throw new IllegalArgumentException("Vigencia invalida: " + procedimento.codigo());
            }
        }
        Long id = jdbc.query("""
                insert into catalogo_procedimento (tabela, versao, fonte, consultado_em)
                values (?, ?, ?, ?) on conflict (tabela, versao) do nothing returning id
                """, rs -> rs.next() ? rs.getLong(1) : null,
                request.tabela().name(), request.versao().trim(), request.fonte().trim(), request.consultadoEm());
        if (id == null) {
            throw new IllegalArgumentException("Tabela e versao ja cadastradas; use uma nova versao");
        }
        jdbc.batchUpdate("""
                insert into procedimento_referencia
                (catalogo_id, codigo, descricao, inicio_vigencia, fim_vigencia) values (?, ?, ?, ?, ?)
                """, request.procedimentos(), 500, (ps, procedimento) -> {
            ps.setLong(1, id);
            ps.setString(2, procedimento.codigo());
            ps.setString(3, procedimento.descricao().trim());
            ps.setObject(4, procedimento.inicioVigencia());
            ps.setObject(5, procedimento.fimVigencia());
        });
        return Map.of("id", id, "tabela", request.tabela(), "versao", request.versao().trim(),
                "procedimentos", request.procedimentos().size());
    }

    /** Importa a tabela oficial (planilha ou CSV da ANS) como nova versao do catalogo. */
    @Transactional
    public Map<String, Object> importarPlanilha(CatalogoController.Tabela tabela, String versao, String fonte, String nome, byte[] conteudo) {
        if (versao == null || versao.isBlank()) throw new IllegalArgumentException("Informe a versao da tabela");
        var procedimentos = LeitorTabelaTuss.ler(nome, conteudo);
        return importar(new CatalogoController.ImportarCatalogo(tabela, versao.trim(),
                fonte == null || fonte.isBlank() ? "Arquivo " + nome : fonte.trim(), LocalDate.now(), procedimentos));
    }

    /**
     * Busca na versao mais recente da tabela: por codigo (inicio) ou por palavras da descricao.
     * Com "codigos", devolve exatamente esses codigos (para mostrar o nome de codigos ja cadastrados).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buscarTexto(CatalogoController.Tabela tabela, String texto, List<String> codigos) {
        Long catalogo = jdbc.query("select id from catalogo_procedimento where tabela=? order by consultado_em desc, id desc limit 1",
                rs -> rs.next() ? rs.getLong(1) : null, tabela.name());
        if (catalogo == null) return List.of();
        if (codigos != null && !codigos.isEmpty()) {
            return jdbc.queryForList("select codigo, descricao from procedimento_referencia where catalogo_id=? and codigo = any(?)",
                    catalogo, codigos.stream().map(c -> c.replaceAll("\\D", "")).toArray(String[]::new));
        }
        String q = texto == null ? "" : texto.trim();
        if (q.length() < 2) return List.of();
        String digitos = q.replaceAll("[.\\-\\s]", "");
        if (digitos.matches("\\d+")) {
            return jdbc.queryForList("""
                    select codigo, descricao from procedimento_referencia where catalogo_id=? and codigo like ?
                    order by (fim_vigencia is null) desc, codigo limit 30""", catalogo, digitos + "%");
        }
        var palavras = Arrays.stream(LeitorTabelaTuss.normalizar(q).split(" ")).filter(p -> !p.isBlank()).limit(6).toList();
        var sql = new StringBuilder("select codigo, descricao from procedimento_referencia where catalogo_id=?");
        List<Object> args = new ArrayList<>(List.of(catalogo));
        for (String p : palavras) {
            sql.append(" and translate(lower(descricao),'áàâãäéèêëíìîïóòôõöúùûüç','aaaaaeeeeiiiiooooouuuuc') like ?");
            args.add("%" + p.replace("%", "").replace("_", "") + "%");
        }
        sql.append(" order by (fim_vigencia is null) desc, length(descricao), codigo limit 30");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("""
                select c.id, c.tabela, c.versao, c.fonte, c.consultado_em, count(p.id) as procedimentos
                from catalogo_procedimento c left join procedimento_referencia p on p.catalogo_id = c.id
                group by c.id order by c.id desc
                """);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buscar(Long id, String codigo, LocalDate data) {
        if (jdbc.queryForObject("select count(*) from catalogo_procedimento where id = ?", Long.class, id) == 0) {
            throw new RecursoNaoEncontradoException("Catalogo nao encontrado");
        }
        if (data == null) {
            return jdbc.queryForList("select * from procedimento_referencia where catalogo_id = ? and codigo = ?",
                    id, codigo);
        }
        return jdbc.queryForList("""
                select * from procedimento_referencia where catalogo_id = ? and codigo = ?
                and (inicio_vigencia is null or inicio_vigencia <= ?)
                and (fim_vigencia is null or fim_vigencia >= ?)
                """, id, codigo, data, data);
    }
}
