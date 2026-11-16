package br.com.faturamed.chat;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Conexoes SSE abertas do chat: entrega eventos em tempo real e indica quem esta online. */
@Component
public class ChatEventos {
    static final long TEMPO_CONEXAO_MS = 30 * 60 * 1000L;
    private final Map<Long, Set<SseEmitter>> conexoes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pulso = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-pulso"); t.setDaemon(true); return t;
    });

    public ChatEventos() {
        // comentario periodico mantem a conexao viva atraves de proxies
        pulso.scheduleAtFixedRate(this::pulsar, 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter conectar(long usuarioId) {
        SseEmitter emitter = new SseEmitter(TEMPO_CONEXAO_MS);
        boolean estavaOffline = !online(usuarioId);
        conexoes.computeIfAbsent(usuarioId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remover = () -> desconectar(usuarioId, emitter);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover.run());
        enviar(emitter, "pronto", Map.of("usuarioId", usuarioId));
        if (estavaOffline) difundir("presenca", Map.of("usuarioId", usuarioId, "online", true), usuarioId);
        return emitter;
    }

    private void desconectar(long usuarioId, SseEmitter emitter) {
        Set<SseEmitter> lista = conexoes.get(usuarioId);
        if (lista == null || !lista.remove(emitter)) return;
        if (lista.isEmpty() && conexoes.remove(usuarioId, lista))
            difundir("presenca", Map.of("usuarioId", usuarioId, "online", false), usuarioId);
    }

    public boolean online(long usuarioId) {
        Set<SseEmitter> lista = conexoes.get(usuarioId);
        return lista != null && !lista.isEmpty();
    }

    public void enviar(Collection<Long> usuarios, String evento, Object dados) {
        for (Long id : usuarios) {
            Set<SseEmitter> lista = conexoes.get(id);
            if (lista != null) for (SseEmitter e : List.copyOf(lista)) enviar(e, evento, dados);
        }
    }

    /** Evento para todo mundo conectado (ex.: mudanca de status de alguem). */
    public void difundirTodos(String evento, Object dados) {
        enviar(List.copyOf(conexoes.keySet()), evento, dados);
    }

    private void difundir(String evento, Object dados, long exceto) {
        List<Long> outros = conexoes.keySet().stream().filter(id -> id != exceto).toList();
        enviar(outros, evento, dados);
    }

    private void enviar(SseEmitter emitter, String evento, Object dados) {
        try {
            emitter.send(SseEmitter.event().name(evento).data(dados));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    private void pulsar() {
        conexoes.values().forEach(lista -> List.copyOf(lista).forEach(e -> {
            try { e.send(SseEmitter.event().comment("pulso")); }
            catch (IOException | IllegalStateException ex) { e.completeWithError(ex); }
        }));
    }

    @PreDestroy void encerrar() {
        pulso.shutdownNow();
        conexoes.values().forEach(lista -> lista.forEach(SseEmitter::complete));
    }
}
