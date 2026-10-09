package br.com.faturamed.conciliacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conciliacoes/visitas")
public class ConciliacaoVisitasController {
    private final LeitorPlanilhaVisitas leitor;
    private final ConciliadorVisitas conciliador;
    private final ExportadorRelatorioVisitas exportador;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RevisaoVisitasService revisoes;
    private final FiltroRelatorioMedico filtroMedico;
    private final AcompanhamentoRevisao acompanhamento;
    private final MedicoCadastroService medicos;
    private final HospitalCadastroService hospitais;
    private final VersoesConciliacaoService versoes;

    public ConciliacaoVisitasController(LeitorPlanilhaVisitas leitor, ConciliadorVisitas conciliador,
            ExportadorRelatorioVisitas exportador, JdbcTemplate jdbc, ObjectMapper json, RevisaoVisitasService revisoes,
            FiltroRelatorioMedico filtroMedico, AcompanhamentoRevisao acompanhamento, MedicoCadastroService medicos, HospitalCadastroService hospitais,
            VersoesConciliacaoService versoes) {
        this.leitor = leitor;
        this.conciliador = conciliador;
        this.exportador = exportador;
        this.jdbc = jdbc;
        this.json = json;
        this.revisoes = revisoes;
        this.filtroMedico = filtroMedico;
        this.acompanhamento = acompanhamento;
        this.medicos = medicos;
        this.hospitais = hospitais;
        this.versoes = versoes;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestParam(required=false) String hospital, @RequestParam(required=false) Long hospitalId,
            @RequestParam(required=false) Long relatorioBaseId,@RequestParam(required=false) Long versaoBase,
            @RequestParam MultipartFile producao, @RequestParam MultipartFile faturamento) throws Exception {
        if(hospitalId!=null) {
            var cadastro=hospitais.buscar(hospitalId);
            if(!cadastro.ativo()) throw new IllegalArgumentException("Selecione um hospital ativo");
            hospital=cadastro.nome();
        }
        if (hospital==null || hospital.isBlank() || hospital.length() > 180) throw new IllegalArgumentException("Informe o hospital (ate 180 caracteres)");
        var linhasMedico=leitor.ler(producao,false);var linhasHospital=leitor.ler(faturamento,true);
        String hashMedico=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(producao.getBytes()));
        String hashHospital=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(faturamento.getBytes()));
        if(relatorioBaseId!=null) {
            if(versaoBase==null)throw new IllegalArgumentException("Informe a versao de revisao do relatorio base");
            return versoes.atualizar(relatorioBaseId,versaoBase,hospital,hospitalId,producao.getOriginalFilename(),faturamento.getOriginalFilename(),linhasMedico,linhasHospital,hashMedico,hashHospital);
        }
        var relatorio = conciliador.conciliar(hospital.trim(), producao.getOriginalFilename(), faturamento.getOriginalFilename(),
                linhasMedico,linhasHospital);
        if(hospitalId!=null) relatorio=new ConciliadorVisitas.Relatorio(relatorio.hospital(),relatorio.arquivoMedico(),relatorio.arquivoHospital(),
                relatorio.regraStatus(),relatorio.visitas(),relatorio.hospitalSemProducao(),relatorio.resumo(),relatorio.repasseCorrespondente(),hospitalId);
        Long id = jdbc.queryForObject("insert into conciliacao_visitas (relatorio,hash_producao,hash_hospital) values (?::jsonb,?,?) returning id",
                Long.class, json.writeValueAsString(relatorio),hashMedico,hashHospital);
        return Map.of("id", id, "relatorio", relatorio);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("""
                select id, criado_em, relatorio->>'hospital' as hospital,
                relatorio->>'arquivoMedico' as arquivo_medico,numero_versao,anterior_id,
                (select id from conciliacao_visitas seguinte where seguinte.anterior_id=conciliacao_visitas.id) as proxima_id
                from conciliacao_visitas order by id desc limit 100
                """);
    }

    @GetMapping("/{id}")
    public ConciliadorVisitas.Relatorio buscar(@PathVariable Long id) throws Exception {
        return revisoes.carregar(id).atual();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        revisoes.excluir(id);
    }

    @GetMapping("/{id}/acompanhamento")
    public AcompanhamentoRevisao.Resultado acompanhamento(@PathVariable Long id) throws Exception {
        return acompanhamento.analisar(revisoes.carregar(id));
    }

    @GetMapping("/{id}/visitas/{linha}/revisao")
    public RevisaoVisitasService.Detalhe detalhar(@PathVariable Long id, @PathVariable int linha,
            @RequestParam(required = false) String atendimento, @RequestParam(required = false) java.time.LocalDate data) throws Exception {
        return revisoes.detalhar(id, linha, atendimento, data);
    }

    @PostMapping("/{id}/visitas/{linha}/revisao")
    public RevisaoVisitasService.Resultado revisar(@PathVariable Long id, @PathVariable int linha,
            @Valid @RequestBody RevisaoVisitaRequest request) throws Exception {
        return revisoes.revisar(id, linha, request);
    }

    @GetMapping("/{id}/relatorio.xlsx")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id, @RequestParam(required = false) String medico,
            @RequestParam(required=false) Long medicoId) throws Exception {
        var estado = revisoes.carregar(id);
        if(medico!=null && medicoId!=null) throw new IllegalArgumentException("Use medico ou medicoId, nao ambos");
        if(medico!=null && medicos.nomeAmbiguo(medico)) throw new IllegalArgumentException("Nome ambiguo. Vincule as visitas ao cadastro e exporte por medicoId");
        var cadastro=medicoId==null?null:medicos.buscar(medicoId);
        var relatorio = medicoId!=null ? filtroMedico.filtrar(estado.atual(),medicoId,medicos)
                : medico == null ? estado.atual() : filtroMedico.filtrar(estado.atual(), medico);
        var nome=cadastro==null?medico:cadastro.nome();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=conciliacao-visitas-" + id + (nome == null ? "" : "-medico") + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(relatorio, estado.historico(), nome, cadastro));
    }
}
