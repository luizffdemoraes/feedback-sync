package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.usecases.NotifyAdminUseCase;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para NotifyAdminFunction")
class NotifyAdminFunctionTest {

    @Mock
    private NotifyAdminUseCase notifyAdminUseCase;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private ObjectMapper objectMapper;

    private NotifyAdminFunction function;

    @BeforeEach
    void setUp() {
        function = new NotifyAdminFunction(notifyAdminUseCase, objectMapper);
    }

    @Test
    @DisplayName("Deve processar mensagem crítica com sucesso")
    void deveProcessarMensagemCriticaComSucesso() throws Exception {
        String jsonMessage = "{\"id\":\"123\",\"description\":\"Aula ruim\",\"score\":2,\"urgency\":\"HIGH\",\"createdAt\":\"2024-01-15T10:30:00\"}";
        
        ObjectMapper feedbackMapper = mock(ObjectMapper.class);
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        feedback.setId("123");
        feedback.setCreatedAt(LocalDateTime.parse("2024-01-15T10:30:00"));
        
        when(objectMapper.copy()).thenReturn(feedbackMapper);
        when(feedbackMapper.readValue(jsonMessage, Feedback.class)).thenReturn(feedback);
        doNothing().when(notifyAdminUseCase).execute(any(Feedback.class));

        assertDoesNotThrow(() -> function.run(jsonMessage, executionContext));

        verify(notifyAdminUseCase, times(1)).execute(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando use case falha")
    void deveLancarNotificationExceptionQuandoUseCaseFalha() throws Exception {
        String jsonMessage = "{\"id\":\"123\",\"description\":\"Aula ruim\",\"score\":2,\"urgency\":\"HIGH\"}";
        
        ObjectMapper feedbackMapper = mock(ObjectMapper.class);
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        NotificationException exception = new NotificationException("Erro ao enviar notificação");
        
        when(objectMapper.copy()).thenReturn(feedbackMapper);
        when(feedbackMapper.readValue(jsonMessage, Feedback.class)).thenReturn(feedback);
        doThrow(exception).when(notifyAdminUseCase).execute(any(Feedback.class));

        NotificationException thrown = assertThrows(
            NotificationException.class,
            () -> function.run(jsonMessage, executionContext)
        );

        assertEquals(exception, thrown);
        verify(notifyAdminUseCase, times(1)).execute(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre erro ao deserializar JSON")
    void deveLancarNotificationExceptionQuandoOcorreErroAoDeserializarJson() throws Exception {
        String jsonMessage = "invalid json";
        
        ObjectMapper feedbackMapper = mock(ObjectMapper.class);
        com.fasterxml.jackson.core.JsonProcessingException exception = 
            new com.fasterxml.jackson.core.JsonProcessingException("Erro ao deserializar") {};
        
        when(objectMapper.copy()).thenReturn(feedbackMapper);
        when(feedbackMapper.readValue(jsonMessage, Feedback.class)).thenThrow(exception);

        NotificationException thrown = assertThrows(
            NotificationException.class,
            () -> function.run(jsonMessage, executionContext)
        );

        assertTrue(thrown.getMessage().contains("Falha ao processar notificação crítica"));
        assertEquals(exception, thrown.getCause());
        verify(notifyAdminUseCase, never()).execute(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve criar ObjectMapper com FeedbackDeserializer")
    void deveCriarObjectMapperComFeedbackDeserializer() throws Exception {
        String jsonMessage = "{\"id\":\"123\",\"description\":\"Aula ruim\",\"score\":2,\"urgency\":\"HIGH\"}";
        
        ObjectMapper feedbackMapper = mock(ObjectMapper.class);
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        
        when(objectMapper.copy()).thenReturn(feedbackMapper);
        when(feedbackMapper.readValue(jsonMessage, Feedback.class)).thenReturn(feedback);
        doNothing().when(notifyAdminUseCase).execute(any(Feedback.class));

        function.run(jsonMessage, executionContext);

        verify(objectMapper, times(1)).copy();
        verify(feedbackMapper, times(1)).readValue(jsonMessage, Feedback.class);
    }
}
