package br.com.faturamed.seguranca;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/liberacao")
public class LiberacaoRelatorioController {
    private final LiberacaoRelatorioService service;
    public LiberacaoRelatorioController(LiberacaoRelatorioService service){this.service=service;}
    @GetMapping public LiberacaoRelatorioService.Estado consultar(@PathVariable long id) throws Exception{return service.consultar(id);}
    @PostMapping public LiberacaoRelatorioService.Estado liberar(@PathVariable long id,@Valid @RequestBody LiberacaoRelatorioService.Dados dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{return service.liberar(id,dados,user);}
}
