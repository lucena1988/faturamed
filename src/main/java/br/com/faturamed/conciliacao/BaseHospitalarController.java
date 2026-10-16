package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/base-hospitalar")
public class BaseHospitalarController {
    private final BaseHospitalarService service;
    private final AcompanhamentoBaseHospitalarService acompanhamento;
    public BaseHospitalarController(BaseHospitalarService service,AcompanhamentoBaseHospitalarService acompanhamento) { this.service=service; this.acompanhamento=acompanhamento; }
    @GetMapping("/acompanhamento")
    public AcompanhamentoBaseHospitalarService.Resultado acompanhamento(@RequestParam long hospitalId) throws Exception { return acompanhamento.consultar(hospitalId); }

    @PostMapping(value="/hospital",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseHospitalarService.Resultado hospital(@RequestParam long hospitalId,@RequestParam String competencia,
            @RequestParam(defaultValue="NOVO") String modoHospital,@RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return service.importarHospital(hospitalId,competencia,modoHospital,arquivo,usuario);
    }
    @PostMapping(value="/producao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseHospitalarService.Resultado producao(@RequestParam long hospitalId,@RequestParam(required=false) Long medicoId,
            @RequestParam(defaultValue="UNICO") String modoProducao,@RequestParam String competencia,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return service.importarProducao(hospitalId,medicoId,modoProducao,competencia,arquivo,usuario);
    }
    @GetMapping("/arquivos")
    public List<Map<String,Object>> arquivos(@RequestParam long hospitalId) { return service.arquivos(hospitalId); }
    @PostMapping("/relatorios/{id}/atualizar")
    public Map<String,Long> atualizar(@PathVariable long id,@RequestParam long versao) throws Exception { return Map.of("id",service.atualizar(id,versao)); }
    @GetMapping("/visitas")
    public List<Map<String,Object>> visitas(@RequestParam long hospitalId) { return service.visitas(hospitalId); }
    @GetMapping("/visitas/{id}/historico")
    public List<Map<String,Object>> historico(@PathVariable int id) { return service.historico(id); }
    @PostMapping("/visitas/{id}/decisao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decidir(@PathVariable int id,@RequestBody BaseHospitalarService.Decisao dados,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.decidir(id,dados,usuario);
    }
    @GetMapping("/arquivos/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable long id) {
        var arquivo=service.arquivo(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(arquivo.get("nome").toString(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body((byte[])arquivo.get("conteudo"));
    }
}
