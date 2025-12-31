package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.QueueNotificationGateway;
import com.azure.core.exception.AzureException;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementação do QueueNotificationGateway usando Azure Queue Storage.
 * 
 * Responsabilidade: Publicar notificações críticas na fila Azure Queue Storage.
 * A Azure Function NotifyAdminFunction processa as mensagens da fila e envia emails via SendGrid.
 * 
 * Fluxo:
 * 1. CreateFeedbackUseCase → publishCritical(feedback) → QueueNotificationGatewayImpl
 * 2. QueueNotificationGatewayImpl → Azure Queue Storage (fila: critical-feedbacks)
 * 3. Azure Queue Storage → Trigger → NotifyAdminFunction
 * 4. NotifyAdminFunction → SendGrid → Email ao admin
 */
@ApplicationScoped
public class QueueNotificationGatewayImpl implements QueueNotificationGateway {

    private static final Logger logger = LoggerFactory.getLogger(QueueNotificationGatewayImpl.class);
    private static final String QUEUE_NAME = "critical-feedbacks";

    private final String connectionString;
    private final ObjectMapper objectMapper;
    private QueueClient queueClient;

    @Inject
    public QueueNotificationGatewayImpl(
            @ConfigProperty(name = "azure.storage.connection-string") String connectionString,
            ObjectMapper objectMapper) {
        this.connectionString = connectionString;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            queueClient = new QueueClientBuilder()
                    .connectionString(connectionString)
                    .queueName(QUEUE_NAME)
                    .buildClient();

            // Criar a fila se não existir
            queueClient.createIfNotExists();
            logger.info("Azure Queue Storage inicializado. Fila: {}", QUEUE_NAME);
        } catch (AzureException e) {
            String errorMessage = String.format("Erro ao inicializar Azure Queue Storage. Fila: %s", QUEUE_NAME);
            throw new NotificationException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = String.format("Erro inesperado ao inicializar Azure Queue Storage. Fila: %s", QUEUE_NAME);
            throw new NotificationException(errorMessage, e);
        }
    }

    @Override
    public void publishCritical(Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            queueClient.sendMessage(jsonPayload);
            logger.debug("Mensagem crítica publicada na fila: {}", QUEUE_NAME);
        } catch (JsonProcessingException e) {
            String payloadType = payload != null ? payload.getClass().getSimpleName() : "null";
            String errorMessage = String.format("Erro ao serializar payload para JSON. Fila: %s. Tipo do payload: %s", 
                    QUEUE_NAME, payloadType);
            throw new NotificationException(errorMessage, e);
        } catch (AzureException e) {
            String errorMessage = String.format("Erro ao publicar mensagem na fila Azure Queue Storage. Fila: %s", QUEUE_NAME);
            throw new NotificationException(errorMessage, e);
        } catch (Exception e) {
            String payloadType = payload != null ? payload.getClass().getSimpleName() : "null";
            String errorMessage = String.format("Erro inesperado ao publicar mensagem crítica. Fila: %s. Tipo do payload: %s", 
                    QUEUE_NAME, payloadType);
            throw new NotificationException(errorMessage, e);
        }
    }

    @PreDestroy
    void cleanup() {
        // QueueClient não precisa ser fechado explicitamente
        logger.info("Azure Queue Storage desconectado");
    }
}
