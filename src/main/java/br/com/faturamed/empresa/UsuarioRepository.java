package br.com.faturamed.empresa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByEmpresaId(Long empresaId);

    Optional<Usuario> findByEmail(String email);
}
