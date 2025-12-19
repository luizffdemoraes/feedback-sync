package br.com.fiap.postech.feedback.application.services;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serviço responsável por fazer o parsing de JSON para FeedbackRequest.
 * Extrai a lógica de parsing do handler, seguindo Single Responsibility Principle.
 */
@ApplicationScoped
public class FeedbackRequestParser {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackRequestParser.class);

    @Inject
    ObjectMapper objectMapper;

    /**
     * Faz o parsing de uma string JSON para FeedbackRequest.
     * Suporta campos em português e inglês.
     * 
     * @param json String JSON a ser parseada
     * @return FeedbackRequest parseado e validado
     * @throws FeedbackDomainException se o JSON for inválido ou campos obrigatórios estiverem ausentes
     */
    public FeedbackRequest parse(String json) {
        if (json == null || json.isBlank()) {
            throw new FeedbackDomainException("JSON não pode ser nulo ou vazio");
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            String description = extractField(node, "description", "descricao", true);
            Integer score = extractIntField(node, "score", "nota", true);
            String urgency = extractField(node, "urgency", "urgencia", false);

            return new FeedbackRequest(description, score, urgency);

        } catch (FeedbackDomainException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Erro ao parsear JSON: {}", e.getMessage(), e);
            throw new FeedbackDomainException("Erro ao parsear JSON: " + e.getMessage(), e);
        }
    }

    private String extractField(JsonNode node, String englishKey, String portugueseKey, boolean required) {
        if (node.hasNonNull(englishKey)) {
            return node.get(englishKey).asText();
        }
        if (node.hasNonNull(portugueseKey)) {
            return node.get(portugueseKey).asText();
        }
        if (required) {
            throw new FeedbackDomainException(
                String.format("Campo obrigatório '%s' ou '%s' não encontrado", englishKey, portugueseKey)
            );
        }
        return null;
    }

    private Integer extractIntField(JsonNode node, String englishKey, String portugueseKey, boolean required) {
        if (node.hasNonNull(englishKey)) {
            return node.get(englishKey).asInt();
        }
        if (node.hasNonNull(portugueseKey)) {
            return node.get(portugueseKey).asInt();
        }
        if (required) {
            throw new FeedbackDomainException(
                String.format("Campo obrigatório '%s' ou '%s' não encontrado", englishKey, portugueseKey)
            );
        }
        return null;
    }
}

