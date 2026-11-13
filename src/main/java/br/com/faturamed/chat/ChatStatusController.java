package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Status de acompanhamento nas mensagens e a lista "o que esta comigo". */
@RestController
@RequestMapping("/api/chat")
public class ChatStatusController {
    public record NovoStatus(String status, Boolean assumir) {}
    private final ChatStatusService service;
    public ChatStatusController(ChatStatusService service) { this.service = service; }

    @PutMapping("/mensagens/{id}/status")
    public ChatStatusService.StatusMensagem definir(@PathVariable long id, @RequestBody NovoStatus dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.definir(id, dados.status(), Boolean.TRUE.equals(dados.assumir()), user);
    }

    @GetMapping("/conversas/{id}/status")
    public List<ChatStatusService.StatusMensagem> daConversa(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.daConversa(id, user);
    }

    @GetMapping("/pendencias")
    public List<ChatStatusService.Pendencia> comigo(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.comigo(user);
    }
}
