package br.com.fiap.postech.feedback.infrastructure.gateways;

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

@ApplicationScoped
public class ServiceBusNotificationGatewayImpl implements NotificationGateway {

    @ConfigProperty(name = "azure.servicebus.connection-string")
    String connectionString;

    @ConfigProperty(name = "azure.servicebus.topic-name")
    String topicName;

    @Inject
    ObjectMapper objectMapper;

    private ServiceBusSenderClient senderClient;

    @PostConstruct
    void init() {
        senderClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .topicName(topicName)
                .buildClient();

        System.out.println("✅ Service Bus conectado ao tópico: " + topicName);
    }

    @Override
    public void publishCritical(Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            ServiceBusMessage message = new ServiceBusMessage(jsonPayload)
                    .setContentType("application/json")
                    .setSubject("critical-feedback");

            senderClient.sendMessage(message);

            System.out.println("📤 Mensagem crítica publicada no Service Bus");

        } catch (Exception e) {
            System.err.println("❌ Erro ao publicar no Service Bus: " + e.getMessage());
            throw new RuntimeException("Falha ao publicar mensagem crítica", e);
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        try {
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(message)
                    .setContentType("text/plain")
                    .setSubject("admin-notification");

            senderClient.sendMessage(serviceBusMessage);

            System.out.println("📧 Notificação enviada ao admin: " + message);

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar notificação: " + e.getMessage());
        }
    }

    @PreDestroy
    void cleanup() {
        if (senderClient != null) {
            senderClient.close();
            System.out.println("🔌 Service Bus desconectado");
        }
    }
}
