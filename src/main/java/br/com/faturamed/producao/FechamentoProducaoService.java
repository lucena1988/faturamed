package br.com.faturamed.producao;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FechamentoProducaoService {
    static final Set<String> TIPOS = Set.of("POR_EXAME", "POR_AGENDA", "TABELA_AGENDA", "MENSAL_MEDICO");

    public record ExamePeso(@NotNull Long exameId, @Positive int peso) {}
    public record Faixa(@PositiveOrZero int quantidade, @NotNull @PositiveOrZero BigDecimal valor) {}
    public record RegraDados(@NotNull Long hospitalId, @NotBlank @Size(max = 120) String nome, @NotBlank String tipo,
            @NotNull @PositiveOrZero BigDecimal valor, @PositiveOrZero int minimoPorAgenda, @PositiveOrZero BigDecimal incremento,
            Long medicoId, @NotNull LocalDate vigenciaInicio, LocalDate vigenciaFim, @Size(max = 300) String observacao, boolean ativo,
            @Valid List<ExamePeso> exames, @Valid List<Faixa> faixas) {}
    public record Regra(long id, long hospitalId, String hospital, String nome, String tipo, BigDecimal valor, int minimoPorAgenda,
            BigDecimal incremento, Long medicoId, String medico, LocalDate vigenciaInicio, LocalDate vigenciaFim, String observacao,
            boolean ativo, List<ExamePeso> exames, List<Faixa> faixas) {}

    /** Linha de calculo: uma regra aplicada a uma agenda (folha x medico) ou um fixo mensal. */
    public record Calculo(Long loteId, LocalDate data, String periodo, Long medicoId, String medico, Long regraId, String descricao,
            int exames, int pontos, BigDecimal valor) {}
    public record SemRegra(long itemId, long loteId, LocalDate data, String medico, String exame, String nrAtendimento, String paciente) {}
    public record ResumoMedico(String medico, int agendas, int exames, BigDecimal total) {}
    public record Previa(long hospitalId, LocalDate inicio, LocalDate fim, int lotes, int etiquetas, List<Calculo> calculos,
            List<ResumoMedico> resumo, List<SemRegra> semRegra, BigDecimal total) {}
    public record Fechamento(long id, long hospitalId, String hospital, LocalDate dataInicio, LocalDate dataFim, String status,
            BigDecimal total, String aprovadoPor, LocalDate aprovadoEm, String nfNumero, LocalDate nfData, String observacao, int etiquetas) {}
    public record Etiqueta(LocalDate data, String periodo, String medico, String exame, String nrAtendimento, String paciente,
            String convenio, String plano, String carteirinha, String guia) {}
    public record Detalhe(Fechamento fechamento, List<Calculo> calculos, List<ResumoMedico> resumo, List<Etiqueta> etiquetas) {}

    private final JdbcTemplate jdbc;
    private final HistoricoProducao historico;
    public FechamentoProducaoService(JdbcTemplate jdbc, HistoricoProducao historico) { this.jdbc = jdbc; this.historico = historico; }

    // ================= regras =================
    @Transactional(readOnly = true)
    public List<Regra> regras(Long hospitalId) {
        List<Regra> base = jdbc.query("""
                select r.*, h.nome hospital, m.nome medico from producao_regra_valor r join hospital_cadastro h on h.id=r.hospital_id
                left join medico_cadastro m on m.id=r.medico_id where (?::bigint is null or r.hospital_id=?)
                order by h.nome, r.nome, r.vigencia_inicio desc""", (r, n) -> new Regra(r.getLong("id"), r.getLong("hospital_id"),
                r.getString("hospital"), r.getString("nome"), r.getString("tipo"), r.getBigDecimal("valor"), r.getInt("minimo_por_agenda"),
                r.getBigDecimal("incremento"), (Long) r.getObject("medico_id", Long.class), r.getString("medico"),
                r.getObject("vigencia_inicio", LocalDate.class), r.getObject("vigencia_fim", LocalDate.class), r.getString("observacao"),
                r.getBoolean("ativo"), new ArrayList<>(), new ArrayList<>()), hospitalId, hospitalId);
        Map<Long, Regra> porId = base.stream().collect(Collectors.toMap(Regra::id, r -> r));
        jdbc.query("select * from producao_regra_exame order by regra_id, exame_id", rs -> {
            Regra r = porId.get(rs.getLong("regra_id"));
            if (r != null) r.exames().add(new ExamePeso(rs.getLong("exame_id"), rs.getInt("peso")));
        });
        jdbc.query("select * from producao_regra_faixa order by regra_id, quantidade", rs -> {
            Regra r = porId.get(rs.getLong("regra_id"));
            if (r != null) r.faixas().add(new Faixa(rs.getInt("quantidade"), rs.getBigDecimal("valor")));
        });
        return base;
    }

    @Transactional
    public Regra salvarRegra(Long id, RegraDados d) {
        String tipo = d.tipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw new IllegalArgumentException("Tipo de regra invalido");
        List<ExamePeso> exames = d.exames() == null ? List.of() : d.exames();
        List<Faixa> faixas = d.faixas() == null ? List.of() : d.faixas();
        if ("MENSAL_MEDICO".equals(tipo) && d.medicoId() == null) throw new IllegalArgumentException("Informe o medico do valor mensal");
        if (!"MENSAL_MEDICO".equals(tipo) && exames.isEmpty()) throw new IllegalArgumentException("Selecione ao menos um exame para a regra");
        if ("TABELA_AGENDA".equals(tipo) && faixas.isEmpty()) throw new IllegalArgumentException("Informe a tabela de faixas (quantidade e valor)");
        if (faixas.stream().map(Faixa::quantidade).distinct().count() != faixas.size()) throw new IllegalArgumentException("Quantidade repetida na tabela de faixas");
        if (d.vigenciaFim() != null && d.vigenciaFim().isBefore(d.vigenciaInicio())) throw new IllegalArgumentException("Fim da vigencia antes do inicio");
        BigDecimal incremento = d.incremento() == null ? BigDecimal.ZERO : d.incremento();
        Long medico = "MENSAL_MEDICO".equals(tipo) ? d.medicoId() : null;
        String obs = d.observacao() == null ? "" : d.observacao().trim();
        if (id == null) {
            id = jdbc.queryForObject("""
                    insert into producao_regra_valor(hospital_id,nome,tipo,valor,minimo_por_agenda,incremento,medico_id,vigencia_inicio,vigencia_fim,observacao,ativo)
                    values(?,?,?,?,?,?,?,?,?,?,?) returning id""", Long.class, d.hospitalId(), d.nome().trim(), tipo, d.valor(), d.minimoPorAgenda(),
                    incremento, medico, d.vigenciaInicio(), d.vigenciaFim(), obs, d.ativo());
        } else {
            if (jdbc.update("""
                    update producao_regra_valor set hospital_id=?,nome=?,tipo=?,valor=?,minimo_por_agenda=?,incremento=?,medico_id=?,vigencia_inicio=?,
                    vigencia_fim=?,observacao=?,ativo=? where id=?""", d.hospitalId(), d.nome().trim(), tipo, d.valor(), d.minimoPorAgenda(), incremento,
                    medico, d.vigenciaInicio(), d.vigenciaFim(), obs, d.ativo(), id) == 0)
                throw new RecursoNaoEncontradoException("Regra nao encontrada");
            jdbc.update("delete from producao_regra_exame where regra_id=?", id);
            jdbc.update("delete from producao_regra_faixa where regra_id=?", id);
        }
        Set<Long> vistos = new HashSet<>();
        for (ExamePeso e : exames)
            if (vistos.add(e.exameId())) jdbc.update("insert into producao_regra_exame(regra_id,exame_id,peso) values(?,?,?)", id, e.exameId(), Math.max(1, e.peso()));
        for (Faixa f : faixas) jdbc.update("insert into producao_regra_faixa(regra_id,quantidade,valor) values(?,?,?)", id, f.quantidade(), f.valor());
        long rid = id;
        return regras(d.hospitalId()).stream().filter(r -> r.id() == rid).findFirst().orElseThrow();
    }

    @Transactional
    public void excluirRegra(long id) {
        Integer usada = jdbc.queryForObject("select count(*) from producao_fechamento_calculo where regra_id=?", Integer.class, id);
        if (usada != null && usada > 0) throw new ConflitoRevisaoException("Regra ja usada em fechamento. Encerre a vigencia ou desative-a em vez de excluir");
        jdbc.update("delete from producao_regra_valor where id=?", id);
    }

    // ================= previa / fechamento =================
    private List<MotorValorProducao.Regra> regrasMotor(long hospitalId) {
        return regras(hospitalId).stream().filter(Regra::ativo).map(r -> new MotorValorProducao.Regra(r.id(), r.nome(), r.tipo(), r.valor(),
                r.minimoPorAgenda(), r.incremento(), r.medicoId(), r.vigenciaInicio(), r.vigenciaFim(),
                r.exames().stream().collect(Collectors.toMap(ExamePeso::exameId, ExamePeso::peso)),
                r.faixas().stream().collect(Collectors.toMap(Faixa::quantidade, Faixa::valor, (a, b) -> a, TreeMap::new)))).toList();
    }

    private record ItemLinha(long itemId, long loteId, LocalDate data, String periodo, Long medicoId, String medico, Long exameId, String exame,
            String nr, String paciente) {}

    @Transactional(readOnly = true)
    public Previa previa(long hospitalId, LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) throw new IllegalArgumentException("Data final antes da inicial");
        List<ItemLinha> itens = jdbc.query("""
                select i.id, l.id lote_id, l.data_producao, l.periodo, i.medico_id, m.nome medico, i.exame_id, e.nome exame, i.nr_atendimento, i.paciente
                from producao_item i join producao_lote l on l.id=i.lote_id left join medico_cadastro m on m.id=i.medico_id
                left join producao_exame_tipo e on e.id=i.exame_id
                where l.hospital_id=? and l.data_producao between ? and ? and l.status='CONFERIDO' and l.fechamento_id is null
                order by l.data_producao, l.id, i.ordem""", (r, n) -> new ItemLinha(r.getLong("id"), r.getLong("lote_id"),
                r.getObject("data_producao", LocalDate.class), r.getString("periodo"), (Long) r.getObject("medico_id", Long.class),
                Objects.toString(r.getString("medico"), ""), (Long) r.getObject("exame_id", Long.class), Objects.toString(r.getString("exame"), ""),
                r.getString("nr_atendimento"), r.getString("paciente")), hospitalId, inicio, fim);
        Map<List<Object>, List<ItemLinha>> agendas = new LinkedHashMap<>();
        for (ItemLinha i : itens) agendas.computeIfAbsent(Arrays.asList(i.loteId(), i.medicoId()), k -> new ArrayList<>()).add(i);
        List<MotorValorProducao.Agenda> lista = agendas.values().stream().filter(v -> v.get(0).medicoId() != null)
                .map(v -> new MotorValorProducao.Agenda(v.get(0).loteId(), v.get(0).medicoId(), v.get(0).data(), v.get(0).periodo(),
                        v.stream().map(i -> new MotorValorProducao.ItemAgenda(i.itemId(), i.exameId())).toList())).toList();
        var resultado = MotorValorProducao.calcular(regrasMotor(hospitalId), lista, inicio, fim);
        Map<Long, String> nomes = medicos();
        List<Calculo> calculos = resultado.calculos().stream().map(c -> new Calculo(c.loteId(), c.data(), c.periodo(), c.medicoId(),
                nomes.getOrDefault(c.medicoId(), ""), c.regraId(), c.descricao(), c.exames(), c.pontos(), c.valor())).toList();
        Set<Long> sem = new HashSet<>(resultado.itensSemRegra());
        itens.stream().filter(i -> i.medicoId() == null).forEach(i -> sem.add(i.itemId()));
        List<SemRegra> semRegra = itens.stream().filter(i -> sem.contains(i.itemId()))
                .map(i -> new SemRegra(i.itemId(), i.loteId(), i.data(), i.medico(), i.exame(), i.nr(), i.paciente())).toList();
        int lotes = (int) itens.stream().map(ItemLinha::loteId).distinct().count();
        return new Previa(hospitalId, inicio, fim, lotes, itens.size(), calculos, resumir(calculos), semRegra, total(calculos));
    }

    private Map<Long, String> medicos() {
        Map<Long, String> m = new HashMap<>();
        jdbc.query("select id, nome from medico_cadastro", rs -> { m.put(rs.getLong(1), rs.getString(2)); });
        return m;
    }

    @Transactional
    public Detalhe fechar(long hospitalId, LocalDate inicio, LocalDate fim, String observacao, String usuario) {
        Previa p = previa(hospitalId, inicio, fim);
        if (p.etiquetas() == 0) throw new IllegalArgumentException("Nenhum lote conferido neste hospital e periodo");
        if (!p.semRegra().isEmpty())
            throw new IllegalArgumentException(p.semRegra().size() + " etiqueta(s) sem regra de valor ou sem medico. Ajuste antes de fechar");
        long id = jdbc.queryForObject("""
                insert into producao_fechamento(hospital_id,data_inicio,data_fim,total,observacao,criado_por) values(?,?,?,?,?,?) returning id""",
                Long.class, hospitalId, inicio, fim, p.total(), observacao == null ? "" : observacao.trim(), usuario);
        for (Calculo c : p.calculos())
            jdbc.update("""
                    insert into producao_fechamento_calculo(fechamento_id,lote_id,data_producao,periodo,medico_id,regra_id,descricao,exames,pontos,valor)
                    values(?,?,?,?,?,?,?,?,?,?)""", id, c.loteId(), c.data(), c.periodo(), c.medicoId(), c.regraId(), c.descricao(), c.exames(), c.pontos(), c.valor());
        jdbc.update("""
                insert into producao_fechamento_item(fechamento_id,item_id) select ?, i.id from producao_item i join producao_lote l on l.id=i.lote_id
                where l.hospital_id=? and l.data_producao between ? and ? and l.status='CONFERIDO' and l.fechamento_id is null""", id, hospitalId, inicio, fim);
        List<Long> lotes = jdbc.queryForList("""
                select id from producao_lote where hospital_id=? and data_producao between ? and ? and status='CONFERIDO' and fechamento_id is null""",
                Long.class, hospitalId, inicio, fim);
        jdbc.update("""
                update producao_lote set fechamento_id=?, status='ENVIADO', atualizado_em=now(), versao=versao+1
                where hospital_id=? and data_producao between ? and ? and status='CONFERIDO' and fechamento_id is null""", id, hospitalId, inicio, fim);
        historico.fechamento(id, "GERADO", "Fechamento gerado com " + lotes.size() + " lote(s) e " + p.etiquetas() + " etiqueta(s), total R$ " + p.total(), usuario);
        for (Long lote : lotes) historico.lote(lote, "ENVIADA", "Incluído no fechamento nº " + id, usuario);
        return detalhe(id);
    }

    private static final String SELECT_FECHAMENTO = """
            select f.*, h.nome hospital, (select count(*) from producao_fechamento_item x where x.fechamento_id=f.id) etiquetas
            from producao_fechamento f join hospital_cadastro h on h.id=f.hospital_id
            """;

    private Fechamento fechamento(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Fechamento(r.getLong("id"), r.getLong("hospital_id"), r.getString("hospital"), r.getObject("data_inicio", LocalDate.class),
                r.getObject("data_fim", LocalDate.class), r.getString("status"), r.getBigDecimal("total"), r.getString("aprovado_por"),
                r.getObject("aprovado_em", LocalDate.class), r.getString("nf_numero"), r.getObject("nf_data", LocalDate.class),
                r.getString("observacao"), r.getInt("etiquetas"));
    }

    @Transactional(readOnly = true)
    public List<Fechamento> fechamentos(Long hospitalId) {
        return jdbc.query(SELECT_FECHAMENTO + " where (?::bigint is null or f.hospital_id=?) order by f.data_fim desc, f.id desc",
                this::fechamento, hospitalId, hospitalId);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhe(long id) {
        List<Fechamento> f = jdbc.query(SELECT_FECHAMENTO + " where f.id=?", this::fechamento, id);
        if (f.isEmpty()) throw new RecursoNaoEncontradoException("Fechamento nao encontrado");
        List<Calculo> calculos = jdbc.query("""
                select c.*, m.nome medico from producao_fechamento_calculo c left join medico_cadastro m on m.id=c.medico_id
                where c.fechamento_id=? order by m.nome, c.data_producao nulls last, c.id""", (r, n) -> new Calculo((Long) r.getObject("lote_id", Long.class),
                r.getObject("data_producao", LocalDate.class), r.getString("periodo"), (Long) r.getObject("medico_id", Long.class),
                Objects.toString(r.getString("medico"), ""), (Long) r.getObject("regra_id", Long.class), r.getString("descricao"), r.getInt("exames"),
                r.getInt("pontos"), r.getBigDecimal("valor")), id);
        List<Etiqueta> etiquetas = jdbc.query("""
                select l.data_producao, l.periodo, m.nome medico, e.nome exame, i.nr_atendimento, i.paciente, i.convenio, i.plano, i.carteirinha, i.guia
                from producao_fechamento_item fi join producao_item i on i.id=fi.item_id join producao_lote l on l.id=i.lote_id
                left join medico_cadastro m on m.id=i.medico_id left join producao_exame_tipo e on e.id=i.exame_id
                where fi.fechamento_id=? order by m.nome, l.data_producao, i.nr_atendimento""", (r, n) -> new Etiqueta(
                r.getObject("data_producao", LocalDate.class), r.getString("periodo"), Objects.toString(r.getString("medico"), ""),
                Objects.toString(r.getString("exame"), ""), r.getString("nr_atendimento"), r.getString("paciente"), r.getString("convenio"),
                r.getString("plano"), r.getString("carteirinha"), r.getString("guia")), id);
        return new Detalhe(f.get(0), calculos, resumir(calculos), etiquetas);
    }

    /** AGUARDANDO (enviado ao hospital) -> APROVADO (hospital deu ok) -> NF_EMITIDA. */
    @Transactional
    public Detalhe aprovar(long id, String aprovadoPor, LocalDate em, String usuario) {
        if (aprovadoPor == null || aprovadoPor.isBlank()) throw new IllegalArgumentException("Informe quem aprovou no hospital");
        exigirStatus(id, "AGUARDANDO");
        jdbc.update("update producao_fechamento set status='APROVADO', aprovado_por=?, aprovado_em=? where id=?",
                aprovadoPor.trim(), em == null ? LocalDate.now() : em, id);
        historico.fechamento(id, "APROVADO", "Ok do hospital registrado: aprovado por " + aprovadoPor.trim(), usuario);
        return detalhe(id);
    }

    @Transactional
    public Detalhe registrarNf(long id, String numero, LocalDate data, String usuario) {
        if (numero == null || numero.isBlank()) throw new IllegalArgumentException("Informe o numero da NF");
        exigirStatus(id, "APROVADO");
        jdbc.update("update producao_fechamento set status='NF_EMITIDA', nf_numero=?, nf_data=? where id=?", numero.trim(),
                data == null ? LocalDate.now() : data, id);
        historico.fechamento(id, "NF_EMITIDA", "NF " + numero.trim() + " registrada", usuario);
        return detalhe(id);
    }

    /** Desfaz um fechamento ainda nao aprovado: as folhas voltam a ficar conferidas e disponiveis. */
    @Transactional
    public void cancelar(long id, String usuario) {
        exigirStatus(id, "AGUARDANDO");
        for (Long lote : jdbc.queryForList("select id from producao_lote where fechamento_id=?", Long.class, id))
            historico.lote(lote, "RETIRADA", "Fechamento nº " + id + " cancelado: lote voltou para conferido", usuario);
        historico.fechamento(id, "CANCELADO", "Fechamento cancelado", usuario);
        jdbc.update("update producao_lote set fechamento_id=null, status='CONFERIDO', atualizado_em=now(), versao=versao+1 where fechamento_id=?", id);
        jdbc.update("delete from producao_fechamento where id=?", id);
    }

    private void exigirStatus(long id, String esperado) {
        List<String> s = jdbc.queryForList("select status from producao_fechamento where id=?", String.class, id);
        if (s.isEmpty()) throw new RecursoNaoEncontradoException("Fechamento nao encontrado");
        if (!esperado.equals(s.get(0))) throw new ConflitoRevisaoException("Operacao nao permitida: fechamento esta " + s.get(0));
    }

    static List<ResumoMedico> resumir(List<Calculo> calculos) {
        Map<String, List<Calculo>> g = new TreeMap<>();
        for (Calculo c : calculos) g.computeIfAbsent(c.medico(), k -> new ArrayList<>()).add(c);
        List<ResumoMedico> r = new ArrayList<>();
        g.forEach((medico, cs) -> r.add(new ResumoMedico(medico, (int) cs.stream().filter(c -> c.loteId() != null).map(Calculo::loteId).distinct().count(),
                cs.stream().mapToInt(Calculo::exames).sum(), total(cs))));
        return r;
    }

    static BigDecimal total(List<Calculo> calculos) {
        return calculos.stream().map(Calculo::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
