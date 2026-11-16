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
    @org.springframework.beans.factory.annotation.Autowired private RegistroAcesso acessos;
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
    public HospitalPortalService.Conferencia abrir(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) { acessos.registrar("ABRIU_FECHAMENTO", "FECHAMENTO", id, "Portal do hospital"); return service.abrir(id, hospital(u), u.nome()); }

    @PostMapping("/fechamentos/{id}/lotes/{loteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void conferirLote(@PathVariable long id, @PathVariable long loteId, @RequestBody Marcar m, @AuthenticationPrincipal UsuarioAutenticado u) {
        service.conferirLote(id, loteId, m.conferido(), hospital(u), u.nome());
    }

    @PostMapping("/fechamentos/{id}/lotes/conferir-todos")
    public Map<String, Object> conferirTodos(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) {
        return Map.of("marcados", service.conferirTodos(id, hospital(u), u.nome()));
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

    @PostMapping("/fechamentos/{id}/revelar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revelar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) {
        service.detalheParaPlanilha(id, hospital(u)); // garante que o fechamento e do hospital
        acessos.registrar("REVELOU_DADOS", "FECHAMENTO", id, "Portal do hospital: exibiu carteirinha sem máscara");
    }

    @GetMapping("/fechamentos/{id}/nf")
    public ResponseEntity<byte[]> nf(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) {
        acessos.registrar("BAIXOU_NF", "FECHAMENTO", id, "Portal do hospital");
        return FechamentoProducaoController.arquivoNf(service.nfArquivo(id, hospital(u)));
    }

    @GetMapping("/fechamentos/{id}/planilha")
    public ResponseEntity<byte[]> planilha(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado u) {
        acessos.registrar("BAIXOU_PLANILHA", "FECHAMENTO", id, "Portal do hospital");
        var d = service.detalheParaPlanilha(id, hospital(u));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("fechamento-" + id + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(exportador.exportar(d));
    }
}
