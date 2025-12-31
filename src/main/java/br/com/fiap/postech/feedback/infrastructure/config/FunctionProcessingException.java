package br.com.fiap.postech.feedback.infrastructure.config;

/**
 * Exceção lançada quando ocorre erro no processamento de Azure Functions.
 * Representa falhas no processamento de mensagens de filas ou timers.
 */
public class FunctionProcessingException extends RuntimeException {
    
    public FunctionProcessingException(String message) {
        super(message);
    }
    
    public FunctionProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
