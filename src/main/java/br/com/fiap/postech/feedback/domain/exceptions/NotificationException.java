package br.com.fiap.postech.feedback.domain.exceptions;

/**
 * Exceção lançada quando ocorre erro no envio de notificações.
 */
public class NotificationException extends RuntimeException {
    
    public NotificationException(String message) {
        super(message);
    }
    
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

