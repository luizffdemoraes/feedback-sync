package br.com.fiap.postech.feedback.infrastructure.mappers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mapper responsável por converter entre Feedback (entidade de domínio)
 * e representação de documento do Cosmos DB (Map).
 * 
 * Segue Single Responsibility Principle: apenas conversão de dados.
 * Isolado da lógica de persistência do gateway.
 */
public class CosmosFeedbackMapper {

    private CosmosFeedbackMapper() {
        // Classe utilitária - não deve ser instanciada
    }

    /**
     * Converte Feedback (entidade de domínio) para Map (documento Cosmos DB).
     * 
     * @param feedback Entidade de domínio
     * @return Map representando documento do Cosmos DB
     */
    public static Map<String, Object> toDocument(Feedback feedback) {
        return Map.of(
            "id", feedback.getId(),
            "description", feedback.getDescription(),
            "score", feedback.getScore().getValue(),
            "urgency", feedback.getUrgency().getValue(),
            "createdAt", feedback.getCreatedAt().toString()
        );
    }

    /**
     * Converte Map (documento Cosmos DB) para Feedback (entidade de domínio).
     * 
     * @param document Documento do Cosmos DB
     * @return Entidade de domínio Feedback
     */
    public static Feedback toEntity(Map<String, Object> document) {
        String id = (String) document.get("id");
        String description = (String) document.get("description");
        Integer scoreValue = getIntegerValue(document.get("score"));
        String urgencyValue = document.get("urgency") != null 
            ? document.get("urgency").toString() 
            : "LOW";
        String createdAtStr = (String) document.get("createdAt");

        Score score = new Score(scoreValue);
        Urgency urgency = Urgency.of(urgencyValue);
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);

        Feedback feedback = new Feedback(description, score, urgency);
        feedback.setId(id);
        feedback.setCreatedAt(createdAt);

        return feedback;
    }

    private static Integer getIntegerValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
}

