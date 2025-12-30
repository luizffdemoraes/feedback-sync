package br.com.fiap.postech.feedback.infrastructure.config;

import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para GlobalExceptionMapper")
class GlobalExceptionMapperTest {

    private GlobalExceptionMapper exceptionMapper;

    @BeforeEach
    void setUp() {
        exceptionMapper = new GlobalExceptionMapper();
    }

    @Test
    @DisplayName("Deve mapear FeedbackDomainException para 400 BAD_REQUEST")
    void deveMapearFeedbackDomainExceptionParaBadRequest() {
        FeedbackDomainException exception = new FeedbackDomainException("Descrição é obrigatória");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertEquals("Descrição é obrigatória", entity.get("error"));
    }

    @Test
    @DisplayName("Deve mapear FeedbackPersistenceException para 500 INTERNAL_SERVER_ERROR")
    void deveMapearFeedbackPersistenceExceptionParaInternalServerError() {
        FeedbackPersistenceException exception = new FeedbackPersistenceException("Erro ao conectar ao banco");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertTrue(entity.get("error").contains("Erro ao salvar feedback"));
    }

    @Test
    @DisplayName("Deve mapear NotificationException para 500 INTERNAL_SERVER_ERROR")
    void deveMapearNotificationExceptionParaInternalServerError() {
        NotificationException exception = new NotificationException("Erro ao enviar notificação");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertTrue(entity.get("error").contains("Erro ao enviar notificação"));
    }

    @Test
    @DisplayName("Deve mapear JsonProcessingException para 400 BAD_REQUEST")
    void deveMapearJsonProcessingExceptionParaBadRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonProcessingException exception = null;
        try {
            mapper.readValue("invalid json", Object.class);
        } catch (JsonProcessingException e) {
            exception = e;
        }

        assertNotNull(exception);
        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertTrue(entity.get("error").contains("Corpo da requisição inválido"));
    }

    @Test
    @DisplayName("Deve mapear IllegalArgumentException para 400 BAD_REQUEST")
    void deveMapearIllegalArgumentExceptionParaBadRequest() {
        IllegalArgumentException exception = new IllegalArgumentException("Score deve estar entre 0 e 10");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertTrue(entity.get("error").contains("Dados inválidos"));
        assertTrue(entity.get("error").contains("Score deve estar entre 0 e 10"));
    }

    @Test
    @DisplayName("Deve mapear exceção genérica para 500 INTERNAL_SERVER_ERROR")
    void deveMapearExcecaoGenericaParaInternalServerError() {
        RuntimeException exception = new RuntimeException("Erro inesperado");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> entity = (java.util.Map<String, String>) response.getEntity();
        assertEquals("Erro interno ao processar requisição", entity.get("error"));
    }

    @Test
    @DisplayName("Deve retornar Content-Type APPLICATION_JSON")
    void deveRetornarContentTypeApplicationJson() {
        FeedbackDomainException exception = new FeedbackDomainException("Erro de teste");

        Response response = exceptionMapper.toResponse(exception);

        assertEquals("application/json", response.getMediaType().toString());
    }
}
