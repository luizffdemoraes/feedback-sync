package br.com.fiap.postech.feedback.infrastructure.mappers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
import com.azure.data.tables.models.TableEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para TableStorageFeedbackMapper")
class TableStorageFeedbackMapperTest {

    @Test
    @DisplayName("Deve converter Feedback para TableEntity corretamente")
    void deveConverterFeedbackParaTableEntity() {
        Feedback feedback = new Feedback(
            "Aula muito boa",
            new Score(7),
            Urgency.of("MEDIUM")
        );
        feedback.setId("feedback-id-123");
        feedback.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));

        TableEntity entity = TableStorageFeedbackMapper.toTableEntity(feedback);

        assertEquals("feedback", entity.getPartitionKey());
        assertEquals("feedback-id-123", entity.getRowKey());
        assertEquals("feedback-id-123", entity.getProperty("id"));
        assertEquals("Aula muito boa", entity.getProperty("description"));
        assertEquals(7, entity.getProperty("score"));
        assertEquals("MEDIUM", entity.getProperty("urgency"));
        String createdAtStr = entity.getProperty("createdAt").toString();
        assertTrue(createdAtStr.startsWith("2024-01-15T10:30"), 
            "createdAt deve começar com 2024-01-15T10:30, mas foi: " + createdAtStr);
    }

    @Test
    @DisplayName("Deve converter TableEntity para Feedback corretamente")
    void deveConverterTableEntityParaFeedback() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula muito boa");
        entity.addProperty("score", 7);
        entity.addProperty("urgency", "MEDIUM");
        entity.addProperty("createdAt", "2024-01-15T10:30:00");

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals("feedback-id-123", feedback.getId());
        assertEquals("Aula muito boa", feedback.getDescription());
        assertEquals(7, feedback.getScore().getValue());
        assertEquals("MEDIUM", feedback.getUrgency().getValue());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve converter TableEntity com urgência padrão quando não informada")
    void deveConverterTableEntityComUrgenciaPadrao() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula boa");
        entity.addProperty("score", 5);
        entity.addProperty("createdAt", "2024-01-15T10:30:00");
        // Não adiciona urgency

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals("LOW", feedback.getUrgency().getValue());
    }

    @Test
    @DisplayName("Deve converter TableEntity com score como Integer")
    void deveConverterTableEntityComScoreComoInteger() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula boa");
        entity.addProperty("score", Integer.valueOf(8));
        entity.addProperty("urgency", "HIGH");
        entity.addProperty("createdAt", "2024-01-15T10:30:00");

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals(8, feedback.getScore().getValue());
    }

    @Test
    @DisplayName("Deve converter TableEntity com score como Number")
    void deveConverterTableEntityComScoreComoNumber() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula boa");
        entity.addProperty("score", Long.valueOf(9));
        entity.addProperty("urgency", "LOW");
        entity.addProperty("createdAt", "2024-01-15T10:30:00");

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals(9, feedback.getScore().getValue());
    }

    @Test
    @DisplayName("Deve converter TableEntity com score como String")
    void deveConverterTableEntityComScoreComoString() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula boa");
        entity.addProperty("score", "6");
        entity.addProperty("urgency", "MEDIUM");
        entity.addProperty("createdAt", "2024-01-15T10:30:00");

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals(6, feedback.getScore().getValue());
    }

    @Test
    @DisplayName("Deve converter TableEntity com score nulo retornando 0")
    void deveConverterTableEntityComScoreNulo() {
        TableEntity entity = new TableEntity("feedback", "feedback-id-123");
        entity.addProperty("id", "feedback-id-123");
        entity.addProperty("description", "Aula boa");
        entity.addProperty("score", null);
        entity.addProperty("urgency", "LOW");
        entity.addProperty("createdAt", "2024-01-15T10:30:00");

        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals(0, feedback.getScore().getValue());
    }

    @Test
    @DisplayName("Deve manter id e createdAt ao converter ida e volta")
    void deveManterIdECreatedAtAoConverterIdaEVolta() {
        Feedback feedbackOriginal = new Feedback(
            "Aula teste",
            new Score(8),
            Urgency.of("HIGH")
        );
        String idOriginal = "id-customizado-456";
        LocalDateTime createdAtOriginal = LocalDateTime.of(2024, 2, 20, 14, 45, 30);
        feedbackOriginal.setId(idOriginal);
        feedbackOriginal.setCreatedAt(createdAtOriginal);

        TableEntity entity = TableStorageFeedbackMapper.toTableEntity(feedbackOriginal);
        Feedback feedbackReconstruido = TableStorageFeedbackMapper.toEntity(entity);

        assertEquals(idOriginal, feedbackReconstruido.getId());
        assertEquals(createdAtOriginal, feedbackReconstruido.getCreatedAt());
        assertEquals(feedbackOriginal.getDescription(), feedbackReconstruido.getDescription());
        assertEquals(feedbackOriginal.getScore().getValue(), feedbackReconstruido.getScore().getValue());
        assertEquals(feedbackOriginal.getUrgency().getValue(), feedbackReconstruido.getUrgency().getValue());
    }
}
