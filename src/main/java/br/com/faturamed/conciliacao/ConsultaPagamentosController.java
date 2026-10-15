package br.com.faturamed.conciliacao;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conciliacoes/visitas/{id}/recebimentos/pagamentos")
public class ConsultaPagamentosController {
    private final ConsultaPagamentosService service;
    public ConsultaPagamentosController(ConsultaPagamentosService service){this.service=service;}
    @GetMapping public ConsultaPagamentosService.Resultado listar(@PathVariable long id,@RequestParam(defaultValue="") String referencia,
            @RequestParam(required=false) Long medicoId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue="TODOS") ConsultaPagamentosService.Situacao situacao) throws Exception {return service.listar(id,referencia,medicoId,de,ate,situacao);}
    @GetMapping("/{pagamento}") public ConsultaPagamentosService.Pagamento detalhar(@PathVariable long id,@PathVariable long pagamento) throws Exception {return service.detalhar(id,pagamento);}
}
