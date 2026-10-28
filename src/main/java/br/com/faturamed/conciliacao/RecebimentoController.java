package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/recebimentos")
public class RecebimentoController {
    private final RecebimentoService service;
    public RecebimentoController(RecebimentoService service) { this.service=service; }
    @GetMapping public RecebimentoService.Resultado listar(@PathVariable long id) throws Exception { return service.listar(id); }
    @PostMapping("/pagamentos") public RecebimentoService.PagamentoRegistrado distribuir(@PathVariable long id,
            @Valid @RequestBody PagamentoDistribuidoRequest dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception { return service.distribuir(id,dados,user); }
    @PostMapping("/visitas/{linha}") public RecebimentoService.Resultado registrar(@PathVariable long id,@PathVariable int linha,
            @Valid @RequestBody RecebimentoService.Dados dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception { return service.registrar(id,linha,dados,user); }
    @PostMapping("/visitas/{linha}/{evento}/estorno") public RecebimentoService.Resultado estornar(@PathVariable long id,@PathVariable int linha,@PathVariable long evento,
            @Valid @RequestBody RecebimentoService.Estorno dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception { return service.estornar(id,linha,evento,dados,user); }
}
