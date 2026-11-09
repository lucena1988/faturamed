package br.com.faturamed.producao;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotinas de protecao de dados da producao:
 *  - ao subir, criptografa os registros antigos que ainda estao em claro (quando a chave esta configurada);
 *  - todo dia, descarta a foto da folha N dias depois da NF emitida (faturamed.producao.guarda-foto-dias, padrao 90).
 */
@Component
public class ProtecaoDadosRotinas {
    private static final Logger LOG = LoggerFactory.getLogger(ProtecaoDadosRotinas.class);
    private final JdbcTemplate jdbc;
    private final CriptografiaDados cripto;
    private final HistoricoProducao historico;
    private final int guardaFotoDias;

    public ProtecaoDadosRotinas(JdbcTemplate jdbc, CriptografiaDados cripto, HistoricoProducao historico,
            @Value("${faturamed.producao.guarda-foto-dias:90}") int guardaFotoDias) {
        this.jdbc = jdbc; this.cripto = cripto; this.historico = historico; this.guardaFotoDias = Math.max(1, guardaFotoDias);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void criptografarRegistrosAntigos() {
        if (!cripto.ativa()) return;
        int etiquetas = 0, fotos = 0;
        List<Map<String, Object>> itens = jdbc.queryForList("""
                select id, carteirinha, guia, senha from producao_item
                where (carteirinha <> '' and carteirinha not like 'enc:v1:%') or (guia <> '' and guia not like 'enc:v1:%')
                   or (senha <> '' and senha not like 'enc:v1:%')""");
        for (Map<String, Object> i : itens) {
            jdbc.update("update producao_item set carteirinha=?, guia=?, senha=? where id=?", cripto.cifrar((String) i.get("carteirinha")),
                    cripto.cifrar((String) i.get("guia")), cripto.cifrar((String) i.get("senha")), i.get("id"));
            etiquetas++;
        }
        for (Long id : jdbc.queryForList("select id from producao_lote where foto is not null", Long.class)) {
            byte[] foto = jdbc.queryForObject("select foto from producao_lote where id=?", byte[].class, id);
            if (foto != null && !CriptografiaDados.ehCifrado(foto)) {
                jdbc.update("update producao_lote set foto=? where id=?", cripto.cifrarArquivo(foto), id);
                fotos++;
            }
        }
        if (etiquetas + fotos > 0) LOG.info("Protecao de dados: {} etiqueta(s) e {} foto(s) antigas criptografadas", etiquetas, fotos);
    }

    /** Fotos das folhas so ficam guardadas ate N dias depois da NF; os dados das etiquetas continuam no lote. */
    @Scheduled(cron = "${faturamed.producao.guarda-foto-cron:0 30 2 * * *}")
    public int descartarFotosVencidas() {
        List<Long> lotes = jdbc.queryForList("""
                select l.id from producao_lote l
                where l.foto is not null and l.status='ENVIADO'
                  and not exists (select 1 from producao_fechamento_item fi join producao_item i on i.id=fi.item_id
                                  join producao_fechamento f on f.id=fi.fechamento_id
                                  where i.lote_id=l.id and fi.situacao='INCLUIDA'
                                    and (f.status <> 'NF_EMITIDA' or f.nf_data > current_date - ?::int))""", Long.class, guardaFotoDias);
        for (Long id : lotes) {
            jdbc.update("update producao_lote set foto=null, foto_tipo=null, foto_removida_em=now() where id=?", id);
            historico.lote(id, "FOTO_DESCARTADA", "Foto da folha descartada pela política de guarda (" + guardaFotoDias + " dias após a NF)", "sistema");
        }
        if (!lotes.isEmpty()) LOG.info("Protecao de dados: {} foto(s) de folha descartada(s) pela politica de guarda", lotes.size());
        return lotes.size();
    }
}
