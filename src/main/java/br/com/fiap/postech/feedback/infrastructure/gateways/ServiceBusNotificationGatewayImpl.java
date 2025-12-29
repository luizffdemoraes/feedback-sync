package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementação do gateway de notificações usando Azure Service Bus.
 * 
 * Responsabilidade: Publicar mensagens no Service Bus para processamento assíncrono.
 */
@ApplicationScoped
public class ServiceBusNotificationGatewayImpl implements NotificationGateway {

    private static final Logger logger = LoggerFactory.getLogger(ServiceBusNotificationGatewayImpl.class);

    private final String connectionString;
    private final String topicName;
    private final ObjectMapper objectMapper;
    private ServiceBusSenderClient senderClient;
    private final ExecutorService executorService;

    @Inject
    public ServiceBusNotificationGatewayImpl(
            @ConfigProperty(name = "azure.servicebus.connection-string") String connectionString,
            @ConfigProperty(name = "azure.servicebus.topic-name") String topicName,
            ObjectMapper objectMapper) {
        this.connectionString = connectionString;
        this.topicName = topicName;
        this.objectMapper = objectMapper;
        // Thread pool para execução assíncrona de notificações
        this.executorService = Executors.newFixedThreadPool(2);
    }

    @PostConstruct
    void init() {
        try {
            senderClient = new ServiceBusClientBuilder()
                    .connectionString(connectionString)
                    .sender()
                    .topicName(topicName)
                    .buildClient();
        } catch (Exception e) {
            logger.error("Falha ao conectar ao Service Bus. Tópico: {}, Erro: {}", topicName, e.getMessage(), e);
        }
    }

    @Override
    public void publishCritical(Object payload) {
        // Verifica se o cliente está inicializado
        if (senderClient == null) {
            logger.warn("Service Bus não está disponível. Notificação crítica não será enviada.");
            return;
        }

        // Executa o envio de forma assíncrona para não bloquear a thread principal
        CompletableFuture.runAsync(() -> sendCriticalMessageAsync(payload), executorService);
    }

    /**
     * Envia mensagem crítica ao Service Bus de forma assíncrona com timeout.
     * Não lança exceções - apenas loga erros.
     */
    private void sendCriticalMessageAsync(Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            ServiceBusMessage message = new ServiceBusMessage(jsonPayload)
                    .setContentType("application/json")
                    .setSubject("critical-feedback");

            sendMessageWithTimeout(message, 5, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("Erro ao preparar mensagem crítica para o Service Bus. Tópico: {}, Erro: {}", 
                topicName, e.getMessage(), e);
        }
    }

    /**
     * Envia mensagem ao Service Bus com timeout configurável.
     */
    private void sendMessageWithTimeout(ServiceBusMessage message, long timeout, TimeUnit unit) {
        CompletableFuture<Void> sendFuture = CompletableFuture.runAsync(() -> {
            try {
                senderClient.sendMessage(message);
                logger.debug("Mensagem crítica enviada ao Service Bus com sucesso");
            } catch (Exception e) {
                logger.error("Erro ao enviar mensagem ao Service Bus. Tópico: {}, Erro: {}", 
                    topicName, e.getMessage(), e);
                throw new RuntimeException("Falha ao enviar mensagem ao Service Bus", e);
            }
        }, executorService);

        try {
            sendFuture.get(timeout, unit);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Timeout ao enviar notificação crítica ao Service Bus ({}s). A notificação será perdida, mas o feedback já foi salvo.", 
                unit.toSeconds(timeout));
            sendFuture.cancel(true);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("Erro ao enviar mensagem ao Service Bus. Tópico: {}, Erro: {}", 
                topicName, cause != null ? cause.getMessage() : e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.warn("Thread interrompida ao aguardar envio de notificação crítica");
            Thread.currentThread().interrupt();
            sendFuture.cancel(true);
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        // Verifica se o cliente está inicializado
        if (senderClient == null) {
            logger.warn("Service Bus não está disponível. Notificação ao admin não será enviada.");
            throw new NotificationException("Service Bus não está disponível");
        }

        try {
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message)
                    .setContentType("text/plain")
                    .setSubject("admin-notification");

            senderClient.sendMessage(serviceBusMessage);

        } catch (Exception e) {
            logger.error("Erro ao enviar notificação ao admin via Service Bus. Erro: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao enviar notificação ao admin: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void cleanup() {
        // Encerra o executor service
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (senderClient != null) {
            try {
                senderClient.close();
            } catch (Exception e) {
                logger.warn("Erro ao fechar conexão do Service Bus: {}", e.getMessage());
            }
        }
    }
}
