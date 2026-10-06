package br.com.faturamed.importacao;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    @EntityGraph(attributePaths = "empresa")
    List<Importacao> findByEmpresaIdOrderByCriadoEmDesc(Long empresaId);
}
