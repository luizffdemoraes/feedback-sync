package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.services.FeedbackRequestParser;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Azure Function que recebe feedbacks via HTTP POST.
 * 
 * Responsabilidade única: Receber requisições HTTP e delegar processamento ao use case.
 */
public class SubmitFeedbackFunction {

    private static final Logger logger = LoggerFactory.getLogger(SubmitFeedbackFunction.class);

    @Inject
    CreateFeedbackUseCase createFeedbackUseCase;

    @Inject
    FeedbackRequestParser requestParser;

    @Inject
    ObjectMapper objectMapper;

    @FunctionName("submitFeedback")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "avaliacao"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        logger.info("Recebendo feedback via HTTP trigger");

        try {
            String json = request.getBody().orElse(null);

            if (json == null || json.isBlank()) {
                logger.warn("Corpo da requisição vazio");
                return createErrorResponse(request, 400, "Corpo da requisição vazio");
            }

            // Usa serviço dedicado para parsing (Single Responsibility)
            FeedbackRequest feedbackRequest = requestParser.parse(json);

            // Validação de negócio delegada ao use case (Score Value Object valida internamente)
            FeedbackResponse result = createFeedbackUseCase.execute(feedbackRequest);

            logger.info("Feedback processado com sucesso, id={}", result.id());

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body(createSuccessResponse(result))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (FeedbackDomainException e) {
            logger.warn("Erro de domínio: {}", e.getMessage());
            return createErrorResponse(request, 400, e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Dados inválidos: {}", e.getMessage());
            return createErrorResponse(request, 400, "Dados inválidos: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Erro interno ao processar feedback", e);
            return createErrorResponse(request, 500, "Erro interno ao processar feedback");
        }
    }

    private String createSuccessResponse(FeedbackResponse response) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of(
                    "id", response.id(),
                    "status", "recebido"
                )
            );
        } catch (Exception e) {
            logger.error("Erro ao criar resposta JSON", e);
            return String.format("{\"id\": \"%s\", \"status\": \"recebido\"}", response.id());
        }
    }

    private HttpResponseMessage createErrorResponse(
            HttpRequestMessage<?> request, int status, String message) {

        try {
            String errorJson = objectMapper.writeValueAsString(
                java.util.Map.of("error", message)
            );
            return request.createResponseBuilder(HttpStatus.valueOf(status))
                    .body(errorJson)
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            logger.error("Erro ao criar resposta de erro", e);
            return request.createResponseBuilder(HttpStatus.valueOf(status))
                    .body("{\"error\": \"" + message + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
