package br.com.faturamed.chat;

import br.com.faturamed.seguranca.UsuarioAutenticado;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    private final ChatEventos eventos;
    private final String dominioVideo;

    public ChatController(ChatService service, ChatEventos eventos, @Value("${faturamed.video.dominio:}") String dominioVideo) {
        this.service = service;
        this.eventos = eventos;
        this.dominioVideo = VideoConfig.normalizar(dominioVideo);
    }

    @GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(@AuthenticationPrincipal UsuarioAutenticado user) {
        return eventos.conectar(user.id());
    }

    @GetMapping("/video")
    public Map<String, Object> video() {
        return Map.of("ativo", !dominioVideo.isEmpty(), "dominio", dominioVideo);
    }

    @GetMapping("/contatos")
    public List<ChatService.Contato> contatos(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.contatos(user);
    }

    @GetMapping("/conversas")
    public List<ChatService.Conversa> conversas(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.conversas(user);
    }

    @GetMapping("/nao-lidas")
    public ChatService.NaoLidas naoLidas(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.resumoNaoLidas(user);
    }

    @PutMapping("/conversas/{id}/silencio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void silenciar(@PathVariable long id, @RequestBody ChatService.Silencio dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        service.silenciar(id, dados.silenciado(), user);
    }

    @PostMapping("/conversas")
    public ChatService.Conversa abrir(@RequestBody ChatService.NovaConversa dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.abrirDireta(dados.usuarioId(), user);
    }

    @PostMapping("/grupos")
    public ChatService.Conversa criarGrupo(@RequestBody ChatService.NovoGrupo dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.criarGrupo(dados, user);
    }

    @GetMapping("/conversas/{id}/participantes")
    public List<ChatService.Participante> participantes(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.participantesDoGrupo(id, user);
    }

    @PostMapping("/conversas/{id}/participantes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adicionar(@PathVariable long id, @RequestBody ChatService.NovoParticipante dados,
                          @AuthenticationPrincipal UsuarioAutenticado user) {
        service.adicionarParticipante(id, dados.usuarioId(), user);
    }

    @DeleteMapping("/conversas/{id}/participantes/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable long id, @PathVariable long usuarioId, @AuthenticationPrincipal UsuarioAutenticado user) {
        service.removerParticipante(id, usuarioId, user);
    }

    @GetMapping("/conversas/{id}/mensagens")
    public ChatService.Pagina mensagens(@PathVariable long id, @RequestParam(required = false) Long antesDe,
                                        @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.mensagens(id, antesDe, user);
    }

    @PostMapping(path = "/conversas/{id}/mensagens", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.Mensagem enviar(@PathVariable long id, @RequestBody ChatService.NovaMensagem dados,
                                       @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.enviar(id, dados.texto(), List.of(), dados.respondeA(), user);
    }

    @PostMapping(path = "/conversas/{id}/mensagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.Mensagem enviarComAnexos(@PathVariable long id, @RequestParam(required = false) String texto,
                                                @RequestParam(required = false) Long respondeA,
                                                @RequestParam(required = false) List<org.springframework.web.multipart.MultipartFile> arquivos,
                                                @AuthenticationPrincipal UsuarioAutenticado user) throws java.io.IOException {
        return service.enviar(id, texto, ChatAnexos.validar(arquivos), respondeA, user);
    }

    @PutMapping("/mensagens/{mensagemId}")
    public ChatService.Mensagem editar(@PathVariable long mensagemId, @RequestBody ChatService.Edicao dados,
                                       @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.editar(mensagemId, dados.texto(), user);
    }

    @DeleteMapping("/mensagens/{mensagemId}")
    public ChatService.Mensagem apagar(@PathVariable long mensagemId, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.apagar(mensagemId, user);
    }

    @PostMapping("/mensagens/{mensagemId}/reacoes")
    public ChatService.Mensagem reagir(@PathVariable long mensagemId, @RequestBody ChatService.NovaReacao dados,
                                       @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.reagir(mensagemId, dados.emoji(), user);
    }

    @PutMapping("/conversas/{id}/fixada")
    public ChatService.Conversa fixar(@PathVariable long id, @RequestBody ChatService.NovaFixada dados,
                                      @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.fixar(id, dados.mensagemId(), user);
    }

    @GetMapping("/busca")
    public List<ChatService.ResultadoBusca> buscar(@RequestParam String q, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.buscar(q, user);
    }

    @GetMapping("/status")
    public ChatService.MeuStatus meuStatus(@AuthenticationPrincipal UsuarioAutenticado user) {
        return service.meuStatus(user);
    }

    @PutMapping("/status")
    public ChatService.MeuStatus definirStatus(@RequestBody ChatService.MeuStatus dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.definirStatus(dados, user);
    }

    @GetMapping("/configuracao")
    public ChatService.Configuracao configuracao() {
        return service.configuracao();
    }

    @PutMapping("/configuracao")
    public ChatService.Configuracao definirConfiguracao(@RequestBody ChatService.Configuracao dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.definirConfiguracao(dados, user);
    }

    @GetMapping("/conversas/{id}/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) throws java.io.IOException {
        String nome = service.nomeDaConversa(id, user);
        byte[] pdf = ChatExportadorPdf.gerar(nome, service.historicoCompleto(id, user), user.nome());
        var disposicao = org.springframework.http.ContentDisposition.attachment()
                .filename("chat-" + ChatAnexos.nomeSeguro(nome).replaceAll("\\s+", "-") + ".pdf", java.nio.charset.StandardCharsets.UTF_8).build();
        return org.springframework.http.ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(pdf);
    }

    @GetMapping("/anexos/{anexoId}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> anexo(@PathVariable long anexoId,
                                                                 @RequestParam(defaultValue = "false") boolean download,
                                                                 @AuthenticationPrincipal UsuarioAutenticado user) {
        var arq = service.baixarAnexo(anexoId, user);
        boolean inline = !download && ChatAnexos.exibirNoNavegador(arq.tipo());
        var disposicao = (inline ? org.springframework.http.ContentDisposition.inline() : org.springframework.http.ContentDisposition.attachment())
                .filename(arq.nome(), java.nio.charset.StandardCharsets.UTF_8).build();
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arq.tipo()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .body(new org.springframework.core.io.ByteArrayResource(arq.conteudo()));
    }

    @PostMapping("/conversas/{id}/chamada")
    public ChatService.Chamada chamada(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        return service.iniciarChamada(id, dominioVideo, user);
    }

    @PostMapping("/conversas/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ler(@PathVariable long id, @RequestBody ChatService.Leitura dados, @AuthenticationPrincipal UsuarioAutenticado user) {
        service.marcarLida(id, dados.ateId(), user);
    }

    @PostMapping("/conversas/{id}/digitando")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void digitando(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado user) {
        service.digitando(id, user);
    }
}
