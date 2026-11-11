package br.com.faturamed.seguranca;

import br.com.faturamed.chat.ChatAnexos;
import br.com.faturamed.shared.RecursoNaoEncontradoException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Foto de perfil: cada pessoa troca a propria; a equipe interna ve a foto dos colegas (ex.: no chat). */
@RestController
public class FotoUsuarioController {
    static final int LIMITE_BYTES = 1024 * 1024;
    private final JdbcTemplate jdbc;

    public FotoUsuarioController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PutMapping(path = "/api/auth/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> trocar(@RequestParam MultipartFile arquivo, @AuthenticationPrincipal UsuarioAutenticado user) throws Exception {
        if (arquivo.isEmpty()) throw new IllegalArgumentException("Escolha uma imagem");
        if (arquivo.getSize() > LIMITE_BYTES) throw new IllegalArgumentException("A foto deve ter até 1 MB");
        var img = ChatAnexos.validar(arquivo.getOriginalFilename(), arquivo.getBytes());
        if (!img.imagem() || img.tipo().equals("image/gif")) throw new IllegalArgumentException("Use uma imagem PNG, JPG ou WEBP");
        jdbc.update("""
            insert into usuario_foto(usuario_id,tipo,conteudo,atualizado_em) values (?,?,?,now())
            on conflict (usuario_id) do update set tipo=excluded.tipo, conteudo=excluded.conteudo, atualizado_em=now()
            """, user.id(), img.tipo(), img.conteudo());
        return Map.of("foto", versao(user.id()));
    }

    @DeleteMapping("/api/auth/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal UsuarioAutenticado user) {
        jdbc.update("delete from usuario_foto where usuario_id=?", user.id());
    }

    @GetMapping("/api/auth/foto")
    public ResponseEntity<byte[]> minha(@AuthenticationPrincipal UsuarioAutenticado user) {
        return foto(user.id());
    }

    /** Foto de um colega; a rota /api/usuarios/** ja e restrita a equipe interna (ADMIN, DEV, OPERADOR). */
    @GetMapping("/api/usuarios/{id}/foto")
    public ResponseEntity<byte[]> doUsuario(@PathVariable long id) {
        return foto(id);
    }

    /** Versao da foto (segundos da ultima troca) para o navegador saber quando buscar de novo; null sem foto. */
    public Long versao(long usuarioId) {
        return jdbc.query("select extract(epoch from atualizado_em)::bigint v from usuario_foto where usuario_id=?",
                (r, n) -> r.getLong("v"), usuarioId).stream().findFirst().orElse(null);
    }

    private ResponseEntity<byte[]> foto(long usuarioId) {
        var linhas = jdbc.query("select tipo, conteudo from usuario_foto where usuario_id=?",
                (r, n) -> Map.entry(r.getString("tipo"), r.getBytes("conteudo")), usuarioId);
        if (linhas.isEmpty()) throw new RecursoNaoEncontradoException("Sem foto");
        var f = linhas.getFirst();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getKey()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.getValue());
    }
}
