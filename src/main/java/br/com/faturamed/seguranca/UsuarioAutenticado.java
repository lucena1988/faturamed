package br.com.faturamed.seguranca;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UsuarioAutenticado(long id, String email, String nome, String senhaHash, String perfil,
        Long medicoId, boolean ativo, boolean bloqueado, long versao) implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + perfil)); }
    @Override public String getPassword() { return senhaHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return ativo; }
    @Override public boolean isAccountNonLocked() { return !bloqueado; }
}
