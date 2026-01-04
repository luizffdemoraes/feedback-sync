package br.com.fiap.postech.feedback.infrastructure.config;

import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    /**
     * Mapeia exceções para respostas HTTP apropriadas.
     * 
     * Ordem de verificação (do mais específico para o mais genérico):
     * 
     * 1. FeedbackDomainException → 400 BAD_REQUEST
     *    Mapeia erros de validação de negócio (ex: descrição vazia, nota inválida).
     *    Retorna mensagem de erro do domínio diretamente ao cliente.
     * 
     * 2. FeedbackPersistenceException → 500 INTERNAL_SERVER_ERROR
     *    Mapeia erros de persistência no Azure Table Storage.
     *    Oculta detalhes técnicos e retorna mensagem genérica de erro de conexão.
     * 
     * 3. NotificationException → 500 INTERNAL_SERVER_ERROR
     *    Mapeia erros ao enviar notificações críticas por email.
     *    Informa que o feedback foi salvo mas a notificação falhou.
     * 
     * 4. JsonProcessingException → 400 BAD_REQUEST
     *    Mapeia erros de deserialização JSON (corpo da requisição inválido ou malformado).
     *    Retorna mensagem genérica de erro de formato.
     * 
     * 5. IllegalArgumentException → 400 BAD_REQUEST
     *    Mapeia erros de validação de parâmetros (ex: Score fora do range 0-10).
     *    Retorna mensagem de erro de validação ao cliente.
     * 
     * 6. Exception (genérica) → 500 INTERNAL_SERVER_ERROR
     *    Mapeia qualquer erro não esperado ou não tratado.
     *    Retorna mensagem genérica de erro interno.
     * 
     * @param exception Exceção lançada durante o processamento da requisição
     * @return Response HTTP com status code e mensagem de erro apropriados
     */
    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof FeedbackDomainException) {
            logger.warn("Erro de domínio: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of(ERROR_KEY, exception.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (exception instanceof FeedbackPersistenceException) {
            logger.error("Erro de persistência: {}", exception.getMessage(), exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of(ERROR_KEY, "Erro ao salvar feedback. Verifique a conexão com o banco de dados."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (exception instanceof NotificationException) {
            logger.error("Erro de notificação: {}", exception.getMessage(), exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of(ERROR_KEY, "Erro ao enviar notificação. O feedback foi salvo, mas a notificação falhou."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (exception instanceof JsonProcessingException) {
            logger.warn("Erro ao processar JSON: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of(ERROR_KEY, "Corpo da requisição inválido ou malformado"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        if (exception instanceof IllegalArgumentException) {
            logger.warn("Dados inválidos: {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of(ERROR_KEY, "Dados inválidos: " + exception.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        logger.error("Erro interno ao processar requisição", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of(ERROR_KEY, "Erro interno ao processar requisição"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

