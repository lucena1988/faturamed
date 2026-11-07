package br.com.faturamed.producao;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProducaoService {
    public static final Set<String> PERIODOS = Set.of("MANHA", "TARDE", "NOITE", "INTEGRAL");
    public static final Set<String> STATUS = Set.of("RASCUNHO", "CONFERIDO", "ENVIADO");

    public record Resumo(long id, long hospitalId, String hospital, String medicos, String exames, LocalDate dataProducao,
            String periodo, String status, int itens, int pendentes, boolean temFoto) {}
    public record Lote(long id, long hospitalId, String hospital, List<Long> medicoIds, List<Long> exameIds, LocalDate dataProducao,
            String periodo, String tipoAgenda, String auxiliar, String observacao, String status, boolean temFoto,
            long versao, List<Item> itens) {}
    public record Item(Long id, @NotBlank @Size(max = 30) String nrAtendimento, @Size(max = 30) String dataHoraAtendimento,
            @Size(max = 180) String paciente, @Size(max = 30) String prontuario, @Size(max = 12) String dataNascimento,
            @Size(max = 180) String filiacao, @Size(max = 120) String convenio, @Size(max = 20) String convenioCodigo,
            @Size(max = 120) String plano, @Size(max = 40) String carteirinha, @Size(max = 40) String guia,
            @Size(max = 40) String senha, @Size(max = 30) String nrPrescricao, @Size(max = 180) String procedimento,
            @Size(max = 180) String medicoSolicitante, String origem, boolean pendenteRevisao, Long medicoId, Long exameId) {}
    public record Cabecalho(@NotNull Long hospitalId, @NotNull @Size(min = 1, message = "Selecione ao menos um medico") List<Long> medicoIds,
            List<Long> exameIds, @NotNull LocalDate dataProducao, @NotBlank String periodo, @Size(max = 120) String tipoAgenda,
            @Size(max = 120) String auxiliar, @Size(max = 500) String observacao, @NotNull @PositiveOrZero Long versao) {}
    public record Salvar(@Valid @NotNull Cabecalho cabecalho, @Valid @NotNull List<Item> itens) {}
    public record Duplicidade(String nrAtendimento, long loteId, LocalDate dataProducao, String hospital) {}
    public record Exame(long id, String nome, boolean ativo) {}

    private final JdbcTemplate jdbc;
    public ProducaoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT_LOTE = """
            select l.*, h.nome hospital, (l.foto is not null) tem_foto,
                   (select string_agg(m.nome, ', ' order by m.nome) from producao_lote_medico lm join medico_cadastro m on m.id=lm.medico_id where lm.lote_id=l.id) medicos,
                   (select string_agg(e.nome, ', ' order by e.nome) from producao_lote_exame le join producao_exame_tipo e on e.id=le.exame_id where le.lote_id=l.id) exames,
                   (select count(*) from producao_item i where i.lote_id=l.id) itens,
                   (select count(*) from producao_item i where i.lote_id=l.id and i.pendente_revisao) pendentes
            from producao_lote l join hospital_cadastro h on h.id=l.hospital_id
            """;

    @Transactional(readOnly = true)
    public List<Resumo> listar(LocalDate de, LocalDate ate, Long hospitalId, Long medicoId, String status) {
        StringBuilder sql = new StringBuilder(SELECT_LOTE).append(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (de != null) { sql.append(" and l.data_producao>=?"); args.add(de); }
        if (ate != null) { sql.append(" and l.data_producao<=?"); args.add(ate); }
        if (hospitalId != null) { sql.append(" and l.hospital_id=?"); args.add(hospitalId); }
        if (medicoId != null) { sql.append(" and exists(select 1 from producao_lote_medico lm where lm.lote_id=l.id and lm.medico_id=?)"); args.add(medicoId); }
        if (status != null && !status.isBlank()) { sql.append(" and l.status=?"); args.add(status); }
        sql.append(" order by l.data_producao desc, l.id desc limit 500");
        return jdbc.query(sql.toString(), (r, n) -> new Resumo(r.getLong("id"), r.getLong("hospital_id"), r.getString("hospital"),
                Objects.toString(r.getString("medicos"), ""), Objects.toString(r.getString("exames"), ""),
                r.getObject("data_producao", LocalDate.class), r.getString("periodo"), r.getString("status"),
                r.getInt("itens"), r.getInt("pendentes"), r.getBoolean("tem_foto")), args.toArray());
    }

    @Transactional(readOnly = true)
    public Lote buscar(long id) {
        List<Lote> lotes = jdbc.query(SELECT_LOTE + " where l.id=?", (r, n) -> new Lote(r.getLong("id"), r.getLong("hospital_id"),
                r.getString("hospital"), ids("select medico_id from producao_lote_medico where lote_id=? order by medico_id", id),
                ids("select exame_id from producao_lote_exame where lote_id=? order by exame_id", id),
                r.getObject("data_producao", LocalDate.class), r.getString("periodo"), r.getString("tipo_agenda"), r.getString("auxiliar"),
                r.getString("observacao"), r.getString("status"), r.getBoolean("tem_foto"), r.getLong("versao"), itens(id)), id);
        if (lotes.isEmpty()) throw new RecursoNaoEncontradoException("Lote de producao nao encontrado");
        return lotes.get(0);
    }

    private List<Long> ids(String sql, long id) { return jdbc.queryForList(sql, Long.class, id); }

    private List<Item> itens(long loteId) {
        return jdbc.query("select * from producao_item where lote_id=? order by ordem, id", (r, n) -> new Item(r.getLong("id"),
                r.getString("nr_atendimento"), r.getString("data_hora_atendimento"), r.getString("paciente"), r.getString("prontuario"),
                r.getString("data_nascimento"), r.getString("filiacao"), r.getString("convenio"), r.getString("convenio_codigo"),
                r.getString("plano"), r.getString("carteirinha"), r.getString("guia"), r.getString("senha"), r.getString("nr_prescricao"),
                r.getString("procedimento"), r.getString("medico_solicitante"), r.getString("origem"), r.getBoolean("pendente_revisao"),
                (Long) r.getObject("medico_id", Long.class), (Long) r.getObject("exame_id", Long.class)), loteId);
    }

    /** Cria (id null) ou atualiza o lote inteiro: cabecalho + lista completa de itens (cada etiqueta = um item). */
    @Transactional
    public Lote salvar(Long id, Salvar dados, String usuario) {
        Cabecalho c = dados.cabecalho();
        String periodo = c.periodo().trim().toUpperCase(Locale.ROOT);
        if (!PERIODOS.contains(periodo)) throw new IllegalArgumentException("Periodo invalido");
        Set<Long> medicos = new LinkedHashSet<>(c.medicoIds());
        Set<Long> exames = new LinkedHashSet<>(c.exameIds() == null ? List.of() : c.exameIds());
        if (medicos.isEmpty()) throw new IllegalArgumentException("Selecione ao menos um medico");
        for (Item i : dados.itens()) {
            if (i.medicoId() != null && !medicos.contains(i.medicoId()))
                throw new IllegalArgumentException("Etiqueta " + i.nrAtendimento() + " aponta para medico fora da folha");
            if (i.exameId() != null && !exames.contains(i.exameId()))
                throw new IllegalArgumentException("Etiqueta " + i.nrAtendimento() + " aponta para exame fora da folha");
        }
        if (id == null) {
            if (c.versao() != 0) throw new IllegalArgumentException("Versao inicial invalida");
            id = jdbc.queryForObject("""
                    insert into producao_lote(hospital_id,data_producao,periodo,tipo_agenda,auxiliar,observacao,criado_por)
                    values(?,?,?,?,?,?,?) returning id""", Long.class, c.hospitalId(), c.dataProducao(), periodo,
                    txt(c.tipoAgenda()), txt(c.auxiliar()), txt(c.observacao()), usuario);
        } else {
            Lote atual = buscar(id);
            if ("ENVIADO".equals(atual.status())) throw new ConflitoRevisaoException("Lote ja enviado ao faturamento nao pode ser alterado");
            if (jdbc.update("""
                    update producao_lote set hospital_id=?,data_producao=?,periodo=?,tipo_agenda=?,auxiliar=?,observacao=?,
                    atualizado_em=now(),versao=versao+1 where id=? and versao=?""", c.hospitalId(), c.dataProducao(), periodo,
                    txt(c.tipoAgenda()), txt(c.auxiliar()), txt(c.observacao()), id, c.versao()) == 0)
                throw new ConflitoRevisaoException("Lote alterado por outro operador. Recarregue antes de salvar");
            jdbc.update("delete from producao_item where lote_id=?", id);
            jdbc.update("delete from producao_lote_medico where lote_id=?", id);
            jdbc.update("delete from producao_lote_exame where lote_id=?", id);
        }
        for (Long m : medicos) jdbc.update("insert into producao_lote_medico(lote_id,medico_id) values(?,?)", id, m);
        for (Long e : exames) jdbc.update("insert into producao_lote_exame(lote_id,exame_id) values(?,?)", id, e);
        Long medicoUnico = medicos.size() == 1 ? medicos.iterator().next() : null;
        Long exameUnico = exames.size() == 1 ? exames.iterator().next() : null;
        int ordem = 0;
        for (Item i : dados.itens()) {
            String origem = i.origem() == null ? "MANUAL" : i.origem().toUpperCase(Locale.ROOT);
            if (!Set.of("MANUAL", "OCR", "LEITOR").contains(origem)) origem = "MANUAL";
            jdbc.update("""
                    insert into producao_item(lote_id,ordem,nr_atendimento,data_hora_atendimento,paciente,prontuario,data_nascimento,filiacao,
                    convenio,convenio_codigo,plano,carteirinha,guia,senha,nr_prescricao,procedimento,medico_solicitante,origem,pendente_revisao,
                    medico_id,exame_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", id, ordem++, i.nrAtendimento().replaceAll("\\s", ""),
                    txt(i.dataHoraAtendimento()), txt(i.paciente()), txt(i.prontuario()), txt(i.dataNascimento()), txt(i.filiacao()),
                    txt(i.convenio()), txt(i.convenioCodigo()), txt(i.plano()), txt(i.carteirinha()), txt(i.guia()), txt(i.senha()),
                    txt(i.nrPrescricao()), txt(i.procedimento()), txt(i.medicoSolicitante()), origem, i.pendenteRevisao(),
                    i.medicoId() != null ? i.medicoId() : medicoUnico, i.exameId() != null ? i.exameId() : exameUnico);
        }
        return buscar(id);
    }

    @Transactional
    public Lote alterarStatus(long id, String status) {
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!STATUS.contains(s)) throw new IllegalArgumentException("Status invalido");
        Lote lote = buscar(id);
        if (!"RASCUNHO".equals(s)) {
            if (lote.itens().isEmpty()) throw new IllegalArgumentException("Lote sem itens");
            List<String> problemas = new ArrayList<>();
            for (int n = 0; n < lote.itens().size(); n++) {
                Item i = lote.itens().get(n);
                List<String> p = new ArrayList<>();
                if (!i.nrAtendimento().matches("[0-9]{7,10}")) p.add("atendimento invalido");
                if (i.paciente().isBlank()) p.add("sem paciente");
                if (i.medicoId() == null) p.add("sem medico");
                if (!lote.exameIds().isEmpty() && i.exameId() == null) p.add("sem exame");
                if (i.pendenteRevisao()) p.add("nao conferida");
                if (!p.isEmpty()) problemas.add("linha " + (n + 1) + " (" + String.join(", ", p) + ")");
            }
            if (!problemas.isEmpty())
                throw new IllegalArgumentException("Corrija antes de conferir: " + String.join("; ", problemas.subList(0, Math.min(10, problemas.size())))
                        + (problemas.size() > 10 ? " e mais " + (problemas.size() - 10) + " etiqueta(s)" : ""));
        }
        jdbc.update("update producao_lote set status=?,atualizado_em=now(),versao=versao+1 where id=?", s, id);
        return buscar(id);
    }

    @Transactional
    public void excluir(long id) {
        Lote lote = buscar(id);
        if ("ENVIADO".equals(lote.status())) throw new ConflitoRevisaoException("Lote ja enviado ao faturamento nao pode ser excluido");
        jdbc.update("delete from producao_lote where id=?", id);
    }

    @Transactional
    public void salvarFoto(long id, byte[] foto, String tipo) {
        buscar(id);
        jdbc.update("update producao_lote set foto=?,foto_tipo=? where id=?", foto, tipo, id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> foto(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select foto,foto_tipo from producao_lote where id=? and foto is not null", id);
        if (r.isEmpty()) throw new RecursoNaoEncontradoException("Lote sem foto");
        return r.get(0);
    }

    @Transactional(readOnly = true)
    public List<Exame> exames() {
        return jdbc.query("select * from producao_exame_tipo order by nome", (r, n) -> new Exame(r.getLong("id"), r.getString("nome"), r.getBoolean("ativo")));
    }

    @Transactional
    public Exame criarExame(String nome) {
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty() || limpo.length() > 120) throw new IllegalArgumentException("Informe o nome do exame (ate 120 caracteres)");
        String normalizado = Normalizer.normalize(limpo, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        try {
            long id = jdbc.queryForObject("insert into producao_exame_tipo(nome,nome_normalizado) values(?,?) returning id", Long.class, limpo, normalizado);
            return new Exame(id, limpo, true);
        } catch (DuplicateKeyException e) {
            throw new ConflitoRevisaoException("Exame ja cadastrado");
        }
    }

    /** Atendimentos informados que ja constam em outros lotes. */
    @Transactional(readOnly = true)
    public List<Duplicidade> duplicidades(Collection<String> atendimentos, Long ignorarLoteId) {
        if (atendimentos == null || atendimentos.isEmpty()) return List.of();
        List<String> lista = atendimentos.stream().filter(Objects::nonNull).map(a -> a.replaceAll("\\s", "")).filter(a -> !a.isEmpty()).distinct().toList();
        if (lista.isEmpty()) return List.of();
        String marcadores = String.join(",", Collections.nCopies(lista.size(), "?"));
        List<Object> args = new ArrayList<>(lista);
        args.add(ignorarLoteId == null ? -1L : ignorarLoteId);
        return jdbc.query("""
                select distinct i.nr_atendimento, l.id, l.data_producao, h.nome hospital
                from producao_item i join producao_lote l on l.id=i.lote_id join hospital_cadastro h on h.id=l.hospital_id
                where i.nr_atendimento in (%s) and l.id<>? order by i.nr_atendimento, l.id""".formatted(marcadores),
                (r, n) -> new Duplicidade(r.getString(1), r.getLong(2), r.getObject(3, LocalDate.class), r.getString(4)),
                args.toArray());
    }

    private static String txt(String v) { return v == null ? "" : v.trim(); }
}
