package br.com.faturamed.seguranca;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresa/painel")
public class PainelEmpresaController {
    private final PainelEmpresaService service;
    public PainelEmpresaController(PainelEmpresaService service) { this.service=service; }
    @GetMapping public PainelEmpresaService.Painel consultar(@AuthenticationPrincipal UsuarioAutenticado user,
            @RequestParam(required=false) Long medicoId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required=false) String hospital) throws Exception {
        // The account's company ID is authoritative; never accept it from a query parameter.
        return service.consultar(user.empresaId(),medicoId,de,ate,hospital);
    }
}
