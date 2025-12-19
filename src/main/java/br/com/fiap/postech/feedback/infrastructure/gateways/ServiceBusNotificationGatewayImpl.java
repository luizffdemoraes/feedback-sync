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

    @ConfigProperty(name = "azure.servicebus.connection-string")
    String connectionString;

    @ConfigProperty(name = "azure.servicebus.topic-name")
    String topicName;

    @Inject
    ObjectMapper objectMapper;

    private ServiceBusSenderClient senderClient;

    @PostConstruct
    void init() {
        try {
            senderClient = new ServiceBusClientBuilder()
                    .connectionString(connectionString)
                    .sender()
                    .topicName(topicName)
                    .buildClient();

            logger.info("Service Bus conectado ao tópico: {}", topicName);
        } catch (Exception e) {
            logger.error("Erro ao conectar ao Service Bus: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao conectar ao Service Bus", e);
        }
    }

    @Override
    public void publishCritical(Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            ServiceBusMessage message = new ServiceBusMessage(jsonPayload)
                    .setContentType("application/json")
                    .setSubject("critical-feedback");

            senderClient.sendMessage(message);

            logger.info("Mensagem crítica publicada no Service Bus. Tópico: {}", topicName);

        } catch (Exception e) {
            logger.error("Erro ao publicar no Service Bus: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao publicar mensagem crítica", e);
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        try {
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message)
                    .setContentType("text/plain")
                    .setSubject("admin-notification");

            senderClient.sendMessage(serviceBusMessage);

            logger.info("Notificação enviada ao admin via Service Bus");

        } catch (Exception e) {
            logger.error("Erro ao enviar notificação: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao enviar notificação ao admin", e);
        }
    }

    @PreDestroy
    void cleanup() {
        if (senderClient != null) {
            try {
                senderClient.close();
                logger.info("Service Bus desconectado");
            } catch (Exception e) {
                logger.warn("Erro ao fechar conexão do Service Bus: {}", e.getMessage());
            }
        }
    }
}
