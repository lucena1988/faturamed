package br.com.faturamed.producao;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal do Hospital: o hospital ve somente os proprios fechamentos, confere lote a lote,
 * contesta etiquetas (sem travar o fechamento) e libera para emissao da NF.
 */
@Service
public class HospitalPortalService {
    public record LoteConferencia(long loteId, LocalDate data, String periodo, String medicos, String exames, int etiquetas,
            int contestadas, LocalDateTime conferidoEm, String conferidoPor) {}
    public record Conferencia(FechamentoProducaoService.Detalhe detalhe, List<LoteConferencia> lotes, java.math.BigDecimal valorEnviado) {}

    private final JdbcTemplate jdbc;
    private final FechamentoProducaoService fechamentos;
    private final HistoricoProducao historico;
    public HospitalPortalService(JdbcTemplate jdbc, FechamentoProducaoService fechamentos, HistoricoProducao historico) {
        this.jdbc = jdbc; this.fechamentos = fechamentos; this.historico = historico;
    }

    public long hospitalDoUsuario(long usuarioId) {
        Long h = jdbc.query("select hospital_id from usuario_acesso where id=? and perfil='HOSPITAL'", rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, usuarioId);
        if (h == null) throw new org.springframework.security.access.AccessDeniedException("Usuario sem hospital vinculado");
        return h;
    }

    public String nomeHospital(long hospitalId) {
        return jdbc.queryForObject("select nome from hospital_cadastro where id=?", String.class, hospitalId);
    }

    @Transactional(readOnly = true)
    public List<FechamentoProducaoService.Fechamento> listar(long hospitalId) { return fechamentos.fechamentos(hospitalId); }

    private void exigirDoHospital(long fechamentoId, long hospitalId) {
        Long dono = jdbc.query("select hospital_id from producao_fechamento where id=?", rs -> rs.next() ? rs.getLong(1) : null, fechamentoId);
        if (dono == null || dono != hospitalId) throw new RecursoNaoEncontradoException("Fechamento nao encontrado");
    }

    private String status(long fechamentoId) {
        return jdbc.queryForObject("select status from producao_fechamento where id=?", String.class, fechamentoId);
    }

    private void exigirEmConferencia(long fechamentoId) {
        if (!"AGUARDANDO".equals(status(fechamentoId))) throw new ConflitoRevisaoException("Este fechamento ja foi aprovado e nao pode mais ser alterado");
    }

    @Transactional
    public Conferencia abrir(long fechamentoId, long hospitalId, String usuario) {
        exigirDoHospital(fechamentoId, hospitalId);
        if (jdbc.update("update producao_fechamento set visto_hospital_em=now() where id=? and visto_hospital_em is null", fechamentoId) > 0)
            historico.fechamento(fechamentoId, "VISTO", "Fechamento aberto pelo hospital para conferência", usuario);
        List<LoteConferencia> lotes = jdbc.query("""
                select l.id, l.data_producao, l.periodo, fl.conferido_em, fl.conferido_por,
                       (select string_agg(m.nome, ', ' order by m.nome) from producao_lote_medico lm join medico_cadastro m on m.id=lm.medico_id where lm.lote_id=l.id) medicos,
                       (select string_agg(e.nome, ', ' order by e.nome) from producao_lote_exame le join producao_exame_tipo e on e.id=le.exame_id where le.lote_id=l.id) exames,
                       (select count(*) from producao_fechamento_item fi join producao_item i on i.id=fi.item_id where fi.fechamento_id=fl.fechamento_id and i.lote_id=l.id) etiquetas,
                       (select count(*) from producao_fechamento_item fi join producao_item i on i.id=fi.item_id where fi.fechamento_id=fl.fechamento_id and i.lote_id=l.id
                          and (i.contestacao is not null and i.contestacao <> 'REINCLUIR' or fi.situacao='RETIRADA')) contestadas
                from producao_fechamento_lote fl join producao_lote l on l.id=fl.lote_id where fl.fechamento_id=? order by l.data_producao, l.id""",
                (r, n) -> new LoteConferencia(r.getLong("id"), r.getObject("data_producao", LocalDate.class), r.getString("periodo"),
                        Objects.toString(r.getString("medicos"), ""), Objects.toString(r.getString("exames"), ""), r.getInt("etiquetas"), r.getInt("contestadas"),
                        r.getTimestamp("conferido_em") == null ? null : r.getTimestamp("conferido_em").toLocalDateTime(), r.getString("conferido_por")), fechamentoId);
        java.math.BigDecimal enviado = jdbc.queryForObject("select coalesce(valor_enviado,total) from producao_fechamento where id=?", java.math.BigDecimal.class, fechamentoId);
        return new Conferencia(fechamentos.detalhe(fechamentoId), lotes, enviado);
    }

    @Transactional
    public void conferirLote(long fechamentoId, long loteId, boolean conferido, long hospitalId, String usuario) {
        exigirDoHospital(fechamentoId, hospitalId);
        exigirEmConferencia(fechamentoId);
        int n = jdbc.update("update producao_fechamento_lote set conferido_em=?, conferido_por=? where fechamento_id=? and lote_id=?",
                conferido ? java.sql.Timestamp.valueOf(LocalDateTime.now()) : null, conferido ? usuario : null, fechamentoId, loteId);
        if (n == 0) throw new RecursoNaoEncontradoException("Lote nao pertence a este fechamento");
        historico.lote(loteId, conferido ? "CONFERIDO_HOSPITAL" : "REABERTO_HOSPITAL",
                conferido ? "Lote conferido pelo hospital (fechamento nº " + fechamentoId + ")" : "Hospital desfez a conferência do lote", usuario);
    }

    /** Marca de uma vez todos os lotes ainda nao conferidos do fechamento. Retorna quantos foram marcados. */
    @Transactional
    public int conferirTodos(long fechamentoId, long hospitalId, String usuario) {
        exigirDoHospital(fechamentoId, hospitalId);
        exigirEmConferencia(fechamentoId);
        List<Long> lotes = jdbc.queryForList("select lote_id from producao_fechamento_lote where fechamento_id=? and conferido_em is null", Long.class, fechamentoId);
        jdbc.update("update producao_fechamento_lote set conferido_em=now(), conferido_por=? where fechamento_id=? and conferido_em is null", usuario, fechamentoId);
        for (Long lote : lotes) historico.lote(lote, "CONFERIDO_HOSPITAL", "Lote conferido pelo hospital (fechamento nº " + fechamentoId + ", em lote)", usuario);
        if (!lotes.isEmpty()) historico.fechamento(fechamentoId, "LOTES_CONFERIDOS", lotes.size() + " lote(s) marcados como conferidos de uma vez", usuario);
        return lotes.size();
    }

    @Transactional
    public void contestar(long fechamentoId, long itemId, String motivo, long hospitalId, String usuario) {
        exigirDoHospital(fechamentoId, hospitalId);
        exigirEmConferencia(fechamentoId);
        boolean retirar = motivo == null;
        if (!retirar && motivo.isBlank()) throw new IllegalArgumentException("Informe o motivo da contestação");
        int n = jdbc.update(retirar
                ? "update producao_item set contestacao=null, contestacao_motivo='', contestacao_por=null, contestacao_em=null where id=? and contestacao='PENDENTE' and id in (select item_id from producao_fechamento_item where fechamento_id=? and situacao='INCLUIDA')"
                : "update producao_item set contestacao='PENDENTE', contestacao_motivo=?, contestacao_por=?, contestacao_em=now() where id=? and id in (select item_id from producao_fechamento_item where fechamento_id=? and situacao='INCLUIDA')",
                retirar ? new Object[]{itemId, fechamentoId} : new Object[]{motivo.trim().length() > 500 ? motivo.trim().substring(0, 500) : motivo.trim(), usuario, itemId, fechamentoId});
        if (n == 0) throw new RecursoNaoEncontradoException("Etiqueta nao pertence a este fechamento");
        Long lote = jdbc.queryForObject("select lote_id from producao_item where id=?", Long.class, itemId);
        String nr = jdbc.queryForObject("select nr_atendimento from producao_item where id=?", String.class, itemId);
        historico.lote(lote, retirar ? "CONTESTACAO_RETIRADA" : "CONTESTADA",
                retirar ? "Hospital retirou a contestação do atendimento " + nr : "Hospital contestou o atendimento " + nr + ": " + motivo.trim(), usuario);
    }

    @Transactional
    public FechamentoProducaoService.Detalhe aprovar(long fechamentoId, long hospitalId, String usuario) {
        exigirDoHospital(fechamentoId, hospitalId);
        exigirEmConferencia(fechamentoId);
        Integer faltam = jdbc.queryForObject("select count(*) from producao_fechamento_lote where fechamento_id=? and conferido_em is null", Integer.class, fechamentoId);
        if (faltam != null && faltam > 0) throw new IllegalArgumentException("Confira todos os lotes antes de liberar (faltam " + faltam + ")");
        fechamentos.aplicarContestacoes(fechamentoId, usuario);
        jdbc.update("update producao_fechamento set status='APROVADO', aprovado_por=?, aprovado_em=? where id=?",
                usuario + " (portal do hospital)", LocalDate.now(), fechamentoId);
        historico.fechamento(fechamentoId, "APROVADO", "Aprovado e liberado para NF pelo hospital", usuario);
        return fechamentos.detalhe(fechamentoId);
    }

    public Map<String, Object> nfArquivo(long fechamentoId, long hospitalId) {
        exigirDoHospital(fechamentoId, hospitalId);
        return fechamentos.nfArquivo(fechamentoId);
    }

    public FechamentoProducaoService.Detalhe detalheParaPlanilha(long fechamentoId, long hospitalId) {
        exigirDoHospital(fechamentoId, hospitalId);
        return fechamentos.detalhe(fechamentoId);
    }
}
