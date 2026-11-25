package br.com.faturamed.conciliacao;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.*;
import org.springframework.http.HttpStatus;
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
    private final RelatorioUnicoService relatorioUnico;
    private final AjusteProducaoService ajustes;
    public ProducaoPrimeiroController(ProducaoPrimeiroService service,ConciliacaoUnicaService conciliacao,RelatorioUnicoService relatorioUnico,AjusteProducaoService ajustes) {
        this.service=service; this.conciliacao=conciliacao; this.relatorioUnico=relatorioUnico; this.ajustes=ajustes;
    }

    /** Arquivo do hospital na mesma tela, antes ou depois da producao. competencia e a do faturamento (NF). */
    @PostMapping(value="/hospital",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> hospital(@RequestParam long hospitalId,@RequestParam String competencia,
            @RequestParam(defaultValue="NOVO") String modoHospital,@RequestParam(required=false) String tipoProcedimento,
            @RequestParam MultipartFile arquivo,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        TipoProcedimento.de(tipoProcedimento);
        return conciliacao.importarHospital(hospitalId,competencia,modoHospital,tipoProcedimento,arquivo,usuario);
    }
    public record CorrigirCompetencia(String competencia,String justificativa) {}
    /** Competencia em branco restaura a derivada da data do atendimento. */
    @PostMapping("/visitas/{id}/competencia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void corrigirCompetencia(@PathVariable long id,@RequestBody CorrigirCompetencia dados,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        ajustes.corrigirCompetencia(id,dados.competencia(),dados.justificativa(),usuario);
    }
    public record DecisaoRepeticao(String acao,String justificativa) {}
    @PostMapping("/visitas/{id}/repeticao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decidirRepeticao(@PathVariable long id,@RequestBody DecisaoRepeticao dados,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        ajustes.decidirRepeticao(id,dados.acao(),dados.justificativa(),usuario);
    }
    @GetMapping("/visitas/{id}/historico")
    public List<Map<String,Object>> historico(@PathVariable long id) { return ajustes.historico(id); }

    /** Repete a conciliacao das visitas ainda sem relatorio (idempotente). */
    @PostMapping("/conciliar")
    public ConciliacaoUnicaService.Resumo conciliar(@RequestParam long hospitalId) throws Exception { return conciliacao.conciliar(hospitalId); }

    /** Medico x competencia: o que ja pode virar relatorio ao medico e o que ja virou. */
    @GetMapping("/relatorios")
    public List<Map<String,Object>> grupos(@RequestParam long hospitalId) { return relatorioUnico.grupos(hospitalId); }

    public record GerarRelatorio(long hospitalId,long medicoId,String competencia,Long empresaId) {}
    @PostMapping("/relatorios")
    public RelatorioUnicoService.Resultado gerar(@RequestBody GerarRelatorio dados,@AuthenticationPrincipal UsuarioAutenticado usuario) throws Exception {
        return relatorioUnico.gerar(dados.hospitalId(),dados.medicoId(),dados.competencia(),dados.empresaId(),usuario);
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
