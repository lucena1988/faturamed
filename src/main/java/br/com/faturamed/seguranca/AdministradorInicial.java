package br.com.faturamed.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdministradorInicial implements ApplicationRunner {
    private final UsuarioService users;
    private final String email, senha;
    public AdministradorInicial(UsuarioService users, @Value("${ADMIN_INITIAL_EMAIL:admin@faturamed.local}") String email,
            @Value("${ADMIN_INITIAL_PASSWORD:}") String senha) { this.users=users; this.email=email; this.senha=senha; }
    @Override public void run(ApplicationArguments args) {
        if(!users.listar().isEmpty()) return;
        if(senha.isBlank()) throw new IllegalStateException("Configure ADMIN_INITIAL_PASSWORD antes do primeiro inicio");
        users.criar(new UsuarioService.Novo(email,"Administrador FaturaMed",senha,"ADMIN",null));
    }
}
