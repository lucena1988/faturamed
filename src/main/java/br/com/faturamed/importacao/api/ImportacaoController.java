package br.com.faturamed.importacao.api;

import br.com.faturamed.importacao.ImportacaoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportacaoResponse criar(@Valid @RequestBody CriarImportacaoRequest request) {
        return ImportacaoResponse.from(importacaoService.criar(request));
    }

    @GetMapping
    public List<ImportacaoResponse> listar(@RequestParam Long empresaId) {
        return importacaoService.listarPorEmpresa(empresaId).stream()
                .map(ImportacaoResponse::from)
                .toList();
    }

    @PostMapping("/{id}/registros")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroImportadoResponse adicionarRegistro(
            @PathVariable Long id,
            @Valid @RequestBody CriarRegistroImportadoRequest request
    ) {
        return RegistroImportadoResponse.from(importacaoService.adicionarRegistro(id, request));
    }

    @GetMapping("/{id}/registros")
    public List<RegistroImportadoResponse> listarRegistros(@PathVariable Long id) {
        return importacaoService.listarRegistros(id).stream()
                .map(RegistroImportadoResponse::from)
                .toList();
    }
}
