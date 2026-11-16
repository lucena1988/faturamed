package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Lembretes, mensagens salvas e respostas rapidas do FaturaChat. */
@RestController
@RequestMapping("/api/chat")
public class ChatOrganizacaoController {
    public record NovoLembrete(Instant quando) {}
    public record NovaResposta(String atalho, String texto) {}
    private final ChatOrganizacaoService service;
    public ChatOrganizacaoController(ChatOrganizacaoService service) { this.service = service; }

    @PostMapping("/mensagens/{id}/lembrete") @ResponseStatus(HttpStatus.CREATED)
    public ChatOrganizacaoService.Lembrete lembrar(@PathVariable long id, @RequestBody NovoLembrete dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.lembrar(id, dados.quando(), user);
    }
    @GetMapping("/lembretes")
    public List<ChatOrganizacaoService.Lembrete> lembretes(@AuthenticationPrincipal UsuarioAutenticado user) { return service.lembretes(user); }
    @DeleteMapping("/lembretes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) { service.cancelarLembrete(id, user); }

    @PutMapping("/mensagens/{id}/salva")
    public Map<String, Boolean> salvar(@PathVariable long id, @RequestBody Map<String, Boolean> dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return Map.of("salva", service.salvar(id, !Boolean.FALSE.equals(dados.get("salva")), user));
    }
    @GetMapping("/salvas")
    public List<ChatOrganizacaoService.Salva> salvas(@AuthenticationPrincipal UsuarioAutenticado user) { return service.salvas(user); }

    @GetMapping("/respostas-rapidas")
    public List<ChatOrganizacaoService.RespostaRapida> respostas(@AuthenticationPrincipal UsuarioAutenticado user) { return service.respostas(user); }
    @PostMapping("/respostas-rapidas")
    public ChatOrganizacaoService.RespostaRapida novaResposta(@RequestBody NovaResposta dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.salvarResposta(dados.atalho(), dados.texto(), user);
    }
    @DeleteMapping("/respostas-rapidas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarResposta(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) { service.apagarResposta(id, user); }
}
