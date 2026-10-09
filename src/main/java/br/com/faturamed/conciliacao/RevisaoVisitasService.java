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
    public record Resultado(long versao, ConciliadorVisitas.Relatorio relatorio, int automaticas) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AplicadorRevisaoVisita aplicador;
    private final ReavaliadorVisitas reavaliador;
    private final MedicoCadastroService medicos;

    public RevisaoVisitasService(JdbcTemplate jdbc, ObjectMapper json, AplicadorRevisaoVisita aplicador, ReavaliadorVisitas reavaliador, MedicoCadastroService medicos) {
        this.jdbc = jdbc; this.json = json; this.aplicador = aplicador;
        this.reavaliador = reavaliador;
        this.medicos = medicos;
    }

    @Transactional(readOnly = true)
    public Estado carregar(Long id) throws Exception { return ler(id, false); }

    @Transactional
    public void excluir(Long id) {
        var ids = jdbc.queryForList("select id from conciliacao_visitas where id = ? for update", Long.class, id);
        if (ids.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty()) throw new ConflitoRevisaoException("Exclua primeiro a versao mais recente deste relatorio");
        jdbc.update("delete from revisao_visita where conciliacao_id = ?", id);
        jdbc.update("delete from conciliacao_visitas where id = ?", id);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(Long id, int linha) throws Exception {
        return detalhar(id, linha, null, null);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(Long id, int linha, String atendimento, java.time.LocalDate data) throws Exception {
        Estado estado = ler(id, false);
        var original = visita(estado.original(), linha);
        var atual = visita(estado.atual(), linha);
        if (atendimento == null && data != null) throw new IllegalArgumentException("Informe atendimento para buscar pela data");
        var candidatos = aplicador.candidatos(estado.original(), atendimento == null ? atual.atendimento() : atendimento.trim(),
                atendimento == null ? atual.data() : data).stream().map(h -> new Candidato(h,
                estado.atual().visitas().stream().filter(v -> v.linhaMedico() != linha && v.hospital() != null
                        && v.hospital().linha() == h.linha()).map(ConciliadorVisitas.Visita::linhaMedico).findFirst().orElse(null))).toList();
        return new Detalhe(estado.versao(), original, atual, candidatos,
                estado.historico().stream().filter(r -> r.linhaMedico() == linha).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Resultado revisar(Long id, int linha, RevisaoVisitaRequest request) throws Exception {
        // O bloqueio serializa revisoes da rodada e protege a exclusividade do registro hospitalar.
        Estado estado = ler(id, true);
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty()) throw new ConflitoRevisaoException("Este relatorio possui nova versao. Abra a versao mais recente para revisar");
        if (estado.versao() != request.versao()) {
            throw new ConflitoRevisaoException("Esta conciliacao recebeu outra revisao. Atualize os detalhes antes de salvar");
        }
        if (request.responsavel().trim().isBlank() || request.justificativa().trim().length() < 5) {
            throw new IllegalArgumentException("Informe responsavel e justificativa com pelo menos 5 caracteres");
        }
        var original = visita(estado.original(), linha);
        var antes = visita(estado.atual(), linha);
        if(request.ajuste()!=null && request.ajuste().medicoCadastroId()!=null) {
            var cadastro=medicos.buscar(request.ajuste().medicoCadastroId());
            if(!cadastro.ativo()) throw new IllegalArgumentException("Selecione um medico ativo");
            var a=request.ajuste();
            request=new RevisaoVisitaRequest(request.acao(),request.linhaHospital(),request.responsavel(),request.justificativa(),request.versao(),
                    new RevisaoVisitaRequest.Ajuste(a.data(),a.atendimento(),cadastro.nome(),a.procedimento(),a.codigoProcedimento(),a.valorHospital(),a.repasse(),a.status(),a.motivo(),cadastro.id()));
        }
        var depois = aplicador.aplicar(original, estado.atual(), request, estado.original());
        Long revisaoId = jdbc.queryForObject("""
                insert into revisao_visita (conciliacao_id, linha_medico, acao, responsavel, justificativa, antes, depois)
                values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb) returning id
                """, Long.class, id, linha, request.acao().name(), request.responsavel().trim(), request.justificativa().trim(),
                json.writeValueAsString(antes), json.writeValueAsString(depois));
        var visitas = new ArrayList<>(estado.atual().visitas().stream().map(v -> v.linhaMedico() == linha ? depois : v).toList());
        int automaticas = 0;
        if (depois.hospital() != null && (antes.hospital() == null || antes.hospital().linha() != depois.hospital().linha())
                && request.acao() != RevisaoVisitaRequest.Acao.RESTAURAR_AUTOMATICO) {
            Set<Integer> revisadas = new HashSet<>();
            estado.historico().forEach(r -> revisadas.add(r.linhaMedico())); revisadas.add(linha);
            var propostas = reavaliador.propostas(estado.original(), visitas, depois.atendimento(), revisadas);
            for (var proposta : propostas.entrySet()) {
                var anterior = visitas.stream().filter(v -> v.linhaMedico() == proposta.getKey()).findFirst().orElseThrow();
                var h = proposta.getValue();
                var automatica = new ConciliadorVisitas.Visita(anterior.linhaMedico(), anterior.atendimento(), h.data(),
                        LeitorPlanilhaVisitas.normalizar(h.setor()).equals("faturado") ? ConciliadorVisitas.Status.PAGA : ConciliadorVisitas.Status.PENDENTE,
                        "Correspondencia unica e exclusiva apos revisao da linha " + linha,
                        anterior.original(), h, anterior.candidatos());
                revisaoId = jdbc.queryForObject("""
                        insert into revisao_visita (conciliacao_id, linha_medico, acao, responsavel, justificativa, antes, depois)
                        values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb) returning id
                        """, Long.class, id, anterior.linhaMedico(), "CORRESPONDENCIA_AUTOMATICA", "Sistema",
                        "Reavaliacao apos revisao " + request.acao() + " da linha " + linha + " por " + request.responsavel().trim(),
                        json.writeValueAsString(anterior), json.writeValueAsString(automatica));
                visitas.replaceAll(v -> v.linhaMedico() == anterior.linhaMedico() ? automatica : v);
                automaticas++;
            }
        }
        return new Resultado(revisaoId, aplicador.recalcular(estado.original(), visitas), automaticas);
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
            var candidatos=aplicador.candidatos(original,depois.atendimento(),depois.data());
            atualizadas.put(depois.linhaMedico(),new ConciliadorVisitas.Visita(depois.linhaMedico(),depois.atendimento(),depois.data(),depois.status(),depois.motivo(),depois.original(),depois.hospital(),candidatos,depois.camposRevisados())); versao = revisaoId;
        }
        var visitas = original.visitas().stream().map(v -> atualizadas.getOrDefault(v.linhaMedico(), v)).toList();
        return new Estado(original, aplicador.recalcular(original, visitas), List.copyOf(historico), versao);
    }

    private ConciliadorVisitas.Visita visita(ConciliadorVisitas.Relatorio relatorio, int linha) {
        return relatorio.visitas().stream().filter(v -> v.linhaMedico() == linha).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Linha do medico nao encontrada"));
    }
}
