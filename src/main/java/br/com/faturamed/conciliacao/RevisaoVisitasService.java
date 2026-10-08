package br.com.faturamed.conciliacao;

import br.com.faturamed.shared.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevisaoVisitasService {
    public record Revisao(long id, int linhaMedico, RevisaoVisitaRequest.Acao acao,
            String responsavel, String justificativa, Instant criadoEm,
            ConciliadorVisitas.Visita antes, ConciliadorVisitas.Visita depois) {}
    public record Estado(ConciliadorVisitas.Relatorio original, ConciliadorVisitas.Relatorio atual,
            List<Revisao> historico, long versao) {}
    public record Candidato(ConciliadorVisitas.RegistroHospital registro, Integer associadoALinha) {}
    public record Detalhe(long versao, ConciliadorVisitas.Visita original, ConciliadorVisitas.Visita atual,
            List<Candidato> candidatos, List<Revisao> historico) {}
    public record Resultado(long versao, ConciliadorVisitas.Relatorio relatorio) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AplicadorRevisaoVisita aplicador;

    public RevisaoVisitasService(JdbcTemplate jdbc, ObjectMapper json, AplicadorRevisaoVisita aplicador) {
        this.jdbc = jdbc; this.json = json; this.aplicador = aplicador;
    }

    @Transactional(readOnly = true)
    public Estado carregar(Long id) throws Exception { return ler(id, false); }

    @Transactional(readOnly = true)
    public Detalhe detalhar(Long id, int linha) throws Exception {
        return detalhar(id, linha, null, null);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(Long id, int linha, String atendimento, java.time.LocalDate data) throws Exception {
        Estado estado = ler(id, false);
        var original = visita(estado.original(), linha);
        var atual = visita(estado.atual(), linha);
        if ((atendimento == null) != (data == null)) throw new IllegalArgumentException("Informe atendimento e data juntos");
        var candidatos = aplicador.candidatos(estado.original(), atendimento == null ? atual.atendimento() : atendimento.trim(),
                data == null ? atual.data() : data).stream().map(h -> new Candidato(h,
                estado.atual().visitas().stream().filter(v -> v.linhaMedico() != linha && v.hospital() != null
                        && v.hospital().linha() == h.linha()).map(ConciliadorVisitas.Visita::linhaMedico).findFirst().orElse(null))).toList();
        return new Detalhe(estado.versao(), original, atual, candidatos,
                estado.historico().stream().filter(r -> r.linhaMedico() == linha).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Resultado revisar(Long id, int linha, RevisaoVisitaRequest request) throws Exception {
        // O bloqueio serializa revisoes da rodada e protege a exclusividade do registro hospitalar.
        Estado estado = ler(id, true);
        if (estado.versao() != request.versao()) {
            throw new ConflitoRevisaoException("Esta conciliacao recebeu outra revisao. Atualize os detalhes antes de salvar");
        }
        if (request.responsavel().trim().isBlank() || request.justificativa().trim().length() < 5) {
            throw new IllegalArgumentException("Informe responsavel e justificativa com pelo menos 5 caracteres");
        }
        var original = visita(estado.original(), linha);
        var antes = visita(estado.atual(), linha);
        var depois = aplicador.aplicar(original, estado.atual(), request, estado.original());
        Long revisaoId = jdbc.queryForObject("""
                insert into revisao_visita (conciliacao_id, linha_medico, acao, responsavel, justificativa, antes, depois)
                values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb) returning id
                """, Long.class, id, linha, request.acao().name(), request.responsavel().trim(), request.justificativa().trim(),
                json.writeValueAsString(antes), json.writeValueAsString(depois));
        var visitas = estado.atual().visitas().stream().map(v -> v.linhaMedico() == linha ? depois : v).toList();
        return new Resultado(revisaoId, aplicador.recalcular(estado.original(), visitas));
    }

    private Estado ler(Long id, boolean bloquear) throws Exception {
        var registros = jdbc.queryForList("select relatorio::text from conciliacao_visitas where id = ?"
                + (bloquear ? " for update" : ""), String.class, id);
        if (registros.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        var original = json.readValue(registros.getFirst(), ConciliadorVisitas.Relatorio.class);
        var linhas = jdbc.queryForList("select * from revisao_visita where conciliacao_id = ? order by id", id);
        List<Revisao> historico = new ArrayList<>();
        Map<Integer, ConciliadorVisitas.Visita> atualizadas = new HashMap<>();
        long versao = 0;
        for (var registro : linhas) {
            long revisaoId = ((Number) registro.get("id")).longValue();
            var antes = json.readValue(registro.get("antes").toString(), ConciliadorVisitas.Visita.class);
            var depois = json.readValue(registro.get("depois").toString(), ConciliadorVisitas.Visita.class);
            Instant criado = ((java.sql.Timestamp) registro.get("criado_em")).toInstant();
            historico.add(new Revisao(revisaoId, ((Number) registro.get("linha_medico")).intValue(),
                    RevisaoVisitaRequest.Acao.valueOf(registro.get("acao").toString()),
                    registro.get("responsavel").toString(), registro.get("justificativa").toString(), criado, antes, depois));
            atualizadas.put(depois.linhaMedico(), depois); versao = revisaoId;
        }
        var visitas = original.visitas().stream().map(v -> atualizadas.getOrDefault(v.linhaMedico(), v)).toList();
        return new Estado(original, aplicador.recalcular(original, visitas), List.copyOf(historico), versao);
    }

    private ConciliadorVisitas.Visita visita(ConciliadorVisitas.Relatorio relatorio, int linha) {
        return relatorio.visitas().stream().filter(v -> v.linhaMedico() == linha).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Linha do medico nao encontrada"));
    }
}
