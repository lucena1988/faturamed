package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medico/relatorios")
public class PortalMedicoController {
    public record Item(long id, String hospital, long visitas) {}
    public record Visita(int linhaMedico,String atendimento,LocalDate data,ConciliadorVisitas.Status status,String motivo,
            String medico,long medicoCadastroId,String procedimento,String codigoProcedimento,BigDecimal repasse) {}
    public record Relatorio(long id,String hospital,List<Visita> visitas) {}
    private final LiberacaoRelatorioService liberacoes;
    public PortalMedicoController(LiberacaoRelatorioService liberacoes) { this.liberacoes=liberacoes; }
    @GetMapping public List<Item> listar(@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        return liberacoes.listarDoMedico(user.medicoId());
    }
    @GetMapping("/{id}") public Relatorio buscar(@PathVariable long id,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        // The account's doctor ID is authoritative; never accept it from a query parameter.
        return liberacoes.buscarDoMedico(id,user.medicoId());
    }
    static Visita sanitizar(ConciliadorVisitas.Visita visita,MedicoCadastroService.Medico medico) {
        return new Visita(visita.linhaMedico(),visita.atendimento(),visita.data(),visita.status(),visita.motivo(),
                medico.nome(),medico.id(),visita.procedimentoEfetivo(),visita.codigoEfetivo(),visita.repasseEfetivo());
    }
}
