package br.com.fiap.postech.feedback.domain.exceptions;

/**
 * Exceção base para erros relacionados ao domínio de Feedback.
 * Representa violações de regras de negócio.
 */
public class FeedbackDomainException extends RuntimeException {
    
    public FeedbackDomainException(String message) {
        super(message);
    }
    
    public FeedbackDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

