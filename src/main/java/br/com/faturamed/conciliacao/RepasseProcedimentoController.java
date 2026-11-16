package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Cadastro do repasse por procedimento, usado quando a planilha do hospital nao traz o repasse. */
@RestController
@RequestMapping("/api/fechamento-faturado/repasses")
public class RepasseProcedimentoController {
    private final RepasseProcedimentoService service;
    public RepasseProcedimentoController(RepasseProcedimentoService service) { this.service = service; }

    @GetMapping public List<RepasseProcedimentoService.Repasse> listar() { return service.listar(); }

    @GetMapping("/procedimentos")
    public List<RepasseProcedimentoService.ProcedimentoConhecido> procedimentos(@RequestParam(required = false) Long hospitalId) {
        return service.procedimentos(hospitalId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public RepasseProcedimentoService.Repasse criar(@RequestBody RepasseProcedimentoService.Dados dados, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        exigirGestor(usuario); return service.salvar(null, dados);
    }

    @PutMapping("/{id}")
    public RepasseProcedimentoService.Repasse atualizar(@PathVariable Long id, @RequestBody RepasseProcedimentoService.Dados dados, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        exigirGestor(usuario); return service.salvar(id, dados);
    }

    private void exigirGestor(UsuarioAutenticado usuario) {
        if (usuario == null || !List.of("ADMIN", "DEV").contains(usuario.perfil())) throw new AccessDeniedException("Somente ADMIN ou DEV altera o repasse cadastrado");
    }
}
