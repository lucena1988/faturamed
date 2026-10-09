package br.com.faturamed.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AcessoController {
    public record TrocaSenha(@NotBlank String senhaAtual,@NotBlank String novaSenha) {}
    private final UsuarioService users;
    private final PasswordEncoder encoder;
    public AcessoController(UsuarioService users,PasswordEncoder encoder) { this.users=users; this.encoder=encoder; }
    @GetMapping("/api/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"header",token.getHeaderName()); }
    @GetMapping("/api/auth/me") public Map<String,Object> me(@AuthenticationPrincipal UsuarioAutenticado user) {
        var result=new LinkedHashMap<String,Object>(); result.put("id",user.id()); result.put("nome",user.nome()); result.put("email",user.email());
        result.put("perfil",user.perfil()); result.put("medicoId",user.medicoId()); return result;
    }
    @PostMapping("/api/auth/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void senha(@AuthenticationPrincipal UsuarioAutenticado user,@Valid @RequestBody TrocaSenha dados,HttpServletRequest request) {
        if(!encoder.matches(dados.senhaAtual(),users.loadUserByUsername(user.email()).getPassword())) throw new IllegalArgumentException("Senha atual incorreta");
        users.redefinir(user.id(),new UsuarioService.NovaSenha(dados.novaSenha(),user.versao()));
        request.getSession().invalidate();
    }
    @GetMapping("/api/usuarios") public List<UsuarioService.Usuario> listar() { return users.listar(); }
    @PostMapping("/api/usuarios") @ResponseStatus(HttpStatus.CREATED)
    public UsuarioService.Usuario criar(@Valid @RequestBody UsuarioService.Novo dados) { return users.criar(dados); }
    @PutMapping("/api/usuarios/{id}/situacao")
    public UsuarioService.Usuario situacao(@PathVariable long id,@Valid @RequestBody UsuarioService.Situacao dados,@AuthenticationPrincipal UsuarioAutenticado user) { return users.situacao(id,dados,user.id()); }
    @PostMapping("/api/usuarios/{id}/senha") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@PathVariable long id,@Valid @RequestBody UsuarioService.NovaSenha dados) { users.redefinir(id,dados); }
}
