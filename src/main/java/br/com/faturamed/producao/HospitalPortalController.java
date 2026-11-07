package br.com.faturamed.producao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** API do Portal do Hospital (perfil HOSPITAL; ver SegurancaConfig). */
@RestController
@RequestMapping("/api/hospital-portal")
public class HospitalPortalController {
    public record Marcar(boolean conferido) {}
    public record Contestar(String motivo) {}

    private final HospitalPortalService service;
    private final ExportadorFechamentoProducao exportador;
    public HospitalPortalController(HospitalPortalService service, ExportadorFechamentoProducao exportador) { this.service = service; this.exportador = exportador; }

    private long hospital(UsuarioAutenticado u) { return service.hospitalDoUsuario(u.id()); }

    @GetMapping("/eu")
    public Map<String, Object> eu(@AuthenticationPrincipal UsuarioAutenticado u) {
        long h = hospital(u);
        return Map.of("hospitalId", h, "hospital", service.nomeHospital(h), "nome", u.nome());
    }

    @GetMapping("/fechamentos")
    public List<FechamentoProducaoService.Fechamento> fechamentos(@AuthenticationPrincipal UsuarioAutenticado u) { return service.listar(hospital(u)); }

    @GetMapping("/fechamentos/{id}")
    public HospitalPortalService.Conferencia abrir(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) { return service.abrir(id, hospital(u), u.nome()); }

    @PostMapping("/fechamentos/{id}/lotes/{loteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void conferirLote(@PathVariable long id, @PathVariable long loteId, @RequestBody Marcar m, @AuthenticationPrincipal UsuarioAutenticado u) {
        service.conferirLote(id, loteId, m.conferido(), hospital(u), u.nome());
    }

    @PostMapping("/fechamentos/{id}/itens/{itemId}/contestacao") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void contestar(@PathVariable long id, @PathVariable long itemId, @RequestBody Contestar c, @AuthenticationPrincipal UsuarioAutenticado u) {
        service.contestar(id, itemId, c.motivo() == null ? "" : c.motivo(), hospital(u), u.nome());
    }

    @DeleteMapping("/fechamentos/{id}/itens/{itemId}/contestacao") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarContestacao(@PathVariable long id, @PathVariable long itemId, @AuthenticationPrincipal UsuarioAutenticado u) {
        service.contestar(id, itemId, null, hospital(u), u.nome());
    }

    @PostMapping("/fechamentos/{id}/aprovar")
    public FechamentoProducaoService.Detalhe aprovar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) { return service.aprovar(id, hospital(u), u.nome()); }

    @GetMapping("/fechamentos/{id}/planilha")
    public ResponseEntity<byte[]> planilha(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) {
        var d = service.detalheParaPlanilha(id, hospital(u));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("fechamento-" + id + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(exportador.exportar(d));
    }
}
