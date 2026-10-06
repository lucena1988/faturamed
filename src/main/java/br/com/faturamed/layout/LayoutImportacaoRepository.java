package br.com.faturamed.layout;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutImportacaoRepository extends JpaRepository<LayoutImportacao, Long> {

    @EntityGraph(attributePaths = "campos")
    List<LayoutImportacao> findByEmpresaIdOrderByCriadoEmDesc(Long empresaId);

    @EntityGraph(attributePaths = "campos")
    Optional<LayoutImportacao> findWithCamposById(Long id);
}
