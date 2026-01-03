package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
import br.com.fiap.postech.feedback.infrastructure.config.FunctionProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para NotifyAdminFunction")
class NotifyAdminFunctionTest {

    @Mock
    private EmailNotificationGateway emailGateway;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ExecutionContext executionContext;
    
    @Mock
    private Logger logger;

    @Spy
    private NotifyAdminFunction notifyAdminFunction;

    private Feedback feedback;
    private String feedbackJson;

    @BeforeEach
    void setUp() {
        feedback = new Feedback("Aula muito ruim", new Score(2), Urgency.HIGH);
        feedback.setId("test-id-123");
        
        feedbackJson = "{\"id\":\"test-id-123\",\"description\":\"Aula muito ruim\",\"score\":{\"value\":2},\"urgency\":{\"value\":\"HIGH\"}}";
        
        // Mocka o logger do ExecutionContext
        when(executionContext.getLogger()).thenReturn(logger);
        doNothing().when(logger).info(anyString());
        
        // Mocka os métodos package-private usando spy
        doReturn(emailGateway).when(notifyAdminFunction).getEmailGateway();
        doReturn(objectMapper).when(notifyAdminFunction).getObjectMapper();
    }

    @Test
    @DisplayName("Deve processar feedback crítico e enviar email com sucesso")
    void deveProcessarFeedbackCriticoEEnviarEmailComSucesso() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Feedback.class))).thenReturn(feedback);
        doNothing().when(emailGateway).sendAdminNotification(anyString());

        assertDoesNotThrow(() -> notifyAdminFunction.run(feedbackJson, executionContext));

        verify(objectMapper, times(1)).readValue(feedbackJson, Feedback.class);
        verify(emailGateway, times(1)).sendAdminNotification(anyString());
    }

    @Test
    @DisplayName("Deve lançar FunctionProcessingException quando erro ao processar feedback")
    void deveLancarRuntimeExceptionQuandoErroAoProcessarFeedback() throws Exception {
        RuntimeException runtimeException = new RuntimeException("Erro ao deserializar");
        
        when(objectMapper.readValue(anyString(), eq(Feedback.class))).thenThrow(runtimeException);

        FunctionProcessingException exception = assertThrows(
            FunctionProcessingException.class,
            () -> notifyAdminFunction.run(feedbackJson, executionContext)
        );

        assertTrue(exception.getMessage().contains("Falha ao processar notificação crítica"));
        assertEquals(runtimeException, exception.getCause());
    }

    @Test
    @DisplayName("Deve construir conteúdo do email corretamente")
    void deveConstruirConteudoDoEmailCorretamente() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Feedback.class))).thenReturn(feedback);
        doNothing().when(emailGateway).sendAdminNotification(anyString());

        notifyAdminFunction.run(feedbackJson, executionContext);

        verify(emailGateway, times(1)).sendAdminNotification(argThat(content -> 
            content.contains("ALERTA: Feedback Crítico Recebido") &&
            content.contains("ID: test-id-123") &&
            content.contains("Descrição: Aula muito ruim") &&
            content.contains("Nota: 2/10") &&
            content.contains("Urgência: HIGH")
        ));
    }

    @Test
    @DisplayName("Deve lançar FunctionProcessingException quando erro ao enviar email para feedback")
    void deveLancarRuntimeExceptionQuandoErroAoEnviarEmailParaFeedback() throws Exception {
        NotificationException notificationException = new NotificationException("Erro ao enviar email");
        
        when(objectMapper.readValue(anyString(), eq(Feedback.class))).thenReturn(feedback);
        doThrow(notificationException).when(emailGateway).sendAdminNotification(anyString());

        FunctionProcessingException exception = assertThrows(
            FunctionProcessingException.class,
            () -> notifyAdminFunction.run(feedbackJson, executionContext)
        );

        assertTrue(exception.getMessage().contains("Falha ao processar notificação crítica"));
        assertEquals(notificationException, exception.getCause());
    }
}
