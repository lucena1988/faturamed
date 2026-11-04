package br.com.faturamed.seguranca;

import br.com.faturamed.conciliacao.MedicoCadastroService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConviteMedicoController {
    private final ConviteMedicoService convites;
    private final MedicoCadastroService medicos;
    public ConviteMedicoController(ConviteMedicoService convites, MedicoCadastroService medicos) { this.convites = convites; this.medicos = medicos; }

    // FaturaMed (ADMIN/OPERADOR): gera o link de convite. O token so e devolvido aqui, uma unica vez.
    @PostMapping("/api/medicos/{id}/convite") @ResponseStatus(HttpStatus.CREATED)
    public ConviteMedicoService.Convite gerar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        return convites.gerar(id, user.id());
    }

    // Publico: quem tem o link consulta a previa e completa o cadastro criando a propria senha.
    @GetMapping("/api/convites/{token}")
    public ConviteMedicoService.Previa previa(@PathVariable String token) { return convites.consultar(token); }

    @PostMapping("/api/convites/{token}")
    public MedicoCadastroService.Medico aceitar(@PathVariable String token, @RequestBody ConviteMedicoService.Aceite dados) {
        return convites.aceitar(token, dados);
    }

    // Medico ja com acesso: le e completa o proprio cadastro (o medico vem da sessao, nunca da URL).
    @GetMapping("/api/medico/cadastro")
    public MedicoCadastroService.Medico meuCadastro(@AuthenticationPrincipal UsuarioAutenticado user) { return medicos.buscar(user.medicoId()); }

    @PutMapping("/api/medico/cadastro")
    public MedicoCadastroService.Medico completar(@RequestBody MedicoCadastroService.Complemento dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return medicos.completar(user.medicoId(), dados);
    }
}
