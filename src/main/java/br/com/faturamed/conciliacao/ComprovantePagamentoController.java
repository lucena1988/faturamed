package br.com.faturamed.conciliacao;

import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import br.com.faturamed.seguranca.UsuarioAutenticado;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/recebimentos/pagamentos/{pagamento}/comprovantes")
public class ComprovantePagamentoController {
    private final ComprovantePagamentoService service;
    public ComprovantePagamentoController(ComprovantePagamentoService service){this.service=service;}
    @GetMapping public ComprovantePagamentoService.Lista listar(@PathVariable long id,@PathVariable long pagamento){return service.listar(id,pagamento);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ComprovantePagamentoService.Lista anexar(@PathVariable long id,@PathVariable long pagamento,@RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception {return service.anexar(id,pagamento,arquivo,user);}
    @GetMapping("/{comprovante}") public ResponseEntity<byte[]> baixar(@PathVariable long id,@PathVariable long pagamento,@PathVariable long comprovante){var arquivo=service.baixar(id,pagamento,comprovante);return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.tipo())).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(arquivo.nome(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").body(arquivo.conteudo());}
    @PostMapping("/{comprovante}/retirada") public ComprovantePagamentoService.Lista retirar(@PathVariable long id,@PathVariable long pagamento,@PathVariable long comprovante,@Valid @RequestBody ComprovantePagamentoService.Retirada dados,@AuthenticationPrincipal UsuarioAutenticado user){return service.retirar(id,pagamento,comprovante,dados,user);}
}
