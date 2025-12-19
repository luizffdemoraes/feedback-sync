package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Deserializador customizado para Feedback.
 * Necessário porque Feedback agora usa Value Objects (Score, Urgency).
 */
public class FeedbackDeserializer extends StdDeserializer<Feedback> {

    public FeedbackDeserializer() {
        this(null);
    }

    public FeedbackDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Feedback deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        
        String id = node.has("id") ? node.get("id").asText() : null;
        String description = node.has("description") ? node.get("description").asText() : "";
        int scoreValue = node.has("score") ? node.get("score").asInt() : 0;
        String urgencyValue = node.has("urgency") ? node.get("urgency").asText() : "LOW";
        
        LocalDateTime createdAt = null;
        if (node.has("createdAt")) {
            try {
                createdAt = LocalDateTime.parse(node.get("createdAt").asText());
            } catch (Exception e) {
                createdAt = LocalDateTime.now();
            }
        } else {
            createdAt = LocalDateTime.now();
        }
        
        Score score = new Score(scoreValue);
        Urgency urgency = Urgency.of(urgencyValue);
        
        Feedback feedback = new Feedback(description, score, urgency);
        if (id != null) {
            feedback.setId(id);
        }
        feedback.setCreatedAt(createdAt);
        
        return feedback;
    }
}

