package br.com.faturamed.conciliacao;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflitoRevisaoException extends RuntimeException {
    public ConflitoRevisaoException(String mensagem) { super(mensagem); }
}
