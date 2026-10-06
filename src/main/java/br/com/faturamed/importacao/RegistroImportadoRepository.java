package br.com.faturamed.importacao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroImportadoRepository extends JpaRepository<RegistroImportado, Long> {

    List<RegistroImportado> findByImportacaoIdOrderByNumeroLinha(Long importacaoId);
}
