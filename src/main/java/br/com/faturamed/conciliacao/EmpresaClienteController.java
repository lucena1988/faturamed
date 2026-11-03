package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresas-clientes")
public class EmpresaClienteController {
    private final EmpresaClienteService service;
    public EmpresaClienteController(EmpresaClienteService service) { this.service=service; }
    @GetMapping public List<EmpresaClienteService.Empresa> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EmpresaClienteService.Empresa criar(@Valid @RequestBody EmpresaClienteService.Dados dados) { return service.salvar(null,dados); }
    @PutMapping("/{id}")
    public EmpresaClienteService.Empresa atualizar(@PathVariable Long id,@Valid @RequestBody EmpresaClienteService.Dados dados) { return service.salvar(id,dados); }
    public record Atribuicao(Long empresaId) {}
    @GetMapping("/conciliacoes/{relatorioId}")
    public Atribuicao atribuida(@PathVariable long relatorioId) { return new Atribuicao(service.atribuida(relatorioId)); }
    @PutMapping("/conciliacoes/{relatorioId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void atribuir(@PathVariable long relatorioId,@RequestBody Atribuicao dados) { service.atribuir(relatorioId,dados.empresaId()); }
}
