package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/medico/relatorios")
public class PortalMedicoController {
    public record Item(long id, String hospital, long visitas) {}
    public record Visita(int linhaMedico,String atendimento,LocalDate data,ConciliadorVisitas.Status status,String motivo,
            String medico,long medicoCadastroId,String procedimento,String codigoProcedimento,BigDecimal repasse) {}
    public record Relatorio(long id,String hospital,List<Visita> visitas) {}
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    private final MedicoCadastroService medicos;
    public PortalMedicoController(JdbcTemplate jdbc,RevisaoVisitasService revisoes,MedicoCadastroService medicos) { this.jdbc=jdbc; this.revisoes=revisoes; this.medicos=medicos; }
    @GetMapping public List<Item> listar(@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        var cadastro=medicos.buscar(user.medicoId());
        var cadastros=medicos.listar();
        var ids=jdbc.queryForList("select id from conciliacao_visitas c where not exists(select 1 from conciliacao_visitas s where s.anterior_id=c.id) order by id desc limit 100",Long.class);
        var result=new ArrayList<Item>();
        for(long id:ids) {
            var base=revisoes.carregar(id).atual();
            long count=base.visitas().stream().filter(v->Objects.equals(medicos.identificar(v,cadastros),cadastro.id())).count();
            if(count>0) result.add(new Item(id,base.hospital(),count));
        }
        return result;
    }
    @GetMapping("/{id}") public Relatorio buscar(@PathVariable long id,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        // The account's doctor ID is authoritative; never accept it from a query parameter.
        var cadastro=medicos.buscar(user.medicoId());
        var cadastros=medicos.listar();
        var base=revisoes.carregar(id).atual();
        var visits=base.visitas().stream().filter(v->Objects.equals(medicos.identificar(v,cadastros),cadastro.id()))
                .map(v->sanitizar(v,cadastro)).toList();
        if(visits.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Relatorio nao encontrado");
        return new Relatorio(id,base.hospital(),visits);
    }
    static Visita sanitizar(ConciliadorVisitas.Visita visita,MedicoCadastroService.Medico medico) {
        return new Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),visita.status(),visita.motivo(),
                medico.nome(),medico.id(),visita.procedimentoEfetivo(),visita.codigoEfetivo(),visita.repasseEfetivo());
    }
}
