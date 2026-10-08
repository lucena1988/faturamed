package br.com.faturamed.conciliacao;

import br.com.faturamed.shared.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ConciliacaoVisitasController(LeitorPlanilhaVisitas leitor, ConciliadorVisitas conciliador,
            ExportadorRelatorioVisitas exportador, JdbcTemplate jdbc, ObjectMapper json) {
        this.leitor = leitor;
        this.conciliador = conciliador;
        this.exportador = exportador;
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestParam String hospital,
            @RequestParam MultipartFile producao, @RequestParam MultipartFile faturamento) throws Exception {
        if (hospital.isBlank() || hospital.length() > 180) throw new IllegalArgumentException("Informe o hospital (ate 180 caracteres)");
        var relatorio = conciliador.conciliar(hospital.trim(), producao.getOriginalFilename(), faturamento.getOriginalFilename(),
                leitor.ler(producao, false), leitor.ler(faturamento, true));
        Long id = jdbc.queryForObject("insert into conciliacao_visitas (relatorio) values (?::jsonb) returning id",
                Long.class, json.writeValueAsString(relatorio));
        return Map.of("id", id, "relatorio", relatorio);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("""
                select id, criado_em, relatorio->>'hospital' as hospital,
                relatorio->>'arquivoMedico' as arquivo_medico from conciliacao_visitas order by id desc limit 100
                """);
    }

    @GetMapping("/{id}")
    public ConciliadorVisitas.Relatorio buscar(@PathVariable Long id) throws Exception {
        var registros = jdbc.queryForList("select relatorio::text from conciliacao_visitas where id = ?", String.class, id);
        if (registros.isEmpty()) throw new RecursoNaoEncontradoException("Relatorio nao encontrado");
        return json.readValue(registros.getFirst(), ConciliadorVisitas.Relatorio.class);
    }

    @GetMapping("/{id}/relatorio.xlsx")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=conciliacao-visitas-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportar(buscar(id)));
    }
}
