package br.com.faturamed.seguranca;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chamados")
public class ChamadoController {
    private final ChamadoService service;

    public ChamadoController(ChamadoService service) {
        this.service = service;
    }

    @GetMapping("/notificacoes")
    public ChamadoService.CaixaAvisos avisos(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.avisos(user);
    }

    @PostMapping("/notificacoes/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lerAviso(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        service.lerAviso(id,user);
    }

    @GetMapping("/tipos")
    public List<ChamadoService.TipoSla> listarTipos() {
        return service.listarTipos();
    }

    @PutMapping("/tipos/{codigo}")
    public ChamadoService.TipoSla atualizarSla(
            @PathVariable String codigo,
            @Valid @RequestBody ChamadoService.AtualizarSla dados,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.atualizarSla(codigo, dados, user);
    }

    @GetMapping("/atendentes")
    public List<ChamadoService.Atendente> listarAtendentes(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.listarAtendentes(user);
    }

    @GetMapping
    public ChamadoService.PainelChamados listar(
            @RequestParam(required = false) String fila,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean meus,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.listar(fila, status, tipo, busca, meus, user);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChamadoService.DetalheChamado criar(
            @RequestParam String tipoCodigo,
            @RequestParam String titulo,
            @RequestParam String descricao,
            @RequestParam(required = false) String paginaOrigem,
            @RequestParam(required = false) List<MultipartFile> arquivos,
            @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        return service.criar(tipoCodigo, titulo, descricao, paginaOrigem, arquivos, user);
    }

    @GetMapping("/{id}")
    public ChamadoService.DetalheChamado detalhar(
            @PathVariable long id,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.detalhar(id, user);
    }

    @PostMapping(value = "/{id}/mensagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChamadoService.DetalheChamado adicionarMensagem(
            @PathVariable long id,
            @RequestParam(required = false) String conteudo,
            @RequestParam(required = false) List<MultipartFile> arquivos,
            @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        return service.adicionarMensagem(id, conteudo, arquivos, user);
    }

    @PostMapping("/{id}/status")
    public ChamadoService.DetalheChamado atualizarStatus(
            @PathVariable long id,
            @Valid @RequestBody ChamadoService.AlteracaoStatus dados,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.atualizarStatus(id, dados, user);
    }

    @PostMapping("/{id}/avaliacao")
    public ChamadoService.DetalheChamado avaliar(
            @PathVariable long id,
            @Valid @RequestBody ChamadoService.Avaliacao dados,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.avaliar(id, dados, user);
    }

    @GetMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<byte[]> baixarAnexo(
            @PathVariable long id,
            @PathVariable long anexoId,
            @RequestParam(defaultValue = "false") boolean download,
            @AuthenticationPrincipal UsuarioAutenticado user) {
        var arq = service.baixarAnexo(id, anexoId, user);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(arq.nome(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arq.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(arq.conteudo());
    }
}
