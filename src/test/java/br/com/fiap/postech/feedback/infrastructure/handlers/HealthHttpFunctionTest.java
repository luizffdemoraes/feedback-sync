package br.com.fiap.postech.feedback.infrastructure.handlers;

import com.microsoft.azure.functions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para HealthHttpFunction")
class HealthHttpFunctionTest {

    @Mock
    private HttpRequestMessage<Void> request;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Logger logger;

    @Mock
    private HttpResponseMessage.Builder responseBuilder;

    @Mock
    private HttpResponseMessage.Builder errorResponseBuilder;

    private HealthHttpFunction function;

    @BeforeEach
    void setUp() {
        function = new HealthHttpFunction();
        when(executionContext.getLogger()).thenReturn(logger);
        lenient().doNothing().when(logger).info(anyString());
        lenient().doNothing().when(logger).severe(anyString());
    }

    @Test
    @DisplayName("Deve retornar status UP quando health check é bem-sucedido")
    void deveRetornarStatusUPQuandoHealthCheckEBemSucedido() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(request, times(1)).createResponseBuilder(HttpStatus.OK);
        verify(responseBuilder, times(1)).body(any(Map.class));
        verify(responseBuilder, times(1)).header("Content-Type", "application/json");
    }

    @Test
    @DisplayName("Deve retornar resposta com campos corretos no body")
    void deveRetornarRespostaComCamposCorretosNoBody() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> body = invocation.getArgument(0);
            assertEquals("UP", body.get("status"));
            assertEquals("feedback-sync", body.get("service"));
            assertEquals("1.0.0", body.get("version"));
            return responseBuilder;
        });
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        function.checkHealth(request, executionContext);

        verify(responseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve retornar status DOWN quando ocorre exceção")
    void deveRetornarStatusDOWNQuandoOcorreExcecao() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenThrow(new RuntimeException("Erro simulado"));
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(request, times(1)).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve logar informações quando health check é executado")
    void deveLogarInformacoesQuandoHealthCheckEExecutado() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        function.checkHealth(request, executionContext);

        verify(logger, atLeastOnce()).info(contains("Recebendo requisição GET /api/health"));
    }

    @Test
    @DisplayName("Deve retornar resposta com status DOWN quando ocorre exceção no body")
    void deveRetornarRespostaComStatusDOWNQuandoOcorreExcecaoNoBody() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenThrow(new RuntimeException("Erro ao criar body"));
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.body(any(Map.class))).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.header(anyString(), anyString())).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(request, times(1)).createResponseBuilder(HttpStatus.OK);
        verify(request, times(1)).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder, times(1)).body(any(Map.class));
        verify(errorResponseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve retornar resposta com status DOWN quando ocorre exceção no header")
    void deveRetornarRespostaComStatusDOWNQuandoOcorreExcecaoNoHeader() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenThrow(new RuntimeException("Erro ao adicionar header"));
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.body(any(Map.class))).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.header(anyString(), anyString())).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(request, times(1)).createResponseBuilder(HttpStatus.OK);
        verify(request, times(1)).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder, times(1)).body(any(Map.class));
        verify(responseBuilder, times(1)).header(anyString(), anyString());
        verify(errorResponseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve retornar resposta com status DOWN quando ocorre exceção no build")
    void deveRetornarRespostaComStatusDOWNQuandoOcorreExcecaoNoBuild() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenThrow(new RuntimeException("Erro ao construir resposta"));
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.body(any(Map.class))).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.header(anyString(), anyString())).thenReturn(errorResponseBuilder);
        when(errorResponseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(request, times(1)).createResponseBuilder(HttpStatus.OK);
        verify(request, times(1)).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(responseBuilder, times(1)).body(any(Map.class));
        verify(responseBuilder, times(1)).header(anyString(), anyString());
        verify(responseBuilder, times(1)).build();
        verify(errorResponseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve retornar resposta com error message quando ocorre exceção")
    void deveRetornarRespostaComErrorMessageQuandoOcorreExcecao() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        RuntimeException exception = new RuntimeException("Erro simulado");
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenThrow(exception);
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenAnswer(invocation -> {
            Map<String, Object> body = invocation.getArgument(0);
            assertEquals("DOWN", body.get("status"));
            assertNotNull(body.get("error"));
            assertTrue(body.get("error").toString().contains("Erro simulado"));
            return responseBuilder;
        });
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        HttpResponseMessage result = function.checkHealth(request, executionContext);

        assertNotNull(result);
        verify(responseBuilder, times(1)).body(any(Map.class));
    }

    @Test
    @DisplayName("Deve retornar resposta com Content-Type application/json")
    void deveRetornarRespostaComContentTypeApplicationJson() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        function.checkHealth(request, executionContext);

        verify(responseBuilder, times(1)).header("Content-Type", "application/json");
    }

    @Test
    @DisplayName("Deve retornar resposta com Content-Type application/json mesmo quando ocorre erro")
    void deveRetornarRespostaComContentTypeApplicationJsonMesmoQuandoOcorreErro() {
        HttpResponseMessage response = mock(HttpResponseMessage.class);
        
        when(request.createResponseBuilder(HttpStatus.OK)).thenThrow(new RuntimeException("Erro"));
        when(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
        when(responseBuilder.body(any(Map.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);

        function.checkHealth(request, executionContext);

        verify(responseBuilder, times(1)).header("Content-Type", "application/json");
    }
}
