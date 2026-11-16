package br.com.faturamed.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** "Visto por ultimo": a presenca grava o instante ao entrar e ao sair do chat (sem banco). */
class ChatPresencaTest {
    @Test
    void conectarGravaVistoEOnline() {
        var jdbc = mock(JdbcTemplate.class);
        var eventos = new ChatEventos();
        eventos.setJdbc(jdbc);
        var emitter = eventos.conectar(7L);
        assertThat(eventos.online(7L)).isTrue();
        verify(jdbc).update(contains("insert into chat_presenca"), eq(7L));
        emitter.complete();
    }

    @Test
    void semBancoOuComFalhaNaoDerrubaOChat() {
        var eventos = new ChatEventos();
        eventos.registrarVisto(List.of(1L)); // sem JdbcTemplate: ignora
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("tabela ausente"));
        eventos.setJdbc(jdbc);
        eventos.registrarVisto(List.of(1L));
        eventos.registrarVisto(List.of());
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }
}
