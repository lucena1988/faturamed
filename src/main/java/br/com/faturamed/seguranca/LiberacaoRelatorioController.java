package br.com.faturamed.seguranca;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/liberacao")
public class LiberacaoRelatorioController {
    private final LiberacaoRelatorioService service;
    private final ExportadorPreviaLiberacao previas;
    public LiberacaoRelatorioController(LiberacaoRelatorioService service,ExportadorPreviaLiberacao previas){this.service=service;this.previas=previas;}
    @GetMapping public LiberacaoRelatorioService.Estado consultar(@PathVariable long id) throws Exception{return service.consultar(id);}
    @PostMapping public LiberacaoRelatorioService.Estado liberar(@PathVariable long id,@Valid @RequestBody LiberacaoRelatorioService.Dados dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{return service.liberar(id,dados,user);}
    /** Excel com o que cada medico passaria a ver, para conferir antes de liberar. Nao publica nada. */
    @GetMapping("/previa.xlsx") public ResponseEntity<byte[]> previa(@PathVariable long id) throws Exception {
        var previa=service.previa(id);
        byte[] conteudo=previas.exportar(previa);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(ExportadorPreviaLiberacao.nomeDoArquivo(previa.competenciasProducao()),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(conteudo);
    }
}
