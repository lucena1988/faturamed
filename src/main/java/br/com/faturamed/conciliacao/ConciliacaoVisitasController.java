package br.com.faturamed.conciliacao;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conciliacoes/visitas")
public class ConciliacaoVisitasController {
    private final ExportadorRelatorioVisitas exportador;
    private final JdbcTemplate jdbc;
    private final RevisaoVisitasService revisoes;
    private final FiltroRelatorioMedico filtroMedico;
    private final AcompanhamentoRevisao acompanhamento;
    private final MedicoCadastroService medicos;

    public ConciliacaoVisitasController(ExportadorRelatorioVisitas exportador, JdbcTemplate jdbc,
            RevisaoVisitasService revisoes, FiltroRelatorioMedico filtroMedico,
            AcompanhamentoRevisao acompanhamento, MedicoCadastroService medicos) {
        this.exportador=exportador; this.jdbc=jdbc; this.revisoes=revisoes;
        this.filtroMedico=filtroMedico; this.acompanhamento=acompanhamento; this.medicos=medicos;
    }
    @GetMapping
    public List<Map<String,Object>> listar() {
        return jdbc.queryForList("""
                select id,criado_em,relatorio->>'hospital' as hospital,
                relatorio->>'arquivoMedico' as arquivo_medico,numero_versao,anterior_id,medico_producao_id,base_hospitalar,
                (select id from conciliacao_visitas seguinte where seguinte.anterior_id=conciliacao_visitas.id) as proxima_id
                from conciliacao_visitas order by id desc limit 100
                """);
    }
    @GetMapping("/{id}")
    public ConciliadorVisitas.Relatorio buscar(@PathVariable Long id) throws Exception { return revisoes.carregar(id).atual(); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) { revisoes.excluir(id); }
    @GetMapping("/{id}/acompanhamento")
    public AcompanhamentoRevisao.Resultado acompanhamento(@PathVariable Long id) throws Exception { return acompanhamento.analisar(revisoes.carregar(id)); }
    @GetMapping("/{id}/visitas/{linha}/revisao")
    public RevisaoVisitasService.Detalhe detalhar(@PathVariable Long id,@PathVariable int linha,
            @RequestParam(required=false) String atendimento,@RequestParam(required=false) java.time.LocalDate data) throws Exception {
        return revisoes.detalhar(id,linha,atendimento,data);
    }
    @PostMapping("/{id}/visitas/{linha}/revisao")
    public RevisaoVisitasService.Resultado revisar(@PathVariable Long id,@PathVariable int linha,@Valid @RequestBody RevisaoVisitaRequest request) throws Exception {
        return revisoes.revisar(id,linha,request);
    }
    @GetMapping("/{id}/relatorio.xlsx")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id,@RequestParam(required=false) String medico,@RequestParam(required=false) Long medicoId) throws Exception {
        var estado=revisoes.carregar(id);
        if(medico!=null && medicoId!=null) throw new IllegalArgumentException("Use medico ou medicoId, nao ambos");
        if(medico!=null && medicos.nomeAmbiguo(medico)) throw new IllegalArgumentException("Nome ambiguo. Vincule as visitas ao cadastro e exporte por medicoId");
        var cadastro=medicoId==null?null:medicos.buscar(medicoId);
        var relatorio=medicoId!=null?filtroMedico.filtrar(estado.atual(),medicoId,medicos):medico==null?estado.atual():filtroMedico.filtrar(estado.atual(),medico);
        var nome=cadastro==null?medico:cadastro.nome();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=conciliacao-visitas-"+id+(nome==null?"":"-medico")+".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(relatorio,estado.historico(),nome,cadastro));
    }
}
