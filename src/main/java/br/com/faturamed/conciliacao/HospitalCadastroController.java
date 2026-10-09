package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hospitais")
public class HospitalCadastroController {
    private final HospitalCadastroService service;
    public HospitalCadastroController(HospitalCadastroService service) { this.service=service; }
    @GetMapping public List<HospitalCadastroService.Hospital> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public HospitalCadastroService.Hospital criar(@Valid @RequestBody HospitalCadastroService.Dados dados) { return service.salvar(null,dados); }
    @PutMapping("/{id}")
    public HospitalCadastroService.Hospital atualizar(@PathVariable Long id,@Valid @RequestBody HospitalCadastroService.Dados dados) { return service.salvar(id,dados); }
}
