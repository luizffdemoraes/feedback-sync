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

    @Inject
    public ServiceBusNotificationGatewayImpl(
            @ConfigProperty(name = "azure.servicebus.connection-string") String connectionString,
            @ConfigProperty(name = "azure.servicebus.topic-name") String topicName,
            ObjectMapper objectMapper) {
        this.connectionString = connectionString;
        this.topicName = topicName;
        this.objectMapper = objectMapper;
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
            throw new NotificationException("Service Bus não está disponível");
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            ServiceBusMessage message = new ServiceBusMessage(jsonPayload)
                    .setContentType("application/json")
                    .setSubject("critical-feedback");

            senderClient.sendMessage(message);

        } catch (Exception e) {
            logger.error("Erro ao publicar mensagem crítica no Service Bus. Tópico: {}, Erro: {}", 
                topicName, e.getMessage(), e);
            throw new NotificationException("Falha ao publicar mensagem crítica: " + e.getMessage(), e);
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
        if (senderClient != null) {
            try {
                senderClient.close();
            } catch (Exception e) {
                logger.warn("Erro ao fechar conexão do Service Bus: {}", e.getMessage());
            }
        }
    }
}
