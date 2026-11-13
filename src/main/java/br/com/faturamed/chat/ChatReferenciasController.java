package br.com.faturamed.chat;

import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Resumo das referencias citadas nas mensagens (#lote, #chamado, #relatorio, #div). */
@RestController
@RequestMapping("/api/chat/referencias")
public class ChatReferenciasController {
    private final ChatReferencias referencias;
    public ChatReferenciasController(ChatReferencias referencias) { this.referencias = referencias; }

    @GetMapping
    public List<ChatReferencias.Referencia> resolver(@RequestParam(name = "c", required = false) List<String> chaves) {
        return referencias.resolver(chaves);
    }
}
