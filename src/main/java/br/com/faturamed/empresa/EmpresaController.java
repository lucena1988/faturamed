package br.com.faturamed.empresa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaResponse criar(@Valid @RequestBody CriarEmpresaRequest request) {
        return EmpresaResponse.from(empresaService.criar(request.nome(), request.documento()));
    }

    @GetMapping
    public List<EmpresaResponse> listar() {
        return empresaService.listar().stream()
                .map(EmpresaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public EmpresaResponse buscar(@PathVariable Long id) {
        return EmpresaResponse.from(empresaService.buscar(id));
    }

    public record CriarEmpresaRequest(
            @NotBlank @Size(max = 180) String nome,
            @Size(max = 30) String documento
    ) {
    }

    public record EmpresaResponse(
            Long id,
            String nome,
            String documento,
            boolean ativa,
            LocalDateTime criadoEm
    ) {

        static EmpresaResponse from(Empresa empresa) {
            return new EmpresaResponse(
                    empresa.getId(),
                    empresa.getNome(),
                    empresa.getDocumento(),
                    empresa.isAtiva(),
                    empresa.getCriadoEm()
            );
        }
    }
}
