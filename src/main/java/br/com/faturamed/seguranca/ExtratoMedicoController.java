package br.com.faturamed.seguranca;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medico/relatorios/{id}")
public class ExtratoMedicoController {
    private final ExtratoMedicoService service;
    private final ExportadorExtratoMedico exportador;
    public ExtratoMedicoController(ExtratoMedicoService service,ExportadorExtratoMedico exportador) { this.service=service;this.exportador=exportador; }
    @GetMapping("/extrato") public ExtratoMedicoService.Extrato buscar(@PathVariable long id,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaDe,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaAte,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoAte,
            @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        return service.buscar(id,new ExtratoMedicoService.Filtros(visitaDe,visitaAte,recebimentoDe,recebimentoAte),user);
    }
    @GetMapping("/extrato.xlsx") public ResponseEntity<byte[]> exportar(@PathVariable long id,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaDe,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate visitaAte,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoDe,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate recebimentoAte,
            @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        var extrato=buscar(id,visitaDe,visitaAte,recebimentoDe,recebimentoAte,user);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=extrato-medico-"+id+".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(extrato));
    }
}
