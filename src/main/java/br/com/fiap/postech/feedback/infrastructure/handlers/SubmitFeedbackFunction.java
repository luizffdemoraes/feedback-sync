package br.com.fiap.postech.feedback.infrastructure.handlers;


import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.inject.Inject;

import java.util.Optional;


public class SubmitFeedbackFunction {

    @Inject
    CreateFeedbackUseCase createFeedbackUseCase;

    @FunctionName("submitFeedback")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "avaliacao"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("📨 Recebendo feedback...");

        try {
            String json = request.getBody().orElse(null);

            if (json == null || json.isEmpty()) {
                return createErrorResponse(request, 400, "Corpo da requisição vazio");
            }

            FeedbackRequest feedbackRequest = parseJsonManual(json);

            // validação usando accessors do record
            if (feedbackRequest.score() < 0 || feedbackRequest.score() > 10) {
                return createErrorResponse(request, 400, "Nota deve estar entre 0 e 10");
            }

            FeedbackResponse result = createFeedbackUseCase.execute(feedbackRequest);

            if (feedbackRequest.score() <= 3) {
                context.getLogger().warning("⚠️ Feedback crítico recebido! Nota: " + feedbackRequest.score());
                // disparar evento para notifyAdmin aqui
            }

            context.getLogger().info("✅ Feedback processado com sucesso");

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("{\"id\": \"" + result.getId() + "\", \"status\": \"recebido\"}")
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return createErrorResponse(request, 400, "JSON inválido: " + e.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("❌ Erro: " + e.getMessage());
            return createErrorResponse(request, 500, "Erro interno: " + e.getMessage());
        }
    }

    // Parse simples: aceita campos em português no payload: descricao, nota, urgencia
    private FeedbackRequest parseJsonManual(String json) {
        String descricao = extractValue(json, "descricao");
        String notaStr = extractValue(json, "nota");
        String urgencia = extractValue(json, "urgencia"); // opcional

        if (descricao == null || notaStr == null) {
            throw new IllegalArgumentException("Campos 'descricao' e 'nota' são obrigatórios");
        }

        try {
            int nota = Integer.parseInt(notaStr);
            return new FeedbackRequest(descricao, nota, urgencia);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'nota' deve ser um número");
        }
    }

    private String extractValue(String json, String field) {
        String patternString = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(patternString);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        // número sem aspas
        String patternNumber = "\"" + field + "\"\\s*:\\s*([0-9]+)";
        p = java.util.regex.Pattern.compile(patternNumber);
        m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private HttpResponseMessage createErrorResponse(
            HttpRequestMessage<?> request, int status, String message) {

        return request.createResponseBuilder(HttpStatus.valueOf(status))
                .body("{\"error\": \"" + message + "\"}")
                .header("Content-Type", "application/json")
                .build();
    }
}