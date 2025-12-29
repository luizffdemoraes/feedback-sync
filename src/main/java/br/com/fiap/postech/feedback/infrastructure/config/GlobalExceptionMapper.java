package br.com.fiap.postech.feedback.infrastructure.config;

import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exception Mapper global para tratamento de exceções na camada de infraestrutura HTTP.
 * 
 * Segue Clean Architecture:
 * - Configuração global da framework (JAX-RS)
 * - Converte exceções de domínio/aplicação em respostas HTTP
 * - Mantém controllers thin (sem try-catch)
 * - Centraliza tratamento de erros
 * 
 * Localização: infrastructure/config/ (configurações globais da infraestrutura)
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionMapper.class);
    private static final String ERROR_KEY = "error";

    @Override
    public Response toResponse(Exception exception) {
        // Erros de domínio (validação de negócio)
        if (exception instanceof FeedbackDomainException) {
            logger.warn("Erro de domínio: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of(ERROR_KEY, exception.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        // Erros de persistência (Cosmos DB)
        if (exception instanceof FeedbackPersistenceException) {
            logger.error("Erro de persistência: {}", exception.getMessage(), exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of(ERROR_KEY, "Erro ao salvar feedback. Verifique a conexão com o banco de dados."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        // Erros de notificação (Service Bus)
        if (exception instanceof NotificationException) {
            logger.error("Erro de notificação: {}", exception.getMessage(), exception);
            // Não falha a requisição se a notificação falhar, apenas loga
            // O feedback já foi salvo, então retornamos sucesso
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of(ERROR_KEY, "Erro ao enviar notificação. O feedback foi salvo, mas a notificação falhou."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        // Erros de validação (dados inválidos)
        if (exception instanceof IllegalArgumentException) {
            logger.warn("Dados inválidos: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of(ERROR_KEY, "Dados inválidos: " + exception.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        // Erro interno não esperado
        logger.error("Erro interno ao processar requisição", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of(ERROR_KEY, "Erro interno ao processar requisição"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

