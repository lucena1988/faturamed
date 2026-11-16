package br.com.faturamed.producao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prazo de conferencia do hospital: N dias uteis (faturamed.producao.prazo-conferencia-dias, padrao 5) a partir do envio.
 * Todo dia as 8h envia um lembrete aos usuarios do hospital quando falta 1 dia util ou menos (uma vez por fechamento).
 */
@Configuration
@EnableScheduling
public class PrazoConferencia {
    private static final Logger LOG = LoggerFactory.getLogger(PrazoConferencia.class);

    private final JdbcTemplate jdbc;
    private final AvisoHospitalEmail aviso;
    private final int dias;

    public PrazoConferencia(JdbcTemplate jdbc, AvisoHospitalEmail aviso, @Value("${faturamed.producao.prazo-conferencia-dias:5}") int dias) {
        this.jdbc = jdbc; this.aviso = aviso; this.dias = Math.max(1, dias);
    }

    public int dias() { return dias; }

    public LocalDate calcular(LocalDate envio) { return somarDiasUteis(envio, dias); }

    static LocalDate somarDiasUteis(LocalDate inicio, int n) {
        LocalDate d = inicio;
        int contados = 0;
        while (contados < n) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) contados++;
        }
        return d;
    }

    /** Dias uteis entre hoje e o prazo (negativo = atrasado). */
    static int diasUteisAte(LocalDate hoje, LocalDate prazo) {
        if (prazo == null) return Integer.MAX_VALUE;
        int sinal = prazo.isBefore(hoje) ? -1 : 1;
        LocalDate a = sinal > 0 ? hoje : prazo, b = sinal > 0 ? prazo : hoje;
        int n = 0;
        for (LocalDate d = a; d.isBefore(b); d = d.plusDays(1)) {
            LocalDate prox = d.plusDays(1);
            if (prox.getDayOfWeek() != DayOfWeek.SATURDAY && prox.getDayOfWeek() != DayOfWeek.SUNDAY) n++;
        }
        return sinal * n;
    }

    @Scheduled(cron = "${faturamed.producao.lembrete-cron:0 0 8 * * MON-FRI}")
    @Transactional
    public void enviarLembretes() {
        LocalDate hoje = LocalDate.now();
        List<java.util.Map<String, Object>> abertos = jdbc.queryForList(
                "select id, prazo_conferencia from producao_fechamento where status='AGUARDANDO' and lembrete_prazo_em is null and prazo_conferencia is not null");
        for (var f : abertos) {
            LocalDate prazo = ((java.sql.Date) f.get("prazo_conferencia")).toLocalDate();
            if (diasUteisAte(hoje, prazo) <= 1) {
                long id = ((Number) f.get("id")).longValue();
                aviso.lembretePrazo(id, prazo);
                jdbc.update("update producao_fechamento set lembrete_prazo_em=now() where id=?", id);
                LOG.info("Lembrete de prazo de conferencia enviado para o fechamento {}", id);
            }
        }
    }
}
