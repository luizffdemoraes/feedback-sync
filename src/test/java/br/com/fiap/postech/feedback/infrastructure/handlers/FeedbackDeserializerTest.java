package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FeedbackDeserializer")
class FeedbackDeserializerTest {

    private FeedbackDeserializer deserializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deserializer = new FeedbackDeserializer();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Deve deserializar JSON completo para Feedback")
    void deveDeserializarJsonCompletoParaFeedback() throws IOException {
        String json = """
            {
                "id": "feedback-id-123",
                "description": "Aula muito boa",
                "score": 7,
                "urgency": "MEDIUM",
                "createdAt": "2024-01-15T10:30:00"
            }
            """;

        JsonParser parser = objectMapper.createParser(json);
        DeserializationContext context = objectMapper.getDeserializationContext();
        
        Feedback feedback = deserializer.deserialize(parser, context);

        assertEquals("feedback-id-123", feedback.getId());
        assertEquals("Aula muito boa", feedback.getDescription());
        assertEquals(7, feedback.getScore().getValue());
        assertEquals("MEDIUM", feedback.getUrgency().getValue());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve deserializar JSON sem ID")
    void deveDeserializarJsonSemId() throws IOException {
        String json = """
            {
                "description": "Aula boa",
                "score": 5,
                "urgency": "LOW"
            }
            """;

        JsonParser parser = objectMapper.createParser(json);
        DeserializationContext context = objectMapper.getDeserializationContext();
        
        Feedback feedback = deserializer.deserialize(parser, context);

        assertNotNull(feedback.getId());
        assertEquals("Aula boa", feedback.getDescription());
        assertEquals(5, feedback.getScore().getValue());
        assertEquals("LOW", feedback.getUrgency().getValue());
        assertNotNull(feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve usar valores padrão quando campos estão ausentes")
    void deveUsarValoresPadraoQuandoCamposEstaoAusentes() throws IOException {
        String json = """
            {
                "description": "Teste"
            }
            """;

        JsonParser parser = objectMapper.createParser(json);
        DeserializationContext context = objectMapper.getDeserializationContext();
        
        Feedback feedback = deserializer.deserialize(parser, context);

        assertEquals("Teste", feedback.getDescription());
        assertEquals(0, feedback.getScore().getValue());
        assertEquals("LOW", feedback.getUrgency().getValue());
        assertNotNull(feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve usar createdAt atual quando campo está ausente")
    void deveUsarCreatedAtAtualQuandoCampoEstaAusente() throws IOException {
        String json = """
            {
                "description": "Teste",
                "score": 8
            }
            """;

        JsonParser parser = objectMapper.createParser(json);
        DeserializationContext context = objectMapper.getDeserializationContext();
        
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        Feedback feedback = deserializer.deserialize(parser, context);
        LocalDateTime depois = LocalDateTime.now().plusSeconds(1);

        assertNotNull(feedback.getCreatedAt());
        assertTrue(feedback.getCreatedAt().isAfter(antes) || feedback.getCreatedAt().isEqual(antes));
        assertTrue(feedback.getCreatedAt().isBefore(depois) || feedback.getCreatedAt().isEqual(depois));
    }

    @Test
    @DisplayName("Deve usar createdAt atual quando createdAt é inválido")
    void deveUsarCreatedAtAtualQuandoCreatedAtEInvalido() throws IOException {
        String json = """
            {
                "description": "Teste",
                "score": 8,
                "createdAt": "data-invalida"
            }
            """;

        JsonParser parser = objectMapper.createParser(json);
        DeserializationContext context = objectMapper.getDeserializationContext();
        
        Feedback feedback = deserializer.deserialize(parser, context);

        assertNotNull(feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve criar deserializer com construtor padrão")
    void deveCriarDeserializerComConstrutorPadrao() {
        FeedbackDeserializer deserializerPadrao = new FeedbackDeserializer();
        assertNotNull(deserializerPadrao);
    }

    @Test
    @DisplayName("Deve criar deserializer com classe")
    void deveCriarDeserializerComClasse() {
        FeedbackDeserializer deserializerComClasse = new FeedbackDeserializer(Feedback.class);
        assertNotNull(deserializerComClasse);
    }
}
