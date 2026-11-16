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
    private final ExclusaoRelatorioService exclusoes;
    private final br.com.faturamed.seguranca.UsuarioService usuarios;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public ConciliacaoVisitasController(ExportadorRelatorioVisitas exportador, JdbcTemplate jdbc,
            RevisaoVisitasService revisoes, FiltroRelatorioMedico filtroMedico,
            AcompanhamentoRevisao acompanhamento, MedicoCadastroService medicos, ExclusaoRelatorioService exclusoes,
            br.com.faturamed.seguranca.UsuarioService usuarios, org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.exportador=exportador; this.jdbc=jdbc; this.revisoes=revisoes;
        this.filtroMedico=filtroMedico; this.acompanhamento=acompanhamento; this.medicos=medicos;
        this.exclusoes=exclusoes; this.usuarios=usuarios; this.encoder=encoder;
    }
    @GetMapping
    public List<Map<String,Object>> listar() {
        return jdbc.queryForList("""
                select id,criado_em,relatorio->>'hospital' as hospital,
                relatorio->>'arquivoMedico' as arquivo_medico,numero_versao,anterior_id,medico_producao_id,base_hospitalar,empresa_id,
                (select id from conciliacao_visitas seguinte where seguinte.anterior_id=conciliacao_visitas.id) as proxima_id
                from conciliacao_visitas order by id desc limit 100
                """);
    }
    @GetMapping("/{id}")
    public ConciliadorVisitas.Relatorio buscar(@PathVariable Long id) throws Exception { return revisoes.carregar(id).atual(); }
    public record ConfirmacaoExclusao(String senha) {}
    // Exclusao definitiva so com a senha de quem esta logado; erro conta como tentativa de login.
    @PostMapping("/{id}/exclusao")
    public ExclusaoRelatorioService.Resultado excluir(@PathVariable long id,@RequestBody ConfirmacaoExclusao dados,
            @org.springframework.security.core.annotation.AuthenticationPrincipal br.com.faturamed.seguranca.UsuarioAutenticado usuario) {
        var conta=usuarios.loadUserByUsername(usuario.email());
        if(!conta.isAccountNonLocked()) throw new org.springframework.security.access.AccessDeniedException("Acesso bloqueado temporariamente apos tentativas invalidas. Aguarde 15 minutos");
        if(dados==null||dados.senha()==null||dados.senha().isBlank()||!encoder.matches(dados.senha(),conta.getPassword())) {
            usuarios.falha(usuario.email());
            throw new IllegalArgumentException("Senha incorreta. O relatorio nao foi excluido");
        }
        usuarios.sucesso(usuario.id());
        return exclusoes.excluir(id,usuario);
    }
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
