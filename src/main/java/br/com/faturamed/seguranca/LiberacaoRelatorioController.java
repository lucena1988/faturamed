package br.com.faturamed.seguranca;

import jakarta.validation.Valid;
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
        byte[] conteudo=previas.exportar(service.previa(id));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("previa-liberacao-relatorio-"+id+".xlsx").build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(conteudo);
    }
}
