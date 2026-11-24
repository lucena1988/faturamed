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
    private final ExclusaoArquivoHospitalService exclusoes;
    private final br.com.faturamed.seguranca.UsuarioService usuarios;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    public BaseHospitalarController(BaseHospitalarService service,AcompanhamentoBaseHospitalarService acompanhamento,ExclusaoArquivoHospitalService exclusoes,
            br.com.faturamed.seguranca.UsuarioService usuarios,org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.service=service; this.acompanhamento=acompanhamento; this.exclusoes=exclusoes; this.usuarios=usuarios; this.encoder=encoder;
    }
    public record ConfirmacaoExclusao(String senha) {}
    // Exclusao definitiva so com a senha de quem esta logado; erro conta como tentativa de login.
    @PostMapping("/arquivos/{id}/exclusao")
    public ExclusaoArquivoHospitalService.Resultado excluirArquivo(@PathVariable long id,@RequestBody ConfirmacaoExclusao dados,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        var conta=usuarios.loadUserByUsername(usuario.email());
        if(!conta.isAccountNonLocked()) throw new org.springframework.security.access.AccessDeniedException("Acesso bloqueado temporariamente apos tentativas invalidas. Aguarde 15 minutos");
        if(dados==null||dados.senha()==null||dados.senha().isBlank()||!encoder.matches(dados.senha(),conta.getPassword())) {
            usuarios.falha(usuario.email());
            throw new IllegalArgumentException("Senha incorreta. O arquivo nao foi excluido");
        }
        usuarios.sucesso(usuario.id());
        return exclusoes.excluir(id,usuario);
    }
    @GetMapping("/acompanhamento")
    public AcompanhamentoBaseHospitalarService.Resultado acompanhamento(@RequestParam long hospitalId,@RequestParam(required=false) String competencia) throws Exception { return acompanhamento.consultar(hospitalId,competencia); }

    @PostMapping(value="/hospital",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseHospitalarService.Resultado hospital(@RequestParam long hospitalId,@RequestParam String competencia,
            @RequestParam(defaultValue="NOVO") String modoHospital,@RequestParam(required=false) String tipoProcedimento,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        TipoProcedimento.de(tipoProcedimento);
        var resultado=service.importarHospital(hospitalId,competencia,modoHospital,arquivo,usuario);
        if(!resultado.repetido()) service.definirTipoArquivo(resultado.arquivoId(),tipoProcedimento);
        return resultado;
    }
    @PostMapping(value="/producao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseHospitalarService.Resultado producao(@RequestParam long hospitalId,@RequestParam(required=false) Long medicoId,
            @RequestParam(required=false) Long empresaId,
            @RequestParam(defaultValue="UNICO") String modoProducao,@RequestParam String competencia,
            @RequestParam(required=false) String tipoProcedimento,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        TipoProcedimento.de(tipoProcedimento);
        var resultado=service.importarProducao(hospitalId,medicoId,empresaId,modoProducao,competencia,arquivo,usuario);
        if(!resultado.repetido()) service.definirTipoArquivo(resultado.arquivoId(),tipoProcedimento);
        return resultado;
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
