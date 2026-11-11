package br.com.faturamed.chat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatMencoesTest {
    static final Map<Long, String> PARTICIPANTES = Map.of(1L, "Ana Souza", 2L, "Bruno Lima", 3L, "Bruno Alves", 4L, "José Conceição");

    @Test void reconhecePrimeiroNomeUnicoENomeCompleto() {
        assertEquals(Set.of(1L), ChatService.detectarMencoes("Oi @Ana, viu o lote?", PARTICIPANTES, 2L));
        assertEquals(Set.of(2L), ChatService.detectarMencoes("@Bruno.Lima confere?", PARTICIPANTES, 1L));
        assertEquals(Set.of(4L), ChatService.detectarMencoes("@jose.conceicao sem acento também vale", PARTICIPANTES, 1L));
    }

    @Test void primeiroNomeRepetidoPrecisaDoNomeCompleto() {
        assertTrue(ChatService.detectarMencoes("@Bruno pode ver?", PARTICIPANTES, 1L).isEmpty());
        assertEquals(Set.of(3L), ChatService.detectarMencoes("@Bruno.Alves pode ver?", PARTICIPANTES, 1L));
    }

    @Test void todosAvisaOGrupoMenosQuemEscreveu() {
        assertEquals(Set.of(2L, 3L, 4L), ChatService.detectarMencoes("@todos reunião às 15h", PARTICIPANTES, 1L));
    }

    @Test void ignoraQuemNaoParticipaEmailEOProprioAutor() {
        assertTrue(ChatService.detectarMencoes("@Carla e contato@empresa.com", PARTICIPANTES, 1L).isEmpty());
        assertTrue(ChatService.detectarMencoes("@Ana eu mesma", PARTICIPANTES, 1L).isEmpty());
        assertTrue(ChatService.detectarMencoes(null, PARTICIPANTES, 1L).isEmpty());
    }
}
