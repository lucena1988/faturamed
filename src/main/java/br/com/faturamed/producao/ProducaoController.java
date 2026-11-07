package br.com.faturamed.producao;

import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/producao")
public class ProducaoController {
    private static final Set<String> TIPOS_IMAGEM = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final ProducaoService service;
    private final ExtratorEtiquetasOcr ocr;
    public ProducaoController(ProducaoService service, ExtratorEtiquetasOcr ocr) { this.service = service; this.ocr = ocr; }

    @GetMapping("/lotes")
    public List<ProducaoService.Resumo> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long hospitalId, @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) String status) {
        return service.listar(de, ate, hospitalId, medicoId, status);
    }
    @GetMapping("/lotes/{id}") public ProducaoService.Lote buscar(@PathVariable long id) { return service.buscar(id); }
    @PostMapping("/lotes") @ResponseStatus(HttpStatus.CREATED)
    public ProducaoService.Lote criar(@Valid @RequestBody ProducaoService.Salvar dados, Principal p) { return service.salvar(null, dados, nome(p)); }
    @PutMapping("/lotes/{id}")
    public ProducaoService.Lote atualizar(@PathVariable long id, @Valid @RequestBody ProducaoService.Salvar dados, Principal p) { return service.salvar(id, dados, nome(p)); }
    @PostMapping("/lotes/{id}/status")
    public ProducaoService.Lote status(@PathVariable long id, @RequestBody Map<String, String> corpo) { return service.alterarStatus(id, corpo.get("status")); }
    @DeleteMapping("/lotes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable long id) { service.excluir(id); }

    @PostMapping(value = "/lotes/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarFoto(@PathVariable long id, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        service.salvarFoto(id, arquivo.getBytes(), tipoImagem(arquivo));
    }
    @GetMapping("/lotes/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable long id) {
        Map<String, Object> f = service.foto(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String) f.get("foto_tipo"))).body((byte[]) f.get("foto"));
    }

    @PostMapping("/duplicidades")
    public List<ProducaoService.Duplicidade> duplicidades(@RequestBody Map<String, Object> corpo) {
        @SuppressWarnings("unchecked") List<String> atendimentos = (List<String>) corpo.getOrDefault("atendimentos", List.of());
        Object lote = corpo.get("loteId");
        return service.duplicidades(atendimentos, lote == null ? null : Long.valueOf(lote.toString()));
    }

    @GetMapping("/exames") public List<ProducaoService.Exame> exames() { return service.exames(); }
    @PostMapping("/exames") @ResponseStatus(HttpStatus.CREATED)
    public ProducaoService.Exame criarExame(@RequestBody Map<String, String> corpo) { return service.criarExame(corpo.get("nome")); }

    @GetMapping("/ocr/status") public Map<String, Object> ocrStatus() { return Map.of("disponivel", ocr.disponivel(), "motor", ocr.motor()); }
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtratorEtiquetasOcr.Resultado extrair(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return ocr.extrair(arquivo.getBytes(), tipoImagem(arquivo));
    }

    private static String tipoImagem(MultipartFile arquivo) {
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase(Locale.ROOT);
        if (arquivo.isEmpty() || !TIPOS_IMAGEM.contains(tipo)) throw new IllegalArgumentException("Envie uma foto JPG, PNG ou WEBP");
        return tipo;
    }
    private static String nome(Principal p) { return p == null ? null : p.getName(); }
}
