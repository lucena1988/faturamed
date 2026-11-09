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
    private static String nome(Principal p) { return p == null ? null : p.getName(); }
    private final FechamentoProducaoService service;
    @org.springframework.beans.factory.annotation.Autowired private RegistroAcesso acessos;
    @org.springframework.beans.factory.annotation.Autowired private PainelProducaoService painel;

    @GetMapping("/painel") public PainelProducaoService.Painel painel(@RequestParam(required = false) Long hospitalId) { return painel.painel(hospitalId); }

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
    public record EnvioLotes(List<Long> loteIds, String observacao) {}
    @PostMapping("/fechamentos/por-lotes") @ResponseStatus(HttpStatus.CREATED)
    public FechamentoProducaoService.Detalhe fecharLotes(@RequestBody EnvioLotes e, Principal p) {
        return service.fecharLotes(e.loteIds(), e.observacao(), nome(p));
    }

    @GetMapping("/fechamentos/{id}") public FechamentoProducaoService.Detalhe detalhe(@PathVariable long id) {
        acessos.registrar("ABRIU_FECHAMENTO", "FECHAMENTO", id, "");
        return service.detalhe(id);
    }
    @PostMapping("/fechamentos/{id}/aprovar")
    public FechamentoProducaoService.Detalhe aprovar(@PathVariable long id, @RequestBody Aprovacao a, Principal p) { return service.aprovar(id, a.aprovadoPor(), a.data(), nome(p)); }
    @PostMapping("/fechamentos/{id}/nf")
    public FechamentoProducaoService.Detalhe nf(@PathVariable long id, @RequestBody NotaFiscal nf, Principal p) { return service.registrarNf(id, nf.numero(), nf.data(), nome(p)); }
    @DeleteMapping("/fechamentos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable long id, Principal p) { service.cancelar(id, nome(p)); }

    public record Decisao(String decisao, String resposta) {}
    @GetMapping("/contestacoes") public List<FechamentoProducaoService.Contestacao> contestacoes() { return service.contestacoes(); }
    @PostMapping("/contestacoes/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolver(@PathVariable long itemId, @RequestBody Decisao d, Principal p) { service.resolverContestacao(itemId, d.decisao(), d.resposta(), nome(p)); }

    private static final java.util.Set<String> TIPOS_NF = java.util.Set.of("application/pdf", "image/jpeg", "image/png", "application/xml", "text/xml");

    @PostMapping(value = "/fechamentos/{id}/nf/arquivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anexarNf(@PathVariable long id, @RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo, Principal p) throws java.io.IOException {
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase(Locale.ROOT);
        if (arquivo.isEmpty() || !TIPOS_NF.contains(tipo)) throw new IllegalArgumentException("Envie a NF em PDF, XML ou imagem (JPG/PNG)");
        String nome = arquivo.getOriginalFilename() == null ? "nf" : arquivo.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        service.salvarNfArquivo(id, arquivo.getBytes(), nome.length() > 200 ? nome.substring(0, 200) : nome, tipo, nome(p));
    }

    @GetMapping("/fechamentos/{id}/nf/arquivo")
    public ResponseEntity<byte[]> baixarNf(@PathVariable long id) {
        acessos.registrar("BAIXOU_NF", "FECHAMENTO", id, "");
        return arquivoNf(service.nfArquivo(id));
    }

    static ResponseEntity<byte[]> arquivoNf(Map<String, Object> a) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((String) a.get("nf_arquivo_nome")).build().toString())
                .contentType(MediaType.parseMediaType((String) a.get("nf_arquivo_tipo"))).body((byte[]) a.get("nf_arquivo"));
    }

    @GetMapping("/fechamentos/{id}/planilha")
    public ResponseEntity<byte[]> planilha(@PathVariable long id) {
        acessos.registrar("BAIXOU_PLANILHA", "FECHAMENTO", id, "Planilha com dados das etiquetas");
        var d = service.detalhe(id);
        String nome = "producao-" + d.fechamento().hospital().replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT)
                + "-" + d.fechamento().dataInicio() + "-a-" + d.fechamento().dataFim() + ".xlsx";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(d));
    }
}
