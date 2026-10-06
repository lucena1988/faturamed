package br.com.faturamed.layout.api;

import br.com.faturamed.layout.LayoutImportacaoService;
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
@RequestMapping("/api/layouts")
public class LayoutImportacaoController {

    private final LayoutImportacaoService layoutImportacaoService;

    public LayoutImportacaoController(LayoutImportacaoService layoutImportacaoService) {
        this.layoutImportacaoService = layoutImportacaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LayoutImportacaoResponse criar(@Valid @RequestBody CriarLayoutImportacaoRequest request) {
        return LayoutImportacaoResponse.from(layoutImportacaoService.criar(request));
    }

    @GetMapping
    public List<LayoutImportacaoResponse> listar(@RequestParam Long empresaId) {
        return layoutImportacaoService.listarPorEmpresa(empresaId).stream()
                .map(LayoutImportacaoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public LayoutImportacaoResponse buscar(@PathVariable Long id) {
        return LayoutImportacaoResponse.from(layoutImportacaoService.buscar(id));
    }
}
