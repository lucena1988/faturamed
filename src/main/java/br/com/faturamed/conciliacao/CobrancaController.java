package br.com.faturamed.conciliacao;

import java.time.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}")
public class CobrancaController {
    private final RevisaoVisitasService revisoes;
    private final FilaCobranca fila;
    private final ExportadorRelatorioVisitas exportador;
    private final JdbcTemplate jdbc;
    private final AcompanhamentoCobrancaService acompanhamento;
    public CobrancaController(RevisaoVisitasService revisoes,FilaCobranca fila,ExportadorRelatorioVisitas exportador,JdbcTemplate jdbc) {
        this(revisoes,fila,exportador,jdbc,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CobrancaController(RevisaoVisitasService revisoes,FilaCobranca fila,ExportadorRelatorioVisitas exportador,JdbcTemplate jdbc,AcompanhamentoCobrancaService acompanhamento) {
        this.revisoes=revisoes;this.fila=fila;this.exportador=exportador;this.jdbc=jdbc;this.acompanhamento=acompanhamento;
    }
    public FilaCobranca.Resultado listar(long id,int minDias,String medico) throws Exception { return listar(id,minDias,medico,PrioridadesCobranca.Filtro.TODAS); }
    @GetMapping("/cobranca")
    public FilaCobranca.Resultado listar(@PathVariable long id,@RequestParam(defaultValue="0") int minDias,
            @RequestParam(defaultValue="") String medico,@RequestParam(defaultValue="TODAS") PrioridadesCobranca.Filtro prioridade) throws Exception {
        var estado=revisoes.carregar(id);
        if(!jdbc.queryForList("select id from conciliacao_visitas where anterior_id=?",id).isEmpty())
            throw new ConflitoRevisaoException("Selecione a versao mais recente para consultar a fila de cobranca");
        var resultado=fila.analisar(estado.atual(),LocalDate.now(ZoneId.of("America/Sao_Paulo")),minDias,medico);
        return new PrioridadesCobranca().aplicar(resultado,acompanhamento==null?java.util.List.of():acompanhamento.listar(id),prioridade);
    }
    public ResponseEntity<byte[]> exportar(long id,int minDias,String medico) throws Exception { return exportar(id,minDias,medico,PrioridadesCobranca.Filtro.TODAS); }
    @GetMapping("/cobranca.xlsx")
    public ResponseEntity<byte[]> exportar(@PathVariable long id,@RequestParam(defaultValue="0") int minDias,
            @RequestParam(defaultValue="") String medico,@RequestParam(defaultValue="TODAS") PrioridadesCobranca.Filtro prioridade) throws Exception {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=cobranca-hospital-"+id+".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportador.exportarCobranca(id,listar(id,minDias,medico,prioridade)));
    }
}
