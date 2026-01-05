package br.com.fiap.postech.feedback.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FunctionProcessingException")
class FunctionProcessingExceptionTest {

    @Test
    @DisplayName("Deve criar exceção com mensagem")
    void deveCriarExcecaoComMensagem() {
        String mensagem = "Erro ao processar função";
        FunctionProcessingException exception = new FunctionProcessingException(mensagem);

        assertEquals(mensagem, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Deve criar exceção com mensagem e causa")
    void deveCriarExcecaoComMensagemECausa() {
        String mensagem = "Erro ao processar função";
        Throwable causa = new RuntimeException("Causa original");
        FunctionProcessingException exception = new FunctionProcessingException(mensagem, causa);

        assertEquals(mensagem, exception.getMessage());
        assertEquals(causa, exception.getCause());
    }

    @Test
    @DisplayName("Deve ser instância de RuntimeException")
    void deveSerInstanciaDeRuntimeException() {
        FunctionProcessingException exception = new FunctionProcessingException("Teste");
        assertInstanceOf(RuntimeException.class, exception);
    }
}
