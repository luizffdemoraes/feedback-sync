package br.com.fiap.postech.feedback.infrastructure.mappers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.entities.Score;
import br.com.fiap.postech.feedback.domain.entities.Urgency;
import com.azure.data.tables.models.TableEntity;

import java.time.LocalDateTime;

/**
 * Mapper responsável por converter entre Feedback (entidade de domínio)
 * e TableEntity do Azure Table Storage.
 * 
 * Segue Single Responsibility Principle: apenas conversão de dados.
 * Isolado da lógica de persistência do gateway.
 */
public class TableStorageFeedbackMapper {

    private static final String PARTITION_KEY = "feedback";
    private static final String ID_PROPERTY = "id";
    private static final String DESCRIPTION_PROPERTY = "description";
    private static final String SCORE_PROPERTY = "score";
    private static final String URGENCY_PROPERTY = "urgency";
    private static final String CREATED_AT_PROPERTY = "createdAt";

    private TableStorageFeedbackMapper() {
        throw new UnsupportedOperationException("Classe utilitária - não deve ser instanciada");
    }

    /**
     * Converte Feedback (entidade de domínio) para TableEntity.
     * 
     * @param feedback Entidade de domínio
     * @return TableEntity para persistência no Table Storage
     */
    public static TableEntity toTableEntity(Feedback feedback) {
        TableEntity entity = new TableEntity(PARTITION_KEY, feedback.getId());
        
        entity.addProperty(ID_PROPERTY, feedback.getId());
        entity.addProperty(DESCRIPTION_PROPERTY, feedback.getDescription());
        entity.addProperty(SCORE_PROPERTY, feedback.getScore().getValue());
        entity.addProperty(URGENCY_PROPERTY, feedback.getUrgency().getValue());
        entity.addProperty(CREATED_AT_PROPERTY, feedback.getCreatedAt().toString());
        
        return entity;
    }

    /**
     * Converte TableEntity para Feedback (entidade de domínio).
     * 
     * @param entity TableEntity do Table Storage
     * @return Entidade de domínio Feedback
     */
    public static Feedback toEntity(TableEntity entity) {
        String id = entity.getRowKey();
        String description = getStringProperty(entity, DESCRIPTION_PROPERTY);
        Integer scoreValue = getIntegerProperty(entity, SCORE_PROPERTY);
        String urgencyValue = getStringProperty(entity, URGENCY_PROPERTY, "LOW");
        String createdAtStr = getStringProperty(entity, CREATED_AT_PROPERTY);
        
        Score score = new Score(scoreValue);
        Urgency urgency = Urgency.of(urgencyValue);
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
        
        Feedback feedback = new Feedback(description, score, urgency);
        feedback.setId(id);
        feedback.setCreatedAt(createdAt);
        
        return feedback;
    }

    private static String getStringProperty(TableEntity entity, String propertyName) {
        Object value = entity.getProperty(propertyName);
        return value != null ? value.toString() : null;
    }

    private static String getStringProperty(TableEntity entity, String propertyName, String defaultValue) {
        Object value = entity.getProperty(propertyName);
        return value != null ? value.toString() : defaultValue;
    }

    private static Integer getIntegerProperty(TableEntity entity, String propertyName) {
        Object value = entity.getProperty(propertyName);
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

