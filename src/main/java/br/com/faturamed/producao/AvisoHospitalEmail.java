package br.com.faturamed.producao;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Avisa por e-mail os usuarios do hospital (perfil HOSPITAL) quando um fechamento chega para conferencia
 * e quando a NF e emitida. Sem SMTP configurado (MAIL_HOST) apenas registra no log.
 * O envio acontece depois do commit, para nao avisar sobre algo que foi desfeito.
 */
@Component
public class AvisoHospitalEmail {
    private static final Logger LOG = LoggerFactory.getLogger(AvisoHospitalEmail.class);

    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> email;
    private final String servidor;
    private final String remetente;
    private final String urlPublica;

    public AvisoHospitalEmail(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> email, @Value("${spring.mail.host:}") String servidor,
            @Value("${faturamed.recuperacao-senha.remetente:}") String remetente, @Value("${faturamed.url-publica:http://localhost:8080}") String urlPublica) {
        this.jdbc = jdbc; this.email = email; this.servidor = servidor; this.remetente = remetente;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    public void fechamentoEnviado(long fechamentoId) {
        var f = dados(fechamentoId);
        enviar(f.hospitalId(), "FaturaMed - fechamento nº " + fechamentoId + " aguardando sua conferência", """
                Olá.

                A FaturaMed enviou o fechamento de produção nº %d (%s a %s), no valor de %s, para conferência do %s.
                Prazo para conferência: %s.

                Acesse o portal para conferir lote a lote, contestar o que não reconhecer e liberar para a emissão da nota fiscal:
                %s/hospital.html?fechamento=%d
                """.formatted(fechamentoId, f.inicio(), f.fim(), f.total(), f.hospital(), f.prazo(), urlPublica, fechamentoId));
    }

    public void lembretePrazo(long fechamentoId, java.time.LocalDate prazo) {
        var f = dados(fechamentoId);
        enviar(f.hospitalId(), "FaturaMed - lembrete: conferência do fechamento nº " + fechamentoId + " vence em " + prazo.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), """
                Olá.

                O prazo para conferir o fechamento de produção nº %d (%s a %s), no valor de %s, vence em %s.

                Se já conferiu tudo, basta aprovar e liberar para a emissão da nota fiscal no portal:
                %s/hospital.html?fechamento=%d
                """.formatted(fechamentoId, f.inicio(), f.fim(), f.total(), prazo.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), urlPublica, fechamentoId));
    }

    public void nfEmitida(long fechamentoId) {
        var f = dados(fechamentoId);
        enviar(f.hospitalId(), "FaturaMed - NF " + f.nf() + " emitida (fechamento nº " + fechamentoId + ")", """
                Olá.

                A nota fiscal %s referente ao fechamento de produção nº %d (%s a %s), no valor de %s, foi emitida.

                Veja os detalhes%s no portal:
                %s/hospital.html?fechamento=%d
                """.formatted(f.nf(), fechamentoId, f.inicio(), f.fim(), f.total(), f.temArquivo() ? " e baixe a nota" : "", urlPublica, fechamentoId));
    }

    private record Dados(long hospitalId, String hospital, String inicio, String fim, String total, String nf, boolean temArquivo, String prazo) {}

    private Dados dados(long id) {
        return jdbc.queryForObject("""
                select f.hospital_id, h.nome, to_char(f.data_inicio,'DD/MM/YYYY') ini, to_char(f.data_fim,'DD/MM/YYYY') fim,
                       f.total, coalesce(f.nf_numero,'') nf, (f.nf_arquivo is not null) arquivo, coalesce(to_char(f.prazo_conferencia,'DD/MM/YYYY'),'—') prazo
                from producao_fechamento f join hospital_cadastro h on h.id=f.hospital_id where f.id=?""",
                (r, n) -> new Dados(r.getLong(1), r.getString(2), r.getString(3), r.getString(4),
                        java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(r.getBigDecimal(5)),
                        r.getString(6), r.getBoolean(7), r.getString(8)), id);
    }

    private void enviar(long hospitalId, String assunto, String texto) {
        List<String> destinos = jdbc.queryForList("select email from usuario_acesso where perfil='HOSPITAL' and ativo and hospital_id=?", String.class, hospitalId);
        Runnable envio = () -> {
            if (destinos.isEmpty()) { LOG.info("Aviso ao hospital {}: nenhum usuario do portal cadastrado ({})", hospitalId, assunto); return; }
            var sender = servidor.isBlank() ? null : email.getIfAvailable();
            if (sender == null) { LOG.info("Aviso ao hospital {} nao enviado (SMTP nao configurado): {}", hospitalId, assunto); return; }
            for (String destino : destinos) {
                var m = new SimpleMailMessage();
                if (!remetente.isBlank()) m.setFrom(remetente);
                m.setTo(destino);
                m.setSubject(assunto);
                m.setText(texto);
                try { sender.send(m); } catch (MailException e) { LOG.error("Aviso ao hospital {}: falha ao enviar para um usuario: {}", hospitalId, e.getMessage()); }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { envio.run(); }
            });
        else envio.run();
    }
}
