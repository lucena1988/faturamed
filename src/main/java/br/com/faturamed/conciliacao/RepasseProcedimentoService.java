package br.com.faturamed.conciliacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repasse cadastrado por procedimento. A planilha do hospital continua sendo a fonte principal:
 * o cadastro so entra quando a linha nao traz "Vl. a Repassar" nem "Regra".
 */
@Service
public class RepasseProcedimentoService {
    public record Repasse(Long id, Long hospitalId, String hospital, String codigoProcedimento, String tipoProcedimento,
            BigDecimal percentual, BigDecimal valorFixo, LocalDate vigenciaInicio, LocalDate vigenciaFim,
            boolean ativo, String observacao) {}
    public record Dados(Long hospitalId, String codigoProcedimento, String tipoProcedimento, BigDecimal percentual,
            BigDecimal valorFixo, LocalDate vigenciaInicio, LocalDate vigenciaFim, Boolean ativo, String observacao) {}

    private final JdbcTemplate jdbc;
    public RepasseProcedimentoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Repasse> listar() {
        return jdbc.query("""
                select r.*, h.nome as hospital from repasse_procedimento r left join hospital_cadastro h on h.id=r.hospital_id
                order by r.ativo desc, h.nome nulls first, r.codigo_procedimento nulls last, r.tipo_procedimento, r.id
                """, (rs, i) -> new Repasse(rs.getLong("id"), rs.getObject("hospital_id", Long.class), rs.getString("hospital"),
                rs.getString("codigo_procedimento"), rs.getString("tipo_procedimento"), rs.getBigDecimal("percentual"),
                rs.getBigDecimal("valor_fixo"), rs.getObject("vigencia_inicio", LocalDate.class), rs.getObject("vigencia_fim", LocalDate.class),
                rs.getBoolean("ativo"), rs.getString("observacao")));
    }

    public record ProcedimentoConhecido(String codigo, String descricao, String tipo) {}

    /** Procedimentos ja vistos nas planilhas dos hospitais, para escolher um exame/procedimento especifico. */
    @Transactional(readOnly = true)
    public List<ProcedimentoConhecido> procedimentos(Long hospitalId) {
        return jdbc.query("""
                select campos->>'cod produto' as codigo, max(campos->>'produto') as descricao, max(tipo_procedimento) as tipo
                from visita_base_hospitalar
                where coalesce(campos->>'cod produto','')<>'' and (?::bigint is null or hospital_id=?::bigint)
                group by campos->>'cod produto' order by max(campos->>'produto')
                """, (rs, i) -> new ProcedimentoConhecido(LeitorPlanilhaVisitas.identificador(rs.getString("codigo")),
                rs.getString("descricao"), rs.getString("tipo")), hospitalId, hospitalId);
    }

    @Transactional
    public Repasse salvar(Long id, Dados dados) {
        String codigo = LeitorPlanilhaVisitas.identificador(Objects.toString(dados.codigoProcedimento(), ""));
        var tipo = TipoProcedimento.de(dados.tipoProcedimento());
        if (codigo.isBlank() && tipo == null) throw new IllegalArgumentException("Informe o codigo ou o tipo do procedimento");
        if ((dados.percentual() == null) == (dados.valorFixo() == null)) throw new IllegalArgumentException("Informe o percentual ou o valor fixo do repasse");
        if (dados.percentual() != null && (dados.percentual().signum() < 0 || dados.percentual().compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Percentual de repasse deve ficar entre 0 e 100");
        if (dados.valorFixo() != null && dados.valorFixo().signum() < 0) throw new IllegalArgumentException("Valor fixo de repasse invalido");
        LocalDate inicio = dados.vigenciaInicio() == null ? LocalDate.of(2000, 1, 1) : dados.vigenciaInicio();
        if (dados.vigenciaFim() != null && dados.vigenciaFim().isBefore(inicio)) throw new IllegalArgumentException("Fim da vigencia antes do inicio");
        Object[] valores = { dados.hospitalId(), codigo.isBlank() ? null : codigo, tipo == null ? null : tipo.name(), dados.percentual(), dados.valorFixo(),
                Date.valueOf(inicio), dados.vigenciaFim() == null ? null : Date.valueOf(dados.vigenciaFim()),
                dados.ativo() == null || dados.ativo(), Objects.toString(dados.observacao(), "").trim() };
        long salvo;
        if (id == null) {
            salvo = jdbc.queryForObject("""
                    insert into repasse_procedimento(hospital_id,codigo_procedimento,tipo_procedimento,percentual,valor_fixo,vigencia_inicio,vigencia_fim,ativo,observacao)
                    values(?,?,?,?,?,?,?,?,?) returning id""", Long.class, valores);
        } else {
            var lista = new ArrayList<>(Arrays.asList(valores)); lista.add(id);
            if (jdbc.update("""
                    update repasse_procedimento set hospital_id=?,codigo_procedimento=?,tipo_procedimento=?,percentual=?,valor_fixo=?,
                    vigencia_inicio=?,vigencia_fim=?,ativo=?,observacao=? where id=?""", lista.toArray()) == 0)
                throw new NoSuchElementException("Repasse nao encontrado");
            salvo = id;
        }
        return listar().stream().filter(r -> r.id() == salvo).findFirst().orElseThrow();
    }

    /**
     * Devolve as linhas do hospital com o repasse cadastrado preenchido onde a planilha nao trouxe
     * "Vl. a Repassar" nem "Regra". As demais linhas voltam como vieram.
     */
    @Transactional(readOnly = true)
    public List<LeitorPlanilhaVisitas.Linha> complementar(Long hospitalId, List<LeitorPlanilhaVisitas.Linha> linhas) {
        if (linhas.stream().noneMatch(RepasseProcedimentoService::semRepasse)) return linhas;
        var regras = listar().stream().filter(Repasse::ativo).toList();
        if (regras.isEmpty()) return linhas;
        List<LeitorPlanilhaVisitas.Linha> resultado = new ArrayList<>(linhas.size());
        for (var linha : linhas) resultado.add(semRepasse(linha) ? aplicar(hospitalId, linha, regras) : linha);
        return resultado;
    }

    static boolean semRepasse(LeitorPlanilhaVisitas.Linha linha) {
        return linha.campo("Vl. A Repassar").isBlank() && linha.campo("Regra").isBlank();
    }

    static LeitorPlanilhaVisitas.Linha aplicar(Long hospitalId, LeitorPlanilhaVisitas.Linha linha, List<Repasse> regras) {
        BigDecimal valorTotal;
        try { valorTotal = linha.campo("Valor Tot").isBlank() ? null : new BigDecimal(linha.campo("Valor Tot")); }
        catch (NumberFormatException e) { return linha; }
        String codigo = LeitorPlanilhaVisitas.identificador(linha.campo("Cod Produto"));
        String informado = linha.campo(TipoProcedimento.CAMPO).isBlank() ? linha.campo("Tipo Procedimento") : linha.campo(TipoProcedimento.CAMPO);
        String tipo = TipoProcedimento.classificar(informado, codigo, linha.campo("Produto")).name();
        LocalDate data;
        try { data = linha.campo("Data Consumo").isBlank() ? null : LeitorPlanilhaVisitas.data(linha.campo("Data Consumo"), linha.numero()); }
        catch (IllegalArgumentException e) { data = null; }
        var regra = escolher(hospitalId, codigo, tipo, data, regras);
        if (regra.isEmpty()) return linha;
        BigDecimal repasse;
        if (regra.get().valorFixo() != null) repasse = regra.get().valorFixo();
        else if (valorTotal == null) return linha;
        else repasse = valorTotal.multiply(regra.get().percentual()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        Map<String, String> campos = new LinkedHashMap<>(linha.campos());
        campos.put("vl. a repassar", repasse.toPlainString());
        campos.put(ConciliadorVisitas.CAMPO_ORIGEM_REPASSE, ConciliadorVisitas.ORIGEM_CADASTRO);
        return new LeitorPlanilhaVisitas.Linha(linha.numero(), campos);
    }

    /** Ordem: hospital+codigo, codigo, hospital+tipo, tipo; respeitando a vigencia na data do consumo. */
    static Optional<Repasse> escolher(Long hospitalId, String codigo, String tipo, LocalDate data, List<Repasse> regras) {
        var vigentes = regras.stream().filter(r -> data == null || (!data.isBefore(r.vigenciaInicio())
                && (r.vigenciaFim() == null || !data.isAfter(r.vigenciaFim())))).toList();
        for (int nivel = 0; nivel < 4; nivel++) {
            final int n = nivel;
            var achado = vigentes.stream().filter(r -> switch (n) {
                case 0 -> hospitalId != null && hospitalId.equals(r.hospitalId()) && !codigo.isBlank() && codigo.equals(r.codigoProcedimento());
                case 1 -> r.hospitalId() == null && !codigo.isBlank() && codigo.equals(r.codigoProcedimento());
                case 2 -> hospitalId != null && hospitalId.equals(r.hospitalId()) && r.codigoProcedimento() == null && tipo.equals(r.tipoProcedimento());
                default -> r.hospitalId() == null && r.codigoProcedimento() == null && tipo.equals(r.tipoProcedimento());
            }).max(Comparator.comparing(Repasse::vigenciaInicio).thenComparing(Repasse::id));
            if (achado.isPresent()) return achado;
        }
        return Optional.empty();
    }
}
