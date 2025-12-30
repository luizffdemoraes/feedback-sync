package br.com.fiap.postech.feedback.domain.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FeedbackPersistenceException")
class FeedbackPersistenceExceptionTest {

    @Test
    @DisplayName("Deve criar exceção com mensagem")
    void deveCriarExcecaoComMensagem() {
        String mensagem = "Erro ao conectar ao banco de dados";
        FeedbackPersistenceException exception = new FeedbackPersistenceException(mensagem);
        
        assertEquals(mensagem, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Deve criar exceção com mensagem e causa")
    void deveCriarExcecaoComMensagemECausa() {
        String mensagem = "Erro ao salvar feedback";
        Throwable causa = new RuntimeException("Connection timeout");
        FeedbackPersistenceException exception = new FeedbackPersistenceException(mensagem, causa);
        
        assertEquals(mensagem, exception.getMessage());
        assertEquals(causa, exception.getCause());
    }

    @Test
    @DisplayName("Deve ser instância de RuntimeException")
    void deveSerInstanciaDeRuntimeException() {
        FeedbackPersistenceException exception = new FeedbackPersistenceException("Teste");
        
        assertTrue(exception instanceof RuntimeException);
    }
}
