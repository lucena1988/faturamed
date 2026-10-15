package br.com.faturamed.seguranca;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class DemonstrativoMedicoController {
    private final DemonstrativoMedicoService administracao;
    private final ExtratoMedicoService portal;
    private final ExportadorDemonstrativoPdf pdf;
    public DemonstrativoMedicoController(DemonstrativoMedicoService administracao,ExtratoMedicoService portal,ExportadorDemonstrativoPdf pdf){this.administracao=administracao;this.portal=portal;this.pdf=pdf;}
    @GetMapping("/api/conciliacoes/visitas/{id}/demonstrativo.pdf")
    public ResponseEntity<byte[]> administrativo(@PathVariable long id,@RequestParam long medicoId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaDe,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaAte,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoAte) throws Exception {
        return resposta(administracao.buscar(id,medicoId,new ExtratoMedicoService.Filtros(visitaDe,visitaAte,recebimentoDe,recebimentoAte)));
    }
    @GetMapping("/api/medico/relatorios/{id}/demonstrativo.pdf")
    public ResponseEntity<byte[]> medico(@PathVariable long id,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaDe,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaAte,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoAte,
        @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        return resposta(portal.buscar(id,new ExtratoMedicoService.Filtros(visitaDe,visitaAte,recebimentoDe,recebimentoAte),user));
    }
    private ResponseEntity<byte[]> resposta(ExtratoMedicoService.Extrato extrato) throws Exception {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=demonstrativo-medico-"+extrato.relatorio()+".pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.exportar(extrato));
    }
}
