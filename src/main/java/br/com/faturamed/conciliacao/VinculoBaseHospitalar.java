package br.com.faturamed.conciliacao;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VinculoBaseHospitalar {
    private final JdbcTemplate jdbc;
    public VinculoBaseHospitalar(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void bloquear(long hospitalId) {
        jdbc.queryForList("select pg_advisory_xact_lock(182018, hashtext(?))",Long.toString(hospitalId));
    }

    public Set<Integer> indisponiveis(long hospitalId) {
        return new HashSet<>(jdbc.queryForList("""
                select v.id from visita_base_hospitalar v where v.hospital_id=? and
                (exists(select 1 from reserva_base_hospitalar b where b.visita_id=v.id)
                 or exists(select 1 from visita_base_hospitalar repetida where repetida.anterior_id=v.id and repetida.decisao='EM_ANALISE'))
                """,Integer.class,hospitalId));
    }

    public void sincronizar(long relatorioId, ConciliadorVisitas.Relatorio relatorio) {
        var arquivos=jdbc.queryForList("select hospital_id,medico_id from arquivo_base_hospitalar where relatorio_id=?",relatorioId);
        if(arquivos.isEmpty()) return;
        long hospitalId=((Number)arquivos.getFirst().get("hospital_id")).longValue();
        Long medicoId=arquivos.getFirst().get("medico_id")==null?null:((Number)arquivos.getFirst().get("medico_id")).longValue();
        bloquear(hospitalId);
        long raiz=raiz(relatorioId);
        var cadastroService=new MedicoCadastroService(jdbc);
        var cadastros=medicoId==null?cadastroService.listar():List.<MedicoCadastroService.Medico>of();
        Set<Integer> usados=new HashSet<>();
        for(var visita:relatorio.visitas()) {
            if(visita.hospital()==null) continue;
            int visitaId=visita.hospital().linha();
            var registros=jdbc.queryForList("select hospital_id,decisao from visita_base_hospitalar where id=?",visitaId);
            if(registros.isEmpty() || ((Number)registros.getFirst().get("hospital_id")).longValue()!=hospitalId
                    || !Set.of("ORIGINAL","VISITA_DISTINTA").contains(registros.getFirst().get("decisao")))
                throw new ConflitoRevisaoException("Visita hospitalar indisponivel. Confira a base");
            if(!usados.add(visitaId)) throw new ConflitoRevisaoException("A mesma visita hospitalar nao pode atender duas linhas de producao");
            var reservas=jdbc.queryForList("select relatorio_raiz_id,linha_producao from reserva_base_hospitalar where visita_id=?",visitaId);
            if(!reservas.isEmpty() && (((Number)reservas.getFirst().get("relatorio_raiz_id")).longValue()!=raiz
                    || ((Number)reservas.getFirst().get("linha_producao")).intValue()!=visita.linhaMedico()))
                throw new ConflitoRevisaoException("Visita ID "+visitaId+" ja vinculada ao historico de outra linha ou producao");
            var ocupadas=jdbc.queryForList("select relatorio_id,linha_producao from vinculo_base_hospitalar where visita_id=?",visitaId);
            boolean mesmoVinculo=!ocupadas.isEmpty() && ((Number)ocupadas.getFirst().get("relatorio_id")).longValue()==relatorioId
                    && ((Number)ocupadas.getFirst().get("linha_producao")).intValue()==visita.linhaMedico();
            if(!ocupadas.isEmpty() && !mesmoVinculo) throw new ConflitoRevisaoException("Visita ID "+visitaId+" ja vinculada a outra linha ou producao. Analise a duplicidade");
            if(!mesmoVinculo && !jdbc.queryForList("select id from visita_base_hospitalar where anterior_id=? and decisao='EM_ANALISE'",visitaId).isEmpty())
                throw new ConflitoRevisaoException("Resolva a possivel duplicidade na base hospitalar antes de vincular esta visita");
            if(medicoId!=null && visita.medicoCadastroId()!=null && !visita.medicoCadastroId().equals(medicoId))
                throw new IllegalArgumentException("O medico deve corresponder ao selecionado na importacao da producao");
            if(medicoId==null) {
                Long efetivo=cadastroService.identificar(visita,cadastros);
                Long origem=cadastroService.identificarNome(visita.original().getOrDefault("medico",""),cadastros);
                if(efetivo==null || !efetivo.equals(cadastroService.identificarNome(visita.hospital().medico(),cadastros))
                        || visita.camposRevisados()==null && origem!=null && !origem.equals(efetivo))
                    throw new IllegalArgumentException("Identifique o medico cadastrado da visita e confira o medico hospitalar antes de vincular a producao coletiva");
            }
        }
        jdbc.update("delete from vinculo_base_hospitalar where relatorio_id=?",relatorioId);
        for(var visita:relatorio.visitas()) if(visita.hospital()!=null) {
            jdbc.update("insert into vinculo_base_hospitalar(visita_id,relatorio_id,linha_producao) values(?,?,?)",visita.hospital().linha(),relatorioId,visita.linhaMedico());
            jdbc.update("insert into reserva_base_hospitalar(visita_id,relatorio_raiz_id,linha_producao) values(?,?,?) on conflict(visita_id) do nothing",visita.hospital().linha(),raiz,visita.linhaMedico());
        }
    }

    public Set<Integer> reservadas(long relatorioId) {
        return new HashSet<>(jdbc.queryForList("select visita_id from reserva_base_hospitalar where relatorio_raiz_id=?",Integer.class,raiz(relatorioId)));
    }
    private long raiz(long id) {
        return jdbc.queryForObject("""
                with recursive cadeia as (select id,anterior_id from conciliacao_visitas where id=?
                union all select r.id,r.anterior_id from conciliacao_visitas r join cadeia c on r.id=c.anterior_id)
                select id from cadeia where anterior_id is null
                """,Long.class,id);
    }
}
