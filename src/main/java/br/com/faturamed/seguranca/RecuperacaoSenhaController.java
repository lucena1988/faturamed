package br.com.faturamed.seguranca;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Publico: pedir o codigo e redefinir a senha sem estar logado. */
@RestController
@RequestMapping("/api/auth/recuperacao")
public class RecuperacaoSenhaController {
    public record Pedido(String email) {}
    public record Redefinicao(String email, String codigo, String senha) {}
    private final RecuperacaoSenhaService service;
    public RecuperacaoSenhaController(RecuperacaoSenhaService service) { this.service = service; }

    // Resposta identica exista ou nao o e-mail, para nao revelar quem tem acesso.
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> solicitar(@RequestBody Pedido pedido) {
        service.solicitar(pedido == null ? null : pedido.email());
        return Map.of("mensagem", "Se o e-mail estiver cadastrado, enviaremos um codigo valido por 15 minutos");
    }

    @PostMapping("/confirmar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@RequestBody Redefinicao dados) {
        if (dados == null) throw new IllegalArgumentException("Informe e-mail, codigo e nova senha");
        service.redefinir(dados.email(), dados.codigo(), dados.senha());
    }
}
