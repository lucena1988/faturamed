package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChamadoService {
    public record Aviso(long id, long chamadoId, String protocolo, String titulo, String tipo, Instant criadoEm) {}
    public record CaixaAvisos(long naoLidas, List<Aviso> itens) {}

    @Transactional(readOnly = true)
    public CaixaAvisos avisos(UsuarioAutenticado user) {
        long total = jdbc.queryForObject("select count(*) from chamado_aviso where usuario_id=? and lido_em is null", Long.class, user.id());
        var itens = jdbc.query("""
            select a.*,c.protocolo,c.titulo from chamado_aviso a join chamado_suporte c on c.id=a.chamado_id
            where a.usuario_id=? and a.lido_em is null order by a.id desc limit 50
            """, (r,n) -> new Aviso(r.getLong("id"),r.getLong("chamado_id"),r.getString("protocolo"),
                r.getString("titulo"),r.getString("tipo"),r.getTimestamp("criado_em").toInstant()),user.id());
        return new CaixaAvisos(total,itens);
    }

    @Transactional
    public void lerAviso(long id, UsuarioAutenticado user) {
        if (jdbc.update("update chamado_aviso set lido_em=coalesce(lido_em,now()) where id=? and usuario_id=?",id,user.id()) == 0)
            throw new RecursoNaoEncontradoException("Aviso nao encontrado");
    }

    private void avisar(ChamadoItem chamado, UsuarioAutenticado autor, String tipo, boolean suporte) {
        jdbc.update("""
            insert into chamado_aviso(chamado_id,usuario_id,tipo)
            select ?,u.id,? from usuario_acesso u where u.ativo=true and u.id<>?
            and ((?=false and u.id=? and u.perfil<>'MEDICO')
              or (?=true and u.perfil in ('ADMIN','DEV') and (?::bigint is null or u.id=?)))
            """,chamado.id(),tipo,autor.id(),suporte,chamado.solicitanteId(),suporte,chamado.responsavelId(),chamado.responsavelId());
    }

    static final int LIMITE_ARQUIVO = 5 * 1024 * 1024;
    private static final Set<String> FILAS = Set.of("DEV", "ADMIN");
    private static final Set<String> PRIORIDADES = Set.of("CRITICA", "ALTA", "MEDIA", "BAIXA");
    private static final Set<String> STATUS_VALIDOS = Set.of("ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_USUARIO", "RESOLVIDO", "FECHADO");

    public record TipoSla(String codigo, String nome, String descricao, String filaPadrao, String prioridadePadrao,
                          int slaHoras, boolean ativo, int ordem) {}

    public record AtualizarSla(@NotNull @Min(1) @Max(720) Integer slaHoras,
                               @NotBlank String filaPadrao,
                               @NotBlank String prioridadePadrao) {}

    public record Atendente(long id, String nome, String email, String perfil) {}

    public record ChamadoItem(long id, String protocolo, long solicitanteId, String solicitanteNome,
                              String solicitanteEmail, String solicitantePerfil, String tipoCodigo, String tipoNome,
                              String filaDestino, String prioridade, String status, String titulo, String descricao,
                              String paginaOrigem, Long responsavelId, String responsavelNome, int slaHoras,
                              Instant prazoSlaEm, Instant primeiroAtendimentoEm, Instant resolvidoEm, Instant fechadoEm,
                              Integer notaAvaliacao, String comentarioAvaliacao, Instant avaliadoEm,
                              Instant criadoEm, Instant atualizadoEm, long versao,
                              String slaStatus, long minutosRestantes, long totalMensagens, long totalAnexos) {}

    public record ResumoKanban(long totalAbertos, long noPrazo, long emAlerta, long vencidos, long resolvidos,
                               Double mediaAvaliacao, long totalAvaliacoes, long filaDevAbertos, long filaAdminAbertos) {}

    public record PainelChamados(ResumoKanban resumo, List<ChamadoItem> chamados) {}

    public record AnexoItem(long id, long chamadoId, Long mensagemId, long usuarioId, String usuarioNome,
                            String nomeArquivo, String contentType, int tamanho, Instant criadoEm, String url) {}

    public record MensagemItem(long id, long chamadoId, long autorId, String autorNome, String autorPerfil,
                               String tipo, String conteudo, Instant criadoEm, List<AnexoItem> anexos) {}

    public record DetalheChamado(ChamadoItem chamado, List<MensagemItem> mensagens, List<AnexoItem> anexos) {}

    public record AlteracaoStatus(String status, String filaDestino, String prioridade, Long responsavelId,
                                  Boolean assumir, @Size(max = 2000) String observacao) {}

    public record Avaliacao(@NotNull @Min(1) @Max(5) Integer nota, @Size(max = 1500) String comentario) {}

    public record ArquivoAnexo(String nome, String contentType, byte[] conteudo) {}

    private final JdbcTemplate jdbc;

    public ChamadoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TipoSla> listarTipos() {
        return jdbc.query("""
                select codigo, nome, descricao, fila_padrao, prioridade_padrao, sla_horas, ativo, ordem
                from chamado_tipo_sla
                where ativo = true
                order by ordem asc, codigo asc
                """, (r, n) -> new TipoSla(
                r.getString("codigo"),
                r.getString("nome"),
                r.getString("descricao"),
                r.getString("fila_padrao"),
                r.getString("prioridade_padrao"),
                r.getInt("sla_horas"),
                r.getBoolean("ativo"),
                r.getInt("ordem")
        ));
    }

    @Transactional
    public TipoSla atualizarSla(String codigo, AtualizarSla dados, UsuarioAutenticado user) {
        exigirAtendente(user);
        String fila = Objects.toString(dados.filaPadrao(), "").trim().toUpperCase(Locale.ROOT);
        String prioridade = Objects.toString(dados.prioridadePadrao(), "").trim().toUpperCase(Locale.ROOT);
        if (!FILAS.contains(fila)) throw new IllegalArgumentException("Fila padrao invalida");
        if (!PRIORIDADES.contains(prioridade)) throw new IllegalArgumentException("Prioridade padrao invalida");
        if (dados.slaHoras() == null || dados.slaHoras() < 1 || dados.slaHoras() > 720)
            throw new IllegalArgumentException("O prazo de SLA deve estar entre 1 e 720 horas");
        int updated = jdbc.update("""
                update chamado_tipo_sla
                set sla_horas = ?, fila_padrao = ?, prioridade_padrao = ?
                where codigo = ?
                """, dados.slaHoras(), fila, prioridade, codigo);
        if (updated == 0) throw new RecursoNaoEncontradoException("Tipo de chamado nao encontrado");
        return listarTipos().stream().filter(t -> t.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de chamado nao encontrado"));
    }

    public List<Atendente> listarAtendentes(UsuarioAutenticado user) {
        exigirAtendente(user);
        return jdbc.query("""
                select id, nome, email, perfil
                from usuario_acesso
                where ativo = true and perfil in ('ADMIN', 'DEV')
                order by perfil desc, nome asc
                """, (r, n) -> new Atendente(
                r.getLong("id"),
                r.getString("nome"),
                r.getString("email"),
                r.getString("perfil")
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public DetalheChamado criar(String tipoCodigo, String titulo, String descricao, String paginaOrigem,
                                List<MultipartFile> arquivos, UsuarioAutenticado user) throws Exception {
        String codigo = Objects.toString(tipoCodigo, "").trim().toUpperCase(Locale.ROOT);
        String tit = Objects.toString(titulo, "").trim();
        String desc = Objects.toString(descricao, "").trim();
        String origem = Objects.toString(paginaOrigem, "").trim();
        if (tit.isBlank() || tit.length() > 180)
            throw new IllegalArgumentException("Informe um titulo para o chamado (ate 180 caracteres)");
        if (desc.isBlank() || desc.length() > 4000)
            throw new IllegalArgumentException("Descreva o problema ou solicitacao (ate 4000 caracteres)");
        if (origem.length() > 250) origem = origem.substring(0, 250);

        var tipo = listarTipos().stream().filter(t -> t.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selecione uma categoria de chamado valida"));

        var validados = validarArquivos(arquivos);
        Instant agora = Instant.now();
        Instant prazoSla = agora.plus(Duration.ofHours(tipo.slaHoras()));
        String protocoloTemp = "TMP-" + UUID.randomUUID().toString().substring(0, 12);

        long id = jdbc.queryForObject("""
                insert into chamado_suporte (
                    protocolo, solicitante_id, solicitante_nome, solicitante_email, solicitante_perfil,
                    tipo_codigo, fila_destino, prioridade, status, titulo, descricao, pagina_origem,
                    sla_horas, prazo_sla_em, criado_em, atualizado_em
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'ABERTO', ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                protocoloTemp, user.id(), user.nome(), user.email(), user.perfil(),
                tipo.codigo(), tipo.filaPadrao(), tipo.prioridadePadrao(),
                tit, desc, origem.isBlank() ? null : origem,
                tipo.slaHoras(), java.sql.Timestamp.from(prazoSla),
                java.sql.Timestamp.from(agora), java.sql.Timestamp.from(agora)
        );

        int ano = Year.now(ZoneId.of("America/Sao_Paulo")).getValue();
        String protocolo = String.format("CH-%d-%04d", ano, id);
        jdbc.update("update chamado_suporte set protocolo = ? where id = ?", protocolo, id);

        String msgEvento = String.format(
                "Protocolo %s aberto por %s (%s) e distribuido automaticamente para a fila %s (SLA: %dh).",
                protocolo, user.nome(), rotuloPerfil(user.perfil()),
                tipo.filaPadrao().equals("DEV") ? "Desenvolvimento (DEV)" : "Administracao / Faturamento (ADMIN)",
                tipo.slaHoras()
        );
        long msgId = inserirMensagem(id, user, "EVENTO_SISTEMA", msgEvento);
        salvarAnexos(id, msgId, validados, user);

        return detalhar(id, user);
    }

    @Transactional(readOnly = true)
    public PainelChamados listar(String fila, String status, String tipoCodigo, String busca,
                                 boolean apenasMeus, UsuarioAutenticado user) {
        boolean isMedico = "MEDICO".equals(user.perfil());
        StringBuilder sql = new StringBuilder(SELECT_CHAMADO_BASE).append(" where 1=1 ");
        List<Object> params = new ArrayList<>();

        if ("OPERADOR".equals(user.perfil())) {
            sql.append(" and (c.solicitante_perfil in ('OPERADOR','MEDICO') or c.solicitante_id = ?) ");
            params.add(user.id());
        }
        if (isMedico) {
            sql.append(" and c.solicitante_id = ? ");
            params.add(user.id());
        } else if (apenasMeus) {
            sql.append(" and (c.solicitante_id = ? or c.responsavel_id = ?) ");
            params.add(user.id());
            params.add(user.id());
        }

        if (fila != null && !fila.isBlank() && !"TODAS".equalsIgnoreCase(fila)) {
            sql.append(" and c.fila_destino = ? ");
            params.add(fila.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank() && !"TODOS".equalsIgnoreCase(status)) {
            sql.append(" and c.status = ? ");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (tipoCodigo != null && !tipoCodigo.isBlank() && !"TODOS".equalsIgnoreCase(tipoCodigo)) {
            sql.append(" and c.tipo_codigo = ? ");
            params.add(tipoCodigo.trim().toUpperCase(Locale.ROOT));
        }
        if (busca != null && !busca.isBlank()) {
            String like = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" and (lower(c.protocolo) like ? or lower(c.titulo) like ? or lower(c.solicitante_nome) like ? or lower(c.solicitante_email) like ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append("""
                 order by
                   case when c.status in ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_USUARIO') then 0 else 1 end asc,
                   c.prazo_sla_em asc,
                   c.atualizado_em desc
                """);

        List<ChamadoItem> itens = jdbc.query(sql.toString(), this::mapearChamado, params.toArray());
        ResumoKanban resumo = calcularResumo(user, isMedico);
        return new PainelChamados(resumo, itens);
    }

    private ResumoKanban calcularResumo(UsuarioAutenticado user, boolean isMedico) {
        String filtroEscopo = isMedico ? " where c.solicitante_id = ? " : "";
        if ("OPERADOR".equals(user.perfil())) filtroEscopo = " where (c.solicitante_perfil in ('OPERADOR','MEDICO') or c.solicitante_id = ?) ";
        Object[] args = isMedico || "OPERADOR".equals(user.perfil()) ? new Object[]{user.id()} : new Object[]{};
        List<ChamadoItem> todos = jdbc.query(SELECT_CHAMADO_BASE + filtroEscopo, this::mapearChamado, args);
        long abertos = 0, noPrazo = 0, emAlerta = 0, vencidos = 0, resolvidos = 0, filaDev = 0, filaAdmin = 0;
        long somaNotas = 0, totalAvaliacoes = 0;
        for (ChamadoItem c : todos) {
            boolean emAberto = !("RESOLVIDO".equals(c.status()) || "FECHADO".equals(c.status()));
            if (emAberto) {
                abertos++;
                if ("DEV".equals(c.filaDestino())) filaDev++;
                else if ("ADMIN".equals(c.filaDestino())) filaAdmin++;
                switch (c.slaStatus()) {
                    case "VENCIDO" -> vencidos++;
                    case "EM_ALERTA" -> emAlerta++;
                    default -> noPrazo++;
                }
            } else {
                resolvidos++;
            }
            if (c.notaAvaliacao() != null) {
                somaNotas += c.notaAvaliacao();
                totalAvaliacoes++;
            }
        }
        Double media = totalAvaliacoes > 0 ? Math.round((somaNotas * 10.0) / totalAvaliacoes) / 10.0 : null;
        return new ResumoKanban(abertos, noPrazo, emAlerta, vencidos, resolvidos, media, totalAvaliacoes, filaDev, filaAdmin);
    }

    @Transactional(readOnly = true)
    public DetalheChamado detalhar(long id, UsuarioAutenticado user) {
        ChamadoItem chamado = buscarChamado(id, user);
        List<AnexoItem> anexos = jdbc.query("""
                select id, chamado_id, mensagem_id, usuario_id, usuario_nome, nome_arquivo, content_type, tamanho, criado_em
                from chamado_anexo
                where chamado_id = ?
                order by id asc
                """, (r, n) -> new AnexoItem(
                r.getLong("id"),
                r.getLong("chamado_id"),
                r.getObject("mensagem_id", Long.class),
                r.getLong("usuario_id"),
                r.getString("usuario_nome"),
                r.getString("nome_arquivo"),
                r.getString("content_type"),
                r.getInt("tamanho"),
                r.getTimestamp("criado_em").toInstant(),
                "/api/chamados/" + id + "/anexos/" + r.getLong("id")
        ), id);

        Map<Long, List<AnexoItem>> porMensagem = new HashMap<>();
        for (AnexoItem a : anexos) {
            if (a.mensagemId() != null) {
                porMensagem.computeIfAbsent(a.mensagemId(), k -> new ArrayList<>()).add(a);
            }
        }

        List<MensagemItem> mensagens = jdbc.query("""
                select id, chamado_id, autor_id, autor_nome, autor_perfil, tipo, conteudo, criado_em
                from chamado_mensagem
                where chamado_id = ?
                order by criado_em asc, id asc
                """, (r, n) -> {
            long msgId = r.getLong("id");
            return new MensagemItem(
                    msgId,
                    r.getLong("chamado_id"),
                    r.getLong("autor_id"),
                    r.getString("autor_nome"),
                    r.getString("autor_perfil"),
                    r.getString("tipo"),
                    r.getString("conteudo"),
                    r.getTimestamp("criado_em").toInstant(),
                    porMensagem.getOrDefault(msgId, List.of())
            );
        }, id);

        return new DetalheChamado(chamado, mensagens, anexos);
    }

    @Transactional(rollbackFor = Exception.class)
    public DetalheChamado adicionarMensagem(long id, String conteudo, List<MultipartFile> arquivos,
                                            UsuarioAutenticado user) throws Exception {
        ChamadoItem chamado = buscarChamado(id, user);
        if ("FECHADO".equals(chamado.status())) {
            throw new ConflitoRevisaoException("Este chamado ja foi encerrado. Reabra o chamado se precisar adicionar novas interacoes.");
        }
        String texto = Objects.toString(conteudo, "").trim();
        var validados = validarArquivos(arquivos);
        if (texto.isBlank() && validados.isEmpty()) {
            throw new IllegalArgumentException("Digite uma mensagem ou anexe uma imagem/arquivo");
        }
        if (texto.length() > 4000) {
            throw new IllegalArgumentException("A mensagem deve ter no maximo 4000 caracteres");
        }
        if (texto.isBlank()) {
            texto = "Anexo enviado (" + validados.size() + " arquivo(s)).";
        }

        long msgId = inserirMensagem(id, user, "MENSAGEM", texto);
        if (user.id() == chamado.solicitanteId()) avisar(chamado,user,"RESPOSTA_SOLICITANTE",true);
        else if ("ADMIN".equals(user.perfil()) || "DEV".equals(user.perfil())) avisar(chamado,user,"MENSAGEM_SUPORTE",false);
        salvarAnexos(id, msgId, validados, user);

        boolean isAtendente = ("ADMIN".equals(user.perfil()) || "DEV".equals(user.perfil())) && user.id() != chamado.solicitanteId();
        if (isAtendente && "ABERTO".equals(chamado.status())) {
            jdbc.update("""
                    update chamado_suporte
                    set status = 'EM_ATENDIMENTO',
                        primeiro_atendimento_em = coalesce(primeiro_atendimento_em, now()),
                        responsavel_id = coalesce(responsavel_id, ?),
                        responsavel_nome = coalesce(responsavel_nome, ?),
                        atualizado_em = now(),
                        versao = versao + 1
                    where id = ?
                    """, user.id(), user.nome(), id);
        } else if (user.id() == chamado.solicitanteId() && "AGUARDANDO_USUARIO".equals(chamado.status())) {
            jdbc.update("""
                    update chamado_suporte
                    set status = 'EM_ATENDIMENTO',
                        atualizado_em = now(),
                        versao = versao + 1
                    where id = ?
                    """, id);
        } else {
            jdbc.update("update chamado_suporte set atualizado_em = now(), versao = versao + 1 where id = ?", id);
        }

        return detalhar(id, user);
    }

    @Transactional
    public DetalheChamado atualizarStatus(long id, AlteracaoStatus dados, UsuarioAutenticado user) {
        ChamadoItem atual = buscarChamado(id, user);
        boolean isAtendente = "ADMIN".equals(user.perfil()) || "DEV".equals(user.perfil());
        if (!isAtendente) {
            // O solicitante pode confirmar ou reabrir apenas o proprio chamado resolvido.
            String novoSt = Objects.toString(dados.status(), "").trim().toUpperCase(Locale.ROOT);
            if (atual.solicitanteId() != user.id() || !"RESOLVIDO".equals(atual.status())
                    || !Set.of("EM_ATENDIMENTO", "FECHADO").contains(novoSt)
                    || dados.filaDestino() != null || dados.prioridade() != null
                    || dados.responsavelId() != null || Boolean.TRUE.equals(dados.assumir())) {
                throw new AccessDeniedException("Apenas a equipe de suporte pode alterar a fila ou etapa do Kanban");
            }
        }

        String novoStatus = dados.status() == null || dados.status().isBlank()
                ? atual.status() : dados.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUS_VALIDOS.contains(novoStatus)) throw new IllegalArgumentException("Status invalido");

        String novaFila = !isAtendente || dados.filaDestino() == null || dados.filaDestino().isBlank()
                ? atual.filaDestino() : dados.filaDestino().trim().toUpperCase(Locale.ROOT);
        if (!FILAS.contains(novaFila)) throw new IllegalArgumentException("Fila de destino invalida");

        String novaPrioridade = !isAtendente || dados.prioridade() == null || dados.prioridade().isBlank()
                ? atual.prioridade() : dados.prioridade().trim().toUpperCase(Locale.ROOT);
        if (!PRIORIDADES.contains(novaPrioridade)) throw new IllegalArgumentException("Prioridade invalida");

        Long novoRespId = atual.responsavelId();
        String novoRespNome = atual.responsavelNome();
        if (isAtendente) {
            if (Boolean.TRUE.equals(dados.assumir())) {
                novoRespId = user.id();
                novoRespNome = user.nome();
            } else if (dados.responsavelId() != null) {
                if (dados.responsavelId() <= 0) {
                    novoRespId = null;
                    novoRespNome = null;
                } else {
                    long respId = dados.responsavelId();
                    var atendente = listarAtendentes(user).stream().filter(a -> a.id() == respId).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Responsavel selecionado invalido"));
                    novoRespId = atendente.id();
                    novoRespNome = atendente.nome();
                }
            } else if ("EM_ATENDIMENTO".equals(novoStatus) && novoRespId == null) {
                novoRespId = user.id();
                novoRespNome = user.nome();
            }
        }

        jdbc.update("""
                update chamado_suporte
                set status = ?,
                    fila_destino = ?,
                    prioridade = ?,
                    responsavel_id = ?,
                    responsavel_nome = ?,
                    primeiro_atendimento_em = case
                        when ? in ('EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO')
                        then coalesce(primeiro_atendimento_em, now())
                        else primeiro_atendimento_em end,
                    resolvido_em = case
                        when ? in ('RESOLVIDO', 'FECHADO') then coalesce(resolvido_em, now())
                        when ? in ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO') then null
                        else resolvido_em end,
                    fechado_em = case
                        when ? = 'FECHADO' then coalesce(fechado_em, now())
                        else null end,
                    atualizado_em = now(),
                    versao = versao + 1
                where id = ?
                """,
                novoStatus, novaFila, novaPrioridade, novoRespId, novoRespNome,
                novoStatus, novoStatus, novoStatus, novoStatus, id);

        if ("AGUARDANDO_USUARIO".equals(novoStatus) && !novoStatus.equals(atual.status()))
            avisar(atual,user,"RETORNO_SOLICITADO",false);

        List<String> mudancas = new ArrayList<>();
        if (!novoStatus.equals(atual.status())) {
            mudancas.add("Status alterado de " + rotuloStatus(atual.status()) + " para " + rotuloStatus(novoStatus));
        }
        if (!novaFila.equals(atual.filaDestino())) {
            mudancas.add("Redirecionado da fila " + atual.filaDestino() + " para a fila " + novaFila);
        }
        if (!novaPrioridade.equals(atual.prioridade())) {
            mudancas.add("Prioridade alterada para " + novaPrioridade);
        }
        if (!Objects.equals(novoRespId, atual.responsavelId())) {
            mudancas.add(novoRespNome != null ? "Atribuido para " + novoRespNome : "Responsavel removido");
        }
        String obs = Objects.toString(dados.observacao(), "").trim();
        if (!obs.isBlank()) {
            mudancas.add("Nota: " + obs);
        }
        if (!mudancas.isEmpty()) {
            inserirMensagem(id, user, "EVENTO_SISTEMA", String.join(" | ", mudancas));
        }

        return detalhar(id, user);
    }

    @Transactional
    public DetalheChamado avaliar(long id, Avaliacao dados, UsuarioAutenticado user) {
        ChamadoItem atual = buscarChamado(id, user);
        if (atual.solicitanteId() != user.id()) {
            throw new AccessDeniedException("Apenas o solicitante que abriu o chamado pode avaliar o atendimento");
        }
        if (!Set.of("RESOLVIDO", "FECHADO").contains(atual.status())) {
            throw new IllegalArgumentException("O chamado precisa estar Resolvido ou Fechado para receber avaliacao");
        }
        int nota = dados.nota();
        String comentario = Objects.toString(dados.comentario(), "").trim();
        jdbc.update("""
                update chamado_suporte
                set nota_avaliacao = ?,
                    comentario_avaliacao = ?,
                    avaliado_em = now(),
                    status = 'FECHADO',
                    fechado_em = coalesce(fechado_em, now()),
                    atualizado_em = now(),
                    versao = versao + 1
                where id = ?
                """, nota, comentario.isBlank() ? null : comentario, id);

        String estrelas = "★".repeat(nota) + "☆".repeat(5 - nota);
        String msg = "Atendimento avaliado pelo solicitante: " + estrelas + " (" + nota + "/5)"
                + (comentario.isBlank() ? "." : " — \"" + comentario + "\"");
        inserirMensagem(id, user, "EVENTO_SISTEMA", msg);
        return detalhar(id, user);
    }

    @Transactional(readOnly = true)
    public ArquivoAnexo baixarAnexo(long chamadoId, long anexoId, UsuarioAutenticado user) {
        buscarChamado(chamadoId, user);
        var rows = jdbc.queryForList("""
                select nome_arquivo, content_type, conteudo
                from chamado_anexo
                where id = ? and chamado_id = ?
                """, anexoId, chamadoId);
        if (rows.isEmpty()) throw new RecursoNaoEncontradoException("Anexo nao encontrado");
        var r = rows.getFirst();
        return new ArquivoAnexo(
                r.get("nome_arquivo").toString(),
                r.get("content_type").toString(),
                (byte[]) r.get("conteudo")
        );
    }

    private ChamadoItem buscarChamado(long id, UsuarioAutenticado user) {
        List<ChamadoItem> list = jdbc.query(SELECT_CHAMADO_BASE + " where c.id = ?", this::mapearChamado, id);
        if (list.isEmpty()) throw new RecursoNaoEncontradoException("Chamado nao encontrado");
        ChamadoItem item = list.getFirst();
        if ("MEDICO".equals(user.perfil()) && item.solicitanteId() != user.id()) {
            throw new AccessDeniedException("Acesso nao permitido a este chamado");
        }
        if ("OPERADOR".equals(user.perfil()) && item.solicitanteId() != user.id()
                && !Set.of("OPERADOR", "MEDICO").contains(item.solicitantePerfil())) {
            throw new AccessDeniedException("Acesso nao permitido a este chamado");
        }
        return item;
    }

    private long inserirMensagem(long chamadoId, UsuarioAutenticado autor, String tipo, String conteudo) {
        return jdbc.queryForObject("""
                insert into chamado_mensagem (chamado_id, autor_id, autor_nome, autor_perfil, tipo, conteudo)
                values (?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, chamadoId, autor.id(), autor.nome(), autor.perfil(), tipo, conteudo);
    }

    private void salvarAnexos(long chamadoId, long mensagemId, List<ArquivoAnexo> arquivos, UsuarioAutenticado user) {
        for (ArquivoAnexo arq : arquivos) {
            jdbc.update("""
                    insert into chamado_anexo (chamado_id, mensagem_id, usuario_id, usuario_nome, nome_arquivo, content_type, tamanho, conteudo)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, chamadoId, mensagemId, user.id(), user.nome(), arq.nome(), arq.contentType(), arq.conteudo().length, arq.conteudo());
        }
    }

    static List<ArquivoAnexo> validarArquivos(List<MultipartFile> arquivos) throws Exception {
        if (arquivos == null || arquivos.isEmpty()) return List.of();
        List<ArquivoAnexo> validados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) continue;
            if (validados.size() >= 5) throw new IllegalArgumentException("Limite de ate 5 anexos por envio");
            if (arquivo.getSize() > LIMITE_ARQUIVO)
                throw new IllegalArgumentException("Cada anexo deve ter no maximo 5 MB");
            String nome = Objects.toString(arquivo.getOriginalFilename(), "anexo").replace('\\', '/');
            nome = nome.substring(nome.lastIndexOf('/') + 1).trim();
            if (nome.isBlank() || nome.length() > 180 || nome.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Nome do arquivo invalido");
            byte[] bytes;
            try (var in = arquivo.getInputStream()) {
                bytes = in.readNBytes(LIMITE_ARQUIVO + 1);
            }
            if (bytes.length == 0 || bytes.length > LIMITE_ARQUIVO)
                throw new IllegalArgumentException("Arquivo vazio ou maior que 5 MB");
            String lower = nome.toLowerCase(Locale.ROOT);
            String tipo;
            if (lower.endsWith(".png") && inicia(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
                tipo = "image/png";
            } else if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && inicia(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255})) {
                tipo = "image/jpeg";
            } else if (lower.endsWith(".webp") && bytes.length >= 12
                    && inicia(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(bytes, 8, 12, "WEBP".getBytes(StandardCharsets.US_ASCII), 0, 4)) {
                tipo = "image/webp";
            } else if (lower.endsWith(".pdf") && inicia(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                tipo = "application/pdf";
            } else {
                throw new IllegalArgumentException("Formato invalido (" + nome + "). Envie imagens PNG, JPG, WEBP ou documento PDF de ate 5 MB");
            }
            validados.add(new ArquivoAnexo(nome, tipo, bytes));
        }
        return validados;
    }

    private static boolean inicia(byte[] bytes, byte[] prefixo) {
        return bytes.length >= prefixo.length && Arrays.equals(bytes, 0, prefixo.length, prefixo, 0, prefixo.length);
    }

    private static void exigirAtendente(UsuarioAutenticado user) {
        if (!Set.of("ADMIN", "DEV").contains(user.perfil())) {
            throw new AccessDeniedException("Operacao restrita a equipe de suporte e administracao");
        }
    }

    private static String rotuloPerfil(String perfil) {
        return switch (Objects.toString(perfil, "")) {
            case "ADMIN" -> "Administrador tecnico";
            case "DEV" -> "Administrador tecnico";
            case "OPERADOR" -> "Funcionario FaturaMed";
            default -> "Medico";
        };
    }

    private static String rotuloStatus(String status) {
        return switch (Objects.toString(status, "")) {
            case "ABERTO" -> "Aberto (Triagem)";
            case "EM_ATENDIMENTO" -> "Em Atendimento";
            case "AGUARDANDO_USUARIO" -> "Aguardando Usuario";
            case "RESOLVIDO" -> "Resolvido";
            case "FECHADO" -> "Fechado";
            default -> status;
        };
    }

    private static final String SELECT_CHAMADO_BASE = """
            select c.*, t.nome as tipo_nome,
                   (select count(*) from chamado_mensagem m where m.chamado_id = c.id) as total_mensagens,
                   (select count(*) from chamado_anexo a where a.chamado_id = c.id) as total_anexos
            from chamado_suporte c
            join chamado_tipo_sla t on t.codigo = c.tipo_codigo
            """;

    private ChamadoItem mapearChamado(java.sql.ResultSet r, int rowNum) throws java.sql.SQLException {
        String status = r.getString("status");
        int slaHoras = r.getInt("sla_horas");
        Instant criadoEm = r.getTimestamp("criado_em").toInstant();
        Instant prazoSlaEm = r.getTimestamp("prazo_sla_em").toInstant();
        Instant primeiroAtendimentoEm = r.getTimestamp("primeiro_atendimento_em") == null ? null : r.getTimestamp("primeiro_atendimento_em").toInstant();
        Instant resolvidoEm = r.getTimestamp("resolvido_em") == null ? null : r.getTimestamp("resolvido_em").toInstant();
        Instant fechadoEm = r.getTimestamp("fechado_em") == null ? null : r.getTimestamp("fechado_em").toInstant();
        Instant avaliadoEm = r.getTimestamp("avaliado_em") == null ? null : r.getTimestamp("avaliado_em").toInstant();
        Instant atualizadoEm = r.getTimestamp("atualizado_em").toInstant();

        Instant agora = Instant.now();
        Instant fimRef = resolvidoEm != null ? resolvidoEm : (fechadoEm != null ? fechadoEm : agora);
        long minutosRestantes = Duration.between(fimRef, prazoSlaEm).toMinutes();
        String slaStatus;
        if ("RESOLVIDO".equals(status) || "FECHADO".equals(status)) {
            slaStatus = fimRef.isAfter(prazoSlaEm) ? "FORA_DO_PRAZO" : "CUMPRIDO";
        } else {
            long totalMinutos = Math.max(60L, slaHoras * 60L);
            if (minutosRestantes < 0) {
                slaStatus = "VENCIDO";
            } else if (minutosRestantes <= Math.max(30L, totalMinutos / 4)) {
                slaStatus = "EM_ALERTA";
            } else {
                slaStatus = "NO_PRAZO";
            }
        }

        return new ChamadoItem(
                r.getLong("id"),
                r.getString("protocolo"),
                r.getLong("solicitante_id"),
                r.getString("solicitante_nome"),
                r.getString("solicitante_email"),
                r.getString("solicitante_perfil"),
                r.getString("tipo_codigo"),
                r.getString("tipo_nome"),
                r.getString("fila_destino"),
                r.getString("prioridade"),
                status,
                r.getString("titulo"),
                r.getString("descricao"),
                r.getString("pagina_origem"),
                r.getObject("responsavel_id", Long.class),
                r.getString("responsavel_nome"),
                slaHoras,
                prazoSlaEm,
                primeiroAtendimentoEm,
                resolvidoEm,
                fechadoEm,
                r.getObject("nota_avaliacao", Integer.class),
                r.getString("comentario_avaliacao"),
                avaliadoEm,
                criadoEm,
                atualizadoEm,
                r.getLong("versao"),
                slaStatus,
                minutosRestantes,
                r.getLong("total_mensagens"),
                r.getLong("total_anexos")
        );
    }
}
