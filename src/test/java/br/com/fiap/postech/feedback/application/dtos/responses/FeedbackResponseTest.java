package br.com.fiap.postech.feedback.application.dtos.responses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FeedbackResponse")
class FeedbackResponseTest {

    @Test
    @DisplayName("Deve criar FeedbackResponse com todos os campos válidos")
    void deveCriarFeedbackResponseComTodosOsCamposValidos() {
        String id = "feedback-123";
        Integer score = 8;
        String description = "Ótimo atendimento";
        LocalDateTime createdAt = LocalDateTime.now();

        FeedbackResponse response = new FeedbackResponse(id, score, description, createdAt);

        assertEquals(id, response.id());
        assertEquals(score, response.score());
        assertEquals(description, response.description());
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException quando ID é nulo")
    void deveLancarIllegalArgumentExceptionQuandoIdENulo() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FeedbackResponse(null, 8, "Descrição", LocalDateTime.now())
        );
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException quando ID está vazio")
    void deveLancarIllegalArgumentExceptionQuandoIdEstaVazio() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FeedbackResponse("", 8, "Descrição", LocalDateTime.now())
        );
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException quando ID contém apenas espaços")
    void deveLancarIllegalArgumentExceptionQuandoIdContemApenasEspacos() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FeedbackResponse("   ", 8, "Descrição", LocalDateTime.now())
        );
    }

    @Test
    @DisplayName("Deve permitir score null")
    void devePermitirScoreNull() {
        String id = "feedback-123";
        FeedbackResponse response = new FeedbackResponse(id, null, "Descrição", LocalDateTime.now());

        assertNull(response.score());
        assertEquals(id, response.id());
    }

    @Test
    @DisplayName("Deve permitir description null")
    void devePermitirDescriptionNull() {
        String id = "feedback-123";
        FeedbackResponse response = new FeedbackResponse(id, 8, null, LocalDateTime.now());

        assertNull(response.description());
        assertEquals(id, response.id());
    }

    @Test
    @DisplayName("Deve permitir createdAt null")
    void devePermitirCreatedAtNull() {
        String id = "feedback-123";
        FeedbackResponse response = new FeedbackResponse(id, 8, "Descrição", null);

        assertNull(response.createdAt());
        assertEquals(id, response.id());
    }
}
