package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclui uma planilha do hospital e os procedimentos que ela criou, para corrigir importacoes
 * feitas na competencia errada. So e permitido enquanto nada depende desses procedimentos:
 * sem vinculo ou reserva com producao, sem atualizacao por outro arquivo e sem repeticoes apontando para eles.
 */
@Service
public class ExclusaoArquivoHospitalService {
    public record Resultado(long arquivoId,String competencia,int procedimentos) {}
    private final JdbcTemplate jdbc;
    private final VinculoBaseHospitalar vinculos;
    public ExclusaoArquivoHospitalService(JdbcTemplate jdbc,VinculoBaseHospitalar vinculos) { this.jdbc=jdbc; this.vinculos=vinculos; }

    @Transactional
    public Resultado excluir(long arquivoId,UsuarioAutenticado usuario) {
        var lista=jdbc.queryForList("select id,hospital_id,tipo,competencia,nome from arquivo_base_hospitalar where id=?",arquivoId);
        if(lista.isEmpty()) throw new RecursoNaoEncontradoException("Arquivo nao encontrado");
        Map<String,Object> arquivo=lista.getFirst();
        if(!"HOSPITAL".equals(arquivo.get("tipo"))) throw new IllegalArgumentException("Para excluir uma producao, exclua o relatorio de conciliacao correspondente");
        long hospitalId=((Number)arquivo.get("hospital_id")).longValue();
        vinculos.bloquear(hospitalId);
        String daPlanilha="(select id from visita_base_hospitalar where arquivo_id=?)";
        bloquearSe("select count(*) from vinculo_base_hospitalar where visita_id in "+daPlanilha,arquivoId,
                "procedimentos desta planilha ja estao conciliados com uma producao. Exclua antes o relatorio de conciliacao");
        bloquearSe("select count(*) from reserva_base_hospitalar where visita_id in "+daPlanilha,arquivoId,
                "procedimentos desta planilha estao reservados por uma producao. Exclua antes o relatorio de conciliacao");
        bloquearSe("select count(*) from visita_base_hospitalar where arquivo_id=? and arquivo_atual_id<>arquivo_id",arquivoId,
                "procedimentos desta planilha foram atualizados por outra planilha. Exclua antes a planilha de atualizacao");
        bloquearSe("select count(*) from visita_base_hospitalar where arquivo_id<>? and anterior_id in "+daPlanilha,arquivoId,arquivoId,
                "outras planilhas tem repeticoes apontando para procedimentos desta. Exclua antes essas planilhas");
        bloquearSe("select count(*) from atualizacao_visita_hospitalar where arquivo_id=?",arquivoId,
                "esta planilha atualizou procedimentos de importacoes anteriores e nao pode ser desfeita automaticamente");
        int procedimentos=jdbc.queryForObject("select count(*) from visita_base_hospitalar where arquivo_id=?",Integer.class,arquivoId);
        jdbc.update("delete from atualizacao_visita_hospitalar where visita_id in "+daPlanilha,arquivoId);
        jdbc.update("delete from revisao_base_hospitalar where visita_id in "+daPlanilha,arquivoId);
        jdbc.update("delete from visita_base_hospitalar where arquivo_id=?",arquivoId);
        jdbc.update("delete from arquivo_base_hospitalar where id=?",arquivoId);
        jdbc.update("""
            insert into arquivo_base_exclusao_auditoria(arquivo_id,hospital_id,competencia,nome,procedimentos,usuario_id,responsavel)
            values(?,?,?,?,?,?,?)
            """,arquivoId,hospitalId,arquivo.get("competencia"),arquivo.get("nome"),procedimentos,usuario.id(),usuario.nome()+" ("+usuario.email()+")");
        return new Resultado(arquivoId,(String)arquivo.get("competencia"),procedimentos);
    }

    private void bloquearSe(String sql,Object arquivoId,String motivo) { bloquearSe(sql,arquivoId,null,motivo); }
    private void bloquearSe(String sql,Object a,Object b,String motivo) {
        Integer total=b==null?jdbc.queryForObject(sql,Integer.class,a):jdbc.queryForObject(sql,Integer.class,a,b);
        if(total!=null && total>0) throw new IllegalArgumentException("Nao foi possivel excluir: "+total+" "+motivo+".");
    }
}
