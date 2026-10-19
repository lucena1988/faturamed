package br.com.faturamed.conciliacao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AtualizacaoHospitalarService {
    private record Identidade(String atendimento,LocalDate data,Long medico,String codigo,String procedimento,String conta) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MedicoCadastroService medicos;
    public AtualizacaoHospitalarService(JdbcTemplate jdbc,ObjectMapper json,MedicoCadastroService medicos) {
        this.jdbc=jdbc; this.json=json; this.medicos=medicos;
    }

    public boolean aplicar(long hospitalId,long arquivoId,LeitorPlanilhaVisitas.Linha origem,
            ConciliadorVisitas.RegistroHospital registro,List<ConciliadorVisitas.RegistroHospital> recebidos,
            List<MedicoCadastroService.Medico> cadastros,long usuarioId) throws Exception {
        var identidade=identidade(registro,cadastros);
        if(identidade.medico()==null || identidade.codigo().isBlank()) return false;
        if(recebidos.stream().filter(item->identidade(item,cadastros).equals(identidade)).count()!=1) return false;
        var anteriores=jdbc.queryForList("select id,campos::text from visita_base_hospitalar where hospital_id=? and atendimento=? and data_visita=? and decisao in ('ORIGINAL','VISITA_DISTINTA') order by id",hospitalId,registro.atendimento(),registro.data());
        List<Map<String,Object>> compativeis=new ArrayList<>();
        for(var anterior:anteriores) {
            var campos=json.readValue(anterior.get("campos").toString(),new TypeReference<Map<String,String>>(){});
            var linha=new LeitorPlanilhaVisitas.Linha(((Number)anterior.get("id")).intValue(),campos);
            var salvo=new ConciliadorVisitas().conciliar("Base","","",List.of(),List.of(linha)).hospitalSemProducao().getFirst();
            if(identidade(salvo,cadastros).equals(identidade)) compativeis.add(anterior);
        }
        if(compativeis.size()!=1) return false;
        int visitaId=((Number)compativeis.getFirst().get("id")).intValue();
        if(!jdbc.queryForList("select id from visita_base_hospitalar where anterior_id=? and decisao='EM_ANALISE'",visitaId).isEmpty()) return false;
        String depois=json.writeValueAsString(origem.campos());
        jdbc.update("insert into atualizacao_visita_hospitalar(visita_id,arquivo_id,linha,usuario_id,antes,depois) values(?,?,?,?,?::jsonb,?::jsonb)",
                visitaId,arquivoId,origem.numero(),usuarioId,compativeis.getFirst().get("campos").toString(),depois);
        jdbc.update("update visita_base_hospitalar set campos=?::jsonb,medico_nome=?,arquivo_atual_id=?,linha_atual=? where id=?",
                depois,registro.medico(),arquivoId,origem.numero(),visitaId);
        return true;
    }

    private Identidade identidade(ConciliadorVisitas.RegistroHospital registro,List<MedicoCadastroService.Medico> cadastros) {
        return new Identidade(registro.atendimento(),registro.data(),medicos.identificarNome(registro.medico(),cadastros),
                registro.codigo(),LeitorPlanilhaVisitas.normalizar(registro.procedimento()),registro.conta());
    }
}
