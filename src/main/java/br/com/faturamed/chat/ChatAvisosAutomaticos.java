package br.com.faturamed.chat;

import br.com.faturamed.conciliacao.FechamentoFaturadoEventos;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisos automaticos do "Robo FaturaMed" para a equipe interna (ADMIN, DEV e OPERADOR):
 * planilha de hospital importada, conciliacao gerada com divergencias e lotes de producao parados.
 * Quem fez a acao nao recebe o proprio aviso. Os textos usam os atalhos do chat (#relatorio, #lote),
 * que viram cartoes clicaveis.
 */
@Component
public class ChatAvisosAutomaticos {
    private static final Logger log = LoggerFactory.getLogger(ChatAvisosAutomaticos.class);
    private final JdbcTemplate jdbc;
    private final ChatService chat;

    public ChatAvisosAutomaticos(JdbcTemplate jdbc, ChatService chat) { this.jdbc = jdbc; this.chat = chat; }

    private List<Long> equipe(long exceto) {
        return jdbc.queryForList("""
                select id from usuario_acesso where ativo and perfil in ('ADMIN','DEV','OPERADOR')
                and email <> ? and id <> ? order by id""", Long.class, ChatService.EMAIL_ROBO, exceto);
    }

    private void avisar(long exceto, String texto) {
        for (long id : equipe(exceto)) {
            try { chat.avisarSistema(id, texto); }
            catch (RuntimeException e) { log.warn("Aviso do Robo nao entregue ao usuario {}: {}", id, e.getMessage()); }
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void planilhaImportada(FechamentoFaturadoEventos.PlanilhaHospitalImportada e) {
        avisar(e.usuarioId(), "📄 Planilha do " + e.hospital() + " importada (competência " + e.competencia() + "): "
                + e.procedimentos() + " procedimento(s) na base hospitalar.");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conciliacaoGerada(FechamentoFaturadoEventos.ConciliacaoGerada e) {
        if (e.divergencias() <= 0) return; // so avisa quando ha o que conferir
        avisar(e.usuarioId(), "⚠️ Conciliação do " + e.hospital() + " gerada com " + e.divergencias() + " divergência(s) em "
                + e.procedimentos() + " procedimento(s): #relatorio " + e.relatorioId());
    }

    /** Dias uteis de manha: lotes em rascunho ha mais de 3 dias. */
    @Scheduled(cron = "${faturamed.chat.lotes-parados-cron:0 30 8 * * MON-FRI}")
    @Transactional
    public void lotesParados() {
        var parados = jdbc.queryForList("""
                select id from producao_lote where status = 'RASCUNHO' and data_producao < current_date - 3
                order by data_producao, id limit 5""", Long.class);
        if (parados.isEmpty()) return;
        Long total = jdbc.queryForObject("select count(*) from producao_lote where status = 'RASCUNHO' and data_producao < current_date - 3", Long.class);
        StringBuilder texto = new StringBuilder("⏳ " + total + " lote(s) de produção parado(s) em rascunho há mais de 3 dias. Os mais antigos:");
        for (Long id : parados) texto.append(" #lote ").append(id);
        avisar(0, texto.toString());
    }
}
