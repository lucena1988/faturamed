package br.com.faturamed.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import br.com.faturamed.api.HealthController;
import br.com.faturamed.conciliacao.ConflitoRevisaoException;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/** Respostas de erro padronizadas da API e o health check. */
class ApiExceptionHandlerTest {
    private final ApiExceptionHandler h = new ApiExceptionHandler();

    @Test
    void cadaErroViraStatusEMensagem() {
        assertThat(h.handleConflitoRevisao(new ConflitoRevisaoException("c")).getStatusCode().value()).isEqualTo(409);
        assertThat(h.handleNotFound(new RecursoNaoEncontradoException("x")).getStatusCode().value()).isEqualTo(404);
        assertThat(h.handleIllegalArgument(new IllegalArgumentException("y")).getBody()).containsEntry("erro", "Valor invalido: y");
        var vazio = mock(MethodArgumentNotValidException.class);
        when(vazio.getBindingResult()).thenReturn(new BeanPropertyBindingResult(new Object(), "o"));
        assertThat(h.handleValidation(vazio).getBody()).containsEntry("erro", "Dados invalidos");
        var br = new BeanPropertyBindingResult(new Object(), "o");
        br.addError(new FieldError("o", "nome", "obrigatorio"));
        var comCampo = mock(MethodArgumentNotValidException.class);
        when(comCampo.getBindingResult()).thenReturn(br);
        assertThat(h.handleValidation(comCampo).getBody()).containsEntry("erro", "nome: obrigatorio");
    }

    @Test
    void healthDizQueEstaNoAr() {
        assertThat(new HealthController().health()).containsEntry("status", "UP").containsEntry("service", "faturamed").containsKey("checkedAt");
    }
}
