package br.com.faturamed.producao;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/producao")
public class FechamentoProducaoController {
    private final FechamentoProducaoService service;
    private final ExportadorFechamentoProducao exportador;
    public FechamentoProducaoController(FechamentoProducaoService service, ExportadorFechamentoProducao exportador) {
        this.service = service; this.exportador = exportador;
    }

    public record NovoFechamento(Long hospitalId, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim, String observacao) {}
    public record Aprovacao(String aprovadoPor, LocalDate data) {}
    public record NotaFiscal(String numero, LocalDate data) {}

    @GetMapping("/regras") public List<FechamentoProducaoService.Regra> regras(@RequestParam(required = false) Long hospitalId) { return service.regras(hospitalId); }
    @PostMapping("/regras") @ResponseStatus(HttpStatus.CREATED)
    public FechamentoProducaoService.Regra criarRegra(@Valid @RequestBody FechamentoProducaoService.RegraDados d) { return service.salvarRegra(null, d); }
    @PutMapping("/regras/{id}")
    public FechamentoProducaoService.Regra atualizarRegra(@PathVariable long id, @Valid @RequestBody FechamentoProducaoService.RegraDados d) { return service.salvarRegra(id, d); }
    @DeleteMapping("/regras/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRegra(@PathVariable long id) { service.excluirRegra(id); }

    @GetMapping("/fechamentos/previa")
    public FechamentoProducaoService.Previa previa(@RequestParam long hospitalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) { return service.previa(hospitalId, inicio, fim); }
    @GetMapping("/fechamentos") public List<FechamentoProducaoService.Fechamento> fechamentos(@RequestParam(required = false) Long hospitalId) { return service.fechamentos(hospitalId); }
    @PostMapping("/fechamentos") @ResponseStatus(HttpStatus.CREATED)
    public FechamentoProducaoService.Detalhe fechar(@RequestBody NovoFechamento n, Principal p) {
        if (n.hospitalId() == null || n.inicio() == null || n.fim() == null) throw new IllegalArgumentException("Informe hospital e periodo");
        return service.fechar(n.hospitalId(), n.inicio(), n.fim(), n.observacao(), p == null ? null : p.getName());
    }
    @GetMapping("/fechamentos/{id}") public FechamentoProducaoService.Detalhe detalhe(@PathVariable long id) { return service.detalhe(id); }
    @PostMapping("/fechamentos/{id}/aprovar")
    public FechamentoProducaoService.Detalhe aprovar(@PathVariable long id, @RequestBody Aprovacao a) { return service.aprovar(id, a.aprovadoPor(), a.data()); }
    @PostMapping("/fechamentos/{id}/nf")
    public FechamentoProducaoService.Detalhe nf(@PathVariable long id, @RequestBody NotaFiscal nf) { return service.registrarNf(id, nf.numero(), nf.data()); }
    @DeleteMapping("/fechamentos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable long id) { service.cancelar(id); }

    @GetMapping("/fechamentos/{id}/planilha")
    public ResponseEntity<byte[]> planilha(@PathVariable long id) {
        var d = service.detalhe(id);
        String nome = "producao-" + d.fechamento().hospital().replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT)
                + "-" + d.fechamento().dataInicio() + "-a-" + d.fechamento().dataFim() + ".xlsx";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(d));
    }
}
