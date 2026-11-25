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
    private final ConciliacaoUnicaService conciliacao;
    public ProducaoPrimeiroController(ProducaoPrimeiroService service,ConciliacaoUnicaService conciliacao) {
        this.service=service; this.conciliacao=conciliacao;
    }

    /** Arquivo do hospital na mesma tela, antes ou depois da producao. competencia e a do faturamento (NF). */
    @PostMapping(value="/hospital",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> hospital(@RequestParam long hospitalId,@RequestParam String competencia,
            @RequestParam(defaultValue="NOVO") String modoHospital,@RequestParam(required=false) String tipoProcedimento,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        TipoProcedimento.de(tipoProcedimento);
        return conciliacao.importarHospital(hospitalId,competencia,modoHospital,tipoProcedimento,arquivo,usuario);
    }
    @GetMapping("/hospital-sem-producao")
    public List<Map<String,Object>> hospitalSemProducao(@RequestParam long hospitalId,@RequestParam(required=false) String competencia) {
        return conciliacao.hospitalSemProducao(hospitalId,competencia);
    }

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
