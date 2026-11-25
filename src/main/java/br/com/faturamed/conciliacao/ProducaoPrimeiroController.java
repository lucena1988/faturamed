package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Conciliacao unica (producao primeiro). Convive com /api/base-hospitalar ate a validacao da nova tela. */
@RestController
@RequestMapping("/api/conciliacao-unica")
public class ProducaoPrimeiroController {
    private final ProducaoPrimeiroService service;
    public ProducaoPrimeiroController(ProducaoPrimeiroService service) { this.service=service; }

    /** Sem competencia: ela deriva da data de cada visita. medicoId so e enviado em producao de um unico medico. */
    @PostMapping(value="/producao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProducaoPrimeiroService.Resultado producao(@RequestParam long hospitalId,@RequestParam(required=false) Long medicoId,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return service.importar(hospitalId,medicoId,arquivo,usuario);
    }
    @GetMapping("/visitas")
    public List<Map<String,Object>> visitas(@RequestParam long hospitalId,@RequestParam(required=false) String competencia) {
        return service.visitas(hospitalId,competencia);
    }
    @GetMapping("/competencias")
    public SortedMap<String,Long> competencias(@RequestParam long hospitalId) { return service.resumoPorCompetencia(hospitalId); }
}
