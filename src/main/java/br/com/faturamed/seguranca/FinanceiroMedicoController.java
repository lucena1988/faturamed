package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.RecebimentoService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medico/relatorios/{id}/recebimentos")
public class FinanceiroMedicoController {
    public record Saldo(int linha,BigDecimal recebido,BigDecimal saldo,boolean inconsistente) {}
    private final PortalMedicoController portal;
    private final RecebimentoService recebimentos;
    public FinanceiroMedicoController(PortalMedicoController portal,RecebimentoService recebimentos) { this.portal=portal;this.recebimentos=recebimentos; }
    @GetMapping public List<Saldo> listar(@PathVariable long id,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        var autorizado=portal.buscar(id,user);Set<Integer> linhas=new HashSet<>();autorizado.visitas().forEach(v->linhas.add(v.linhaMedico()));
        return recebimentos.listar(id).visitas().stream().filter(v->linhas.contains(v.linha())&&Objects.equals(v.medicoId(),user.medicoId()))
                .map(v->new Saldo(v.linha(),v.inconsistente()?null:v.recebido(),v.inconsistente()?null:v.saldo(),v.inconsistente())).toList();
    }
}
