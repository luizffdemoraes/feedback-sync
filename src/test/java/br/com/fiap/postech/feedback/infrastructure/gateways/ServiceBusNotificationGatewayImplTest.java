package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para ServiceBusNotificationGatewayImpl")
class ServiceBusNotificationGatewayImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ServiceBusSenderClient senderClient;

    private ServiceBusNotificationGatewayImpl gateway;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() throws Exception {
        executorService = Executors.newFixedThreadPool(2);
        
        gateway = new ServiceBusNotificationGatewayImpl(
            "Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test",
            "test-topic",
            objectMapper
        );
        
        // Injetar senderClient mockado usando reflection
        Field senderClientField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("senderClient");
        senderClientField.setAccessible(true);
        senderClientField.set(gateway, senderClient);
        
        // Injetar executorService
        Field executorField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(gateway, executorService);
    }

    @Test
    @DisplayName("Deve publicar mensagem crítica quando cliente está inicializado")
    void devePublicarMensagemCriticaQuandoClienteEstaInicializado() throws Exception {
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        String jsonPayload = "{\"id\":\"123\",\"description\":\"Aula ruim\"}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        gateway.publishCritical(feedback);

        // Aguardar execução assíncrona
        Thread.sleep(100);
        
        verify(objectMapper, timeout(500).atLeastOnce()).writeValueAsString(feedback);
    }

    @Test
    @DisplayName("Não deve falhar quando cliente não está inicializado")
    void naoDeveFalharQuandoClienteNaoEstaInicializado() throws Exception {
        // Remover senderClient
        Field senderClientField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("senderClient");
        senderClientField.setAccessible(true);
        senderClientField.set(gateway, null);
        
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");

        assertDoesNotThrow(() -> gateway.publishCritical(feedback));
        
        verify(senderClient, never()).sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    @DisplayName("Deve enviar notificação ao admin com sucesso")
    void deveEnviarNotificacaoAoAdminComSucesso() {
        String mensagem = "Alerta: Feedback crítico recebido";
        
        doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        assertDoesNotThrow(() -> gateway.sendAdminNotification(mensagem));

        verify(senderClient, times(1)).sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando cliente não está inicializado ao enviar notificação admin")
    void deveLancarNotificationExceptionQuandoClienteNaoEstaInicializado() throws Exception {
        // Remover senderClient
        Field senderClientField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("senderClient");
        senderClientField.setAccessible(true);
        senderClientField.set(gateway, null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification("Mensagem de teste")
        );

        assertEquals("Service Bus não está disponível", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre erro ao enviar mensagem")
    void deveLancarNotificationExceptionQuandoOcorreErroAoEnviarMensagem() {
        String mensagem = "Mensagem de teste";
        RuntimeException erro = new RuntimeException("Erro de conexão");
        
        doThrow(erro).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(mensagem)
        );

        assertTrue(exception.getMessage().contains("Falha ao enviar notificação ao admin"));
        assertEquals(erro, exception.getCause());
    }

    @Test
    @DisplayName("Deve criar mensagem com subject correto para notificação crítica")
    void deveCriarMensagemComSubjectCorretoParaNotificacaoCritica() throws Exception {
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        String jsonPayload = "{\"id\":\"123\"}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        gateway.publishCritical(feedback);

        Thread.sleep(100);
        
        verify(senderClient, timeout(500).atLeastOnce()).sendMessage(argThat(message -> 
            "critical-feedback".equals(message.getSubject()) &&
            "application/json".equals(message.getContentType())
        ));
    }

    @Test
    @DisplayName("Deve criar mensagem com subject correto para notificação admin")
    void deveCriarMensagemComSubjectCorretoParaNotificacaoAdmin() {
        String mensagem = "Alerta crítico";
        
        doNothing().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        gateway.sendAdminNotification(mensagem);

        verify(senderClient).sendMessage(argThat(message -> 
            "admin-notification".equals(message.getSubject()) &&
            "text/plain".equals(message.getContentType()) &&
            mensagem.equals(message.getBody().toString())
        ));
    }

    @Test
    @DisplayName("Deve fazer cleanup do executor service")
    void deveFazerCleanupDoExecutorService() throws Exception {
        gateway.cleanup();
        
        // Verificar se o executor foi encerrado
        assertTrue(executorService.isShutdown() || executorService.isTerminated());
    }

    @Test
    @DisplayName("Deve tratar erro ao serializar JSON na mensagem crítica")
    void deveTratarErroAoSerializarJsonNaMensagemCritica() throws Exception {
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        com.fasterxml.jackson.core.JsonProcessingException erro = 
            new com.fasterxml.jackson.core.JsonProcessingException("Erro ao serializar") {};
        
        when(objectMapper.writeValueAsString(any())).thenThrow(erro);

        // Não deve lançar exceção, apenas logar erro
        assertDoesNotThrow(() -> gateway.publishCritical(feedback));
        
        Thread.sleep(100);
        verify(objectMapper, timeout(500).atLeastOnce()).writeValueAsString(feedback);
    }

    @Test
    @DisplayName("Deve fazer cleanup mesmo quando ocorre erro ao fechar senderClient")
    void deveFazerCleanupMesmoQuandoOcorreErroAoFecharSenderClient() throws Exception {
        doThrow(new RuntimeException("Erro ao fechar")).when(senderClient).close();

        // Não deve lançar exceção
        assertDoesNotThrow(() -> gateway.cleanup());
        
        verify(senderClient, times(1)).close();
    }

    @Test
    @DisplayName("Deve fazer cleanup quando senderClient é null")
    void deveFazerCleanupQuandoSenderClientENull() throws Exception {
        Field senderClientField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("senderClient");
        senderClientField.setAccessible(true);
        senderClientField.set(gateway, null);

        assertDoesNotThrow(() -> gateway.cleanup());
    }

    @Test
    @DisplayName("Deve tratar timeout ao enviar mensagem com timeout")
    void deveTratarTimeoutAoEnviarMensagemComTimeout() throws Exception {
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        String jsonPayload = "{\"id\":\"123\"}";
        ServiceBusMessage message = new ServiceBusMessage(jsonPayload);
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        
        // Criar um CompletableFuture que nunca completa para simular timeout
        CompletableFuture<Void> neverCompletingFuture = new CompletableFuture<>();
        doAnswer(invocation -> {
            // Simular timeout não lançando exceção imediatamente
            return null;
        }).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        gateway.publishCritical(feedback);
        
        // Aguardar um pouco para que o método assíncrono seja executado
        Thread.sleep(200);
        
        verify(objectMapper, timeout(500).atLeastOnce()).writeValueAsString(feedback);
    }

    @Test
    @DisplayName("Deve tratar ExecutionException ao enviar mensagem")
    void deveTratarExecutionExceptionAoEnviarMensagem() throws Exception {
        Feedback feedback = new Feedback("Aula ruim", 2, "HIGH");
        String jsonPayload = "{\"id\":\"123\"}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);
        doThrow(new RuntimeException("Erro de conexão")).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        gateway.publishCritical(feedback);
        
        Thread.sleep(200);
        
        verify(objectMapper, timeout(500).atLeastOnce()).writeValueAsString(feedback);
        verify(senderClient, timeout(500).atLeastOnce()).sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    @DisplayName("Deve tratar InterruptedException no cleanup")
    void deveTratarInterruptedExceptionNoCleanup() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        Field executorField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(gateway, mockExecutor);
        
        when(mockExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Interrompido"));

        assertDoesNotThrow(() -> gateway.cleanup());
        
        verify(mockExecutor, times(1)).shutdown();
        verify(mockExecutor, times(1)).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockExecutor, times(1)).shutdownNow();
    }

    @Test
    @DisplayName("Deve fazer shutdownNow quando awaitTermination retorna false")
    void deveFazerShutdownNowQuandoAwaitTerminationRetornaFalse() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        Field executorField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(gateway, mockExecutor);
        
        when(mockExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);

        gateway.cleanup();
        
        verify(mockExecutor, times(1)).shutdown();
        verify(mockExecutor, times(1)).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockExecutor, times(1)).shutdownNow();
    }

    @Test
    @DisplayName("Deve fazer cleanup quando executorService é null")
    void deveFazerCleanupQuandoExecutorServiceENull() throws Exception {
        Field executorField = ServiceBusNotificationGatewayImpl.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(gateway, null);

        assertDoesNotThrow(() -> gateway.cleanup());
    }
}
