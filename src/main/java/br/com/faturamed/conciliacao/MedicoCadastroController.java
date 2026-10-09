package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medicos")
public class MedicoCadastroController {
    private final MedicoCadastroService service;
    public MedicoCadastroController(MedicoCadastroService service) { this.service=service; }
    @GetMapping public List<MedicoCadastroService.Medico> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MedicoCadastroService.Medico criar(@Valid @RequestBody MedicoCadastroService.Dados dados) { return service.salvar(null,dados); }
    @PutMapping("/{id}")
    public MedicoCadastroService.Medico atualizar(@PathVariable Long id,@Valid @RequestBody MedicoCadastroService.Dados dados) { return service.salvar(id,dados); }
}
