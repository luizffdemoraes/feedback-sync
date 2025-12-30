package br.com.fiap.postech.feedback.infrastructure.controllers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para FeedbackController")
class FeedbackControllerTest {

    @Mock
    private CreateFeedbackUseCase createFeedbackUseCase;

    @InjectMocks
    private FeedbackController feedbackController;

    private FeedbackRequest requestValido;
    private FeedbackResponse responseValido;

    @BeforeEach
    void setUp() {
        requestValido = new FeedbackRequest(
            "Aula muito boa",
            7,
            "MEDIUM"
        );

        responseValido = new FeedbackResponse(
            "feedback-id-123",
            7,
            "Aula muito boa",
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Deve criar feedback com sucesso e retornar 201 CREATED")
    void deveCriarFeedbackComSucesso() {
        when(createFeedbackUseCase.execute(any(FeedbackRequest.class)))
            .thenReturn(responseValido);

        Response response = feedbackController.submitFeedback(requestValido);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("feedback-id-123", entity.get("id"));
        assertEquals("recebido", entity.get("status"));

        verify(createFeedbackUseCase, times(1)).execute(requestValido);
    }

    @Test
    @DisplayName("Deve retornar 400 BAD_REQUEST quando request é nulo")
    void deveRetornarBadRequestQuandoRequestENulo() {
        Response response = feedbackController.submitFeedback(null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("Corpo da requisição é obrigatório", entity.get("error"));

        verify(createFeedbackUseCase, never()).execute(any(FeedbackRequest.class));
    }

    @Test
    @DisplayName("Deve propagar FeedbackDomainException do use case")
    void devePropagarFeedbackDomainExceptionDoUseCase() {
        FeedbackDomainException exception = new FeedbackDomainException("Descrição é obrigatória");
        when(createFeedbackUseCase.execute(any(FeedbackRequest.class)))
            .thenThrow(exception);

        FeedbackDomainException thrown = assertThrows(
            FeedbackDomainException.class,
            () -> feedbackController.submitFeedback(requestValido)
        );

        assertEquals("Descrição é obrigatória", thrown.getMessage());
        verify(createFeedbackUseCase, times(1)).execute(requestValido);
    }
}
