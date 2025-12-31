package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para QueueNotificationGatewayImpl")
class QueueNotificationGatewayImplTest {

    @Mock
    private QueueClientBuilder queueClientBuilder;

    @Mock
    private QueueClient queueClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private QueueNotificationGatewayImpl gateway;

    private Feedback feedback;
    private String connectionString;

    @BeforeEach
    void setUp() throws Exception {
        connectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;";
        
        // Usar reflection para injetar connectionString e objectMapper
        java.lang.reflect.Field connectionField = QueueNotificationGatewayImpl.class.getDeclaredField("connectionString");
        connectionField.setAccessible(true);
        connectionField.set(gateway, connectionString);

        java.lang.reflect.Field mapperField = QueueNotificationGatewayImpl.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(gateway, objectMapper);

        feedback = new Feedback("Aula ruim", new Score(2), Urgency.HIGH);
    }

    @Test
    @DisplayName("Deve publicar mensagem crítica na fila com sucesso")
    void devePublicarMensagemCriticaNaFilaComSucesso() throws Exception {
        String jsonPayload = "{\"id\":\"123\",\"description\":\"Aula ruim\"}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        
        // Mock do QueueClient via reflection após init()
        java.lang.reflect.Field queueClientField = QueueNotificationGatewayImpl.class.getDeclaredField("queueClient");
        queueClientField.setAccessible(true);
        queueClientField.set(gateway, queueClient);

        gateway.publishCritical(feedback);

        verify(objectMapper, times(1)).writeValueAsString(feedback);
        verify(queueClient, times(1)).sendMessage(jsonPayload);
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando erro ao serializar JSON")
    void deveLancarNotificationExceptionQuandoErroAoSerializarJson() throws Exception {
        JsonProcessingException jsonException = new JsonProcessingException("Erro ao serializar") {};
        
        when(objectMapper.writeValueAsString(any())).thenThrow(jsonException);

        java.lang.reflect.Field queueClientField = QueueNotificationGatewayImpl.class.getDeclaredField("queueClient");
        queueClientField.setAccessible(true);
        queueClientField.set(gateway, queueClient);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.publishCritical(feedback)
        );

        assertTrue(exception.getMessage().contains("Erro ao serializar payload para JSON"));
        assertTrue(exception.getMessage().contains("Fila: critical-feedbacks"));
        assertEquals(jsonException, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando erro do Azure")
    void deveLancarNotificationExceptionQuandoErroDoAzure() throws Exception {
        String jsonPayload = "{\"id\":\"123\"}";
        RuntimeException azureException = new RuntimeException("Erro do Azure");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        
        java.lang.reflect.Field queueClientField = QueueNotificationGatewayImpl.class.getDeclaredField("queueClient");
        queueClientField.setAccessible(true);
        queueClientField.set(gateway, queueClient);
        
        when(queueClient.sendMessage(anyString())).thenThrow(azureException);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.publishCritical(feedback)
        );

        assertTrue(exception.getMessage().contains("Erro inesperado ao publicar mensagem crítica"));
        assertTrue(exception.getMessage().contains("Fila: critical-feedbacks"));
        assertEquals(azureException, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando erro inesperado")
    void deveLancarNotificationExceptionQuandoErroInesperado() throws Exception {
        String jsonPayload = "{\"id\":\"123\"}";
        RuntimeException runtimeException = new RuntimeException("Erro inesperado");
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        when(queueClient.sendMessage(anyString())).thenThrow(runtimeException);

        java.lang.reflect.Field queueClientField = QueueNotificationGatewayImpl.class.getDeclaredField("queueClient");
        queueClientField.setAccessible(true);
        queueClientField.set(gateway, queueClient);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.publishCritical(feedback)
        );

        assertTrue(exception.getMessage().contains("Erro inesperado ao publicar mensagem crítica"));
        assertTrue(exception.getMessage().contains("Fila: critical-feedbacks"));
        assertEquals(runtimeException, exception.getCause());
    }

    @Test
    @DisplayName("Deve tratar payload nulo corretamente")
    void deveTratarPayloadNuloCorretamente() throws Exception {
        JsonProcessingException jsonException = new JsonProcessingException("Erro ao serializar") {};
        
        when(objectMapper.writeValueAsString(null)).thenThrow(jsonException);

        java.lang.reflect.Field queueClientField = QueueNotificationGatewayImpl.class.getDeclaredField("queueClient");
        queueClientField.setAccessible(true);
        queueClientField.set(gateway, queueClient);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.publishCritical(null)
        );

        assertTrue(exception.getMessage().contains("Tipo do payload: null"));
    }
}
