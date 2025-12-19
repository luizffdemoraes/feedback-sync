package br.com.fiap.postech.feedback.domain.exceptions;

/**
 * Exceção lançada quando ocorre erro na persistência de feedback.
 * Representa falhas na camada de infraestrutura ao salvar/recuperar dados.
 */
public class FeedbackPersistenceException extends RuntimeException {
    
    public FeedbackPersistenceException(String message) {
        super(message);
    }
    
    public FeedbackPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

