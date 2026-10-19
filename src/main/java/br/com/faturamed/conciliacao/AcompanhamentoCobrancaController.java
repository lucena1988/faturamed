package br.com.faturamed.conciliacao;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.faturamed.seguranca.UsuarioAutenticado;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/cobranca")
public class AcompanhamentoCobrancaController {
    private final AcompanhamentoCobrancaService service;
    public AcompanhamentoCobrancaController(AcompanhamentoCobrancaService service) { this.service=service; }
    @PostMapping("/lote")
    public AcompanhamentoCobrancaService.ResultadoLote registrarLote(@PathVariable long id,
            @Valid @RequestBody CobrancaLoteRequest request,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return service.registrarLote(id,request,usuario);
    }
    @GetMapping("/acompanhamento")
    public List<AcompanhamentoCobrancaService.Resumo> listar(@PathVariable long id) throws Exception { return service.listar(id); }
    @GetMapping("/visitas/{linha}")
    public AcompanhamentoCobrancaService.Detalhe detalhar(@PathVariable long id,@PathVariable int linha) throws Exception { return service.detalhar(id,linha); }
    @PostMapping("/visitas/{linha}")
    public AcompanhamentoCobrancaService.Detalhe registrar(@PathVariable long id,@PathVariable int linha,
            @Valid @RequestBody ContatoCobrancaRequest request,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return service.registrar(id,linha,request,usuario);
    }
}
