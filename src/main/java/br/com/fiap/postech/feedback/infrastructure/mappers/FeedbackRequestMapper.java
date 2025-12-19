package br.com.fiap.postech.feedback.infrastructure.mappers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mapper responsável por converter JSON HTTP para FeedbackRequest.
 * 
 * Responsabilidade única: Parsing de JSON HTTP (específico de infraestrutura).
 * Segue Clean Architecture: parsing HTTP fica na camada de infraestrutura.
 */
@ApplicationScoped
public class FeedbackRequestMapper {

    private final ObjectMapper objectMapper;

    @Inject
    public FeedbackRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Faz o parsing de uma string JSON para FeedbackRequest.
     * Suporta campos em português e inglês.
     * 
     * @param json String JSON a ser parseada
     * @return FeedbackRequest parseado e validado
     * @throws FeedbackDomainException se o JSON for inválido ou campos obrigatórios estiverem ausentes
     */
    public FeedbackRequest toRequest(String json) {
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

