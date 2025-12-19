package br.com.fiap.postech.feedback.infrastructure.handlers;


import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


public class SubmitFeedbackFunction {

    private static final Logger logger = LoggerFactory.getLogger(SubmitFeedbackFunction.class);

    @Inject
    CreateFeedbackUseCase createFeedbackUseCase;

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

            if (json == null || json.isEmpty()) {
                logger.warn("Corpo da requisição vazio");
                return createErrorResponse(request, 400, "Corpo da requisição vazio");
            }

            FeedbackRequest feedbackRequest = parseJsonWithObjectMapper(json);

            // validação usando accessors do record
            if (feedbackRequest.score() == null || feedbackRequest.score() < 0 || feedbackRequest.score() > 10) {
                logger.warn("Nota inválida: {}", feedbackRequest.score());
                return createErrorResponse(request, 400, "Nota deve estar entre 0 e 10");
            }

            FeedbackResponse result = createFeedbackUseCase.execute(feedbackRequest);

            logger.info("Feedback processado com sucesso, id={}", result.getId());

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("{\"id\": \"" + result.getId() + "\", \"status\": \"recebido\"}")
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            logger.warn("JSON inválido: {}", e.getMessage());
            return createErrorResponse(request, 400, "JSON inválido: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Erro interno ao processar feedback", e);
            return createErrorResponse(request, 500, "Erro interno: " + e.getMessage());
        }
    }

    // Usa ObjectMapper para suportar campos em inglês e português
    private FeedbackRequest parseJsonWithObjectMapper(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            String description = null;
            if (node.hasNonNull("description")) description = node.get("description").asText();
            else if (node.hasNonNull("descricao")) description = node.get("descricao").asText();

            Integer score = null;
            if (node.hasNonNull("score")) score = node.get("score").asInt();
            else if (node.hasNonNull("nota")) score = node.get("nota").asInt();

            String urgency = null;
            if (node.hasNonNull("urgency")) urgency = node.get("urgency").asText();
            else if (node.hasNonNull("urgencia")) urgency = node.get("urgencia").asText();

            if (description == null || score == null) {
                throw new IllegalArgumentException("Campos 'description/descricao' e 'score/nota' são obrigatórios");
            }

            return new FeedbackRequest(description, score, urgency);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao parsear JSON", e);
        }
    }

    private HttpResponseMessage createErrorResponse(
            HttpRequestMessage<?> request, int status, String message) {

        return request.createResponseBuilder(HttpStatus.valueOf(status))
                .body("{\"error\": \"" + message + "\"}")
                .header("Content-Type", "application/json")
                .build();
    }
}
