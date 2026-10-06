package br.com.faturamed.empresa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {

    List<Unidade> findByEmpresaId(Long empresaId);
}
