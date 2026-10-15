package br.com.faturamed.seguranca;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/notificacoes")
public class NotificacaoController {
    private final NotificacaoService service;
    public NotificacaoController(NotificacaoService service){this.service=service;}
    @GetMapping public NotificacaoService.Caixa listar(@AuthenticationPrincipal UsuarioAutenticado user,
        @RequestParam(defaultValue="false") boolean naoLidas,@RequestParam(defaultValue="0") int pagina){return service.listar(user,naoLidas,pagina);}
    @PostMapping("/{tipo}/{evento}/leitura")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void ler(@AuthenticationPrincipal UsuarioAutenticado user,@PathVariable String tipo,@PathVariable long evento){service.ler(user,tipo,evento);}
}
