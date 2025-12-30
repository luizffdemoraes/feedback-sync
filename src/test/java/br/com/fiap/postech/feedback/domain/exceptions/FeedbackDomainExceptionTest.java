package br.com.fiap.postech.feedback.domain.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FeedbackDomainException")
class FeedbackDomainExceptionTest {

    @Test
    @DisplayName("Deve criar exceção com mensagem")
    void deveCriarExcecaoComMensagem() {
        String mensagem = "Erro de domínio";
        FeedbackDomainException exception = new FeedbackDomainException(mensagem);
        
        assertEquals(mensagem, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Deve criar exceção com mensagem e causa")
    void deveCriarExcecaoComMensagemECausa() {
        String mensagem = "Erro de domínio";
        Throwable causa = new IllegalArgumentException("Causa original");
        FeedbackDomainException exception = new FeedbackDomainException(mensagem, causa);
        
        assertEquals(mensagem, exception.getMessage());
        assertEquals(causa, exception.getCause());
    }

    @Test
    @DisplayName("Deve ser instância de RuntimeException")
    void deveSerInstanciaDeRuntimeException() {
        FeedbackDomainException exception = new FeedbackDomainException("Teste");
        
        assertTrue(exception instanceof RuntimeException);
    }
}
