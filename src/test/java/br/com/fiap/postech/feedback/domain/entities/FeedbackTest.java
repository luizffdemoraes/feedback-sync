package br.com.fiap.postech.feedback.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para Entidade Feedback")
class FeedbackTest {

    @Test
    @DisplayName("Deve criar Feedback com construtor completo")
    void deveCriarFeedbackComConstrutorCompleto() {
        Score score = new Score(7);
        Urgency urgency = Urgency.of("HIGH");
        String description = "Aula muito boa";
        
        Feedback feedback = new Feedback(description, score, urgency);
        
        assertNotNull(feedback.getId());
        assertEquals(description, feedback.getDescription());
        assertEquals(score, feedback.getScore());
        assertEquals(urgency, feedback.getUrgency());
        assertNotNull(feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve criar Feedback com construtor simplificado")
    void deveCriarFeedbackComConstrutorSimplificado() {
        String description = "Aula excelente";
        int scoreValue = 9;
        String urgencyValue = "MEDIUM";
        
        Feedback feedback = new Feedback(description, scoreValue, urgencyValue);
        
        assertNotNull(feedback.getId());
        assertEquals(description, feedback.getDescription());
        assertEquals(scoreValue, feedback.getScore().getValue());
        assertEquals(urgencyValue.toUpperCase(), feedback.getUrgency().getValue());
        assertNotNull(feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve reconstruir Feedback com método estático")
    void deveReconstruirFeedbackComMetodoEstatico() {
        String id = "test-id-123";
        String description = "Feedback de teste";
        int scoreValue = 5;
        String urgencyValue = "LOW";
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        
        Feedback feedback = Feedback.reconstruct(id, description, scoreValue, urgencyValue, createdAt);
        
        assertEquals(id, feedback.getId());
        assertEquals(description, feedback.getDescription());
        assertEquals(scoreValue, feedback.getScore().getValue());
        assertEquals(urgencyValue.toUpperCase(), feedback.getUrgency().getValue());
        assertEquals(createdAt, feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve retornar true para Feedback crítico (score <= 3)")
    void deveRetornarTrueParaFeedbackCritico() {
        Feedback feedback1 = new Feedback("Crítico", 0, "HIGH");
        Feedback feedback2 = new Feedback("Crítico", 3, "HIGH");
        
        assertTrue(feedback1.isCritical());
        assertTrue(feedback2.isCritical());
    }

    @Test
    @DisplayName("Deve retornar false para Feedback não crítico (score > 3)")
    void deveRetornarFalseParaFeedbackNaoCritico() {
        Feedback feedback1 = new Feedback("Bom", 4, "LOW");
        Feedback feedback2 = new Feedback("Excelente", 10, "LOW");
        
        assertFalse(feedback1.isCritical());
        assertFalse(feedback2.isCritical());
    }

    @Test
    @DisplayName("Deve permitir alterar ID usando setter")
    void devePermitirAlterarIdUsandoSetter() {
        Feedback feedback = new Feedback("Teste", 5, "MEDIUM");
        String novoId = "novo-id-456";
        
        feedback.setId(novoId);
        
        assertEquals(novoId, feedback.getId());
    }

    @Test
    @DisplayName("Deve permitir alterar createdAt usando setter")
    void devePermitirAlterarCreatedAtUsandoSetter() {
        Feedback feedback = new Feedback("Teste", 5, "MEDIUM");
        LocalDateTime novaData = LocalDateTime.now().minusDays(5);
        
        feedback.setCreatedAt(novaData);
        
        assertEquals(novaData, feedback.getCreatedAt());
    }

    @Test
    @DisplayName("Deve gerar ID único para cada Feedback")
    void deveGerarIdUnicoParaCadaFeedback() {
        Feedback feedback1 = new Feedback("Teste 1", 5, "LOW");
        Feedback feedback2 = new Feedback("Teste 2", 6, "MEDIUM");
        
        assertNotEquals(feedback1.getId(), feedback2.getId());
    }

    @Test
    @DisplayName("Deve inicializar createdAt com data/hora atual")
    void deveInicializarCreatedAtComDataHoraAtual() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        Feedback feedback = new Feedback("Teste", 5, "LOW");
        LocalDateTime depois = LocalDateTime.now().plusSeconds(1);
        
        assertTrue(feedback.getCreatedAt().isAfter(antes) || feedback.getCreatedAt().isEqual(antes));
        assertTrue(feedback.getCreatedAt().isBefore(depois) || feedback.getCreatedAt().isEqual(depois));
    }
}
