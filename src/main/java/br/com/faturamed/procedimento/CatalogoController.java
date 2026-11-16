package br.com.faturamed.procedimento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {
    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importar(@Valid @RequestBody ImportarCatalogo request) {
        return service.importar(request);
    }

    @PostMapping(value = "/planilha", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importarPlanilha(@RequestParam(defaultValue = "TUSS") Tabela tabela, @RequestParam String versao,
            @RequestParam(required = false) String fonte, @RequestParam org.springframework.web.multipart.MultipartFile arquivo,
            @org.springframework.security.core.annotation.AuthenticationPrincipal br.com.faturamed.seguranca.UsuarioAutenticado usuario) throws java.io.IOException {
        if (usuario == null || !List.of("ADMIN", "DEV").contains(usuario.perfil()))
            throw new org.springframework.security.access.AccessDeniedException("Somente ADMIN ou DEV importa a tabela de procedimentos");
        if (arquivo.isEmpty()) throw new IllegalArgumentException("Arquivo vazio");
        return service.importarPlanilha(tabela, versao, fonte, arquivo.getOriginalFilename(), arquivo.getBytes());
    }

    @GetMapping("/busca")
    public List<Map<String, Object>> buscarTexto(@RequestParam(defaultValue = "TUSS") Tabela tabela,
            @RequestParam(required = false) String q, @RequestParam(required = false) List<String> codigos) {
        return service.buscarTexto(tabela, q, codigos);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return service.listar();
    }

    @GetMapping("/{id}/procedimentos")
    public List<Map<String, Object>> buscar(@PathVariable Long id,
            @RequestParam String codigo, @RequestParam(required = false) LocalDate data) {
        return service.buscar(id, codigo, data);
    }

    public record ImportarCatalogo(
            @NotNull Tabela tabela,
            @NotBlank @Size(max = 80) String versao,
            @NotBlank @Size(max = 1000) String fonte,
            @NotNull LocalDate consultadoEm,
            @NotEmpty @Size(max = 20000) List<@NotNull @Valid Procedimento> procedimentos) {}

    public record Procedimento(
            @NotBlank @Pattern(regexp = "[0-9]{1,40}") String codigo,
            @NotBlank @Size(max = 2000) String descricao,
            LocalDate inicioVigencia,
            LocalDate fimVigencia) {}

    public enum Tabela { TUSS, AMB_92 }
}
