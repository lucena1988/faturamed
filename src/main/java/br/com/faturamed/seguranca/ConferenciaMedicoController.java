package br.com.faturamed.seguranca;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConferenciaMedicoController {
    private final ConferenciaMedicoService service;
    public ConferenciaMedicoController(ConferenciaMedicoService service){this.service=service;}
    @GetMapping("/api/medico/relatorios/{id}/conferencia")
    public ConferenciaMedicoService.Estado consultar(@PathVariable long id,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{return service.consultar(id,user.medicoId());}
    @PostMapping("/api/medico/relatorios/{id}/ciencia")
    public void ciencia(@PathVariable long id,@Valid @RequestBody ConferenciaMedicoService.Ciencia dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{service.ciencia(id,dados,user);}
    @PostMapping("/api/medico/relatorios/{id}/solicitacoes")
    public void solicitar(@PathVariable long id,@Valid @RequestBody ConferenciaMedicoService.Pedido dados,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{service.solicitar(id,dados,user);}
    @GetMapping("/api/solicitacoes-medicos")
    public List<ConferenciaMedicoService.Item> listar(@RequestParam(defaultValue="ABERTAS") String situacao){return service.listar(situacao);}
    @GetMapping("/api/solicitacoes-medicos/{pedido}")
    public ConferenciaMedicoService.Item pedidoAdministrativo(@PathVariable long pedido){return service.pedidoAdministrativo(pedido);}
    @GetMapping("/api/medico/relatorios/{id}/solicitacoes/{pedido}")
    public ConferenciaMedicoService.Item pedidoDoMedico(@PathVariable long id,@PathVariable long pedido,@AuthenticationPrincipal UsuarioAutenticado user) throws Exception{return service.pedidoDoMedico(id,user.medicoId(),pedido);}
    @GetMapping("/api/ciencias-medicos")
    public List<ConferenciaMedicoService.Leitura> ciencias(){return service.ciencias();}
    @PostMapping("/api/solicitacoes-medicos/{id}/resposta")
    public void responder(@PathVariable long id,@Valid @RequestBody ConferenciaMedicoService.Resposta dados,@AuthenticationPrincipal UsuarioAutenticado user){service.responder(id,dados,user);}
}
