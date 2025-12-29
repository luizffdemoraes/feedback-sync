package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.usecases.NotifyAdminUseCase;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que processa mensagens críticas do Service Bus.
 * 
 * Segue Clean Architecture:
 * - Camada Infrastructure (handlers) → delega para Use Case (camada Application)
 * - Use Case → usa Gateway (interface da camada Domain)
 * 
 * Responsabilidade única: Receber mensagem do Service Bus e delegar ao use case.
 */
@ApplicationScoped
public class NotifyAdminFunction {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminFunction.class);

    private final NotifyAdminUseCase notifyAdminUseCase;
    private final ObjectMapper objectMapper;

    @Inject
    public NotifyAdminFunction(
            NotifyAdminUseCase notifyAdminUseCase,
            ObjectMapper objectMapper) {
        this.notifyAdminUseCase = notifyAdminUseCase;
        this.objectMapper = objectMapper;
    }

    private ObjectMapper createFeedbackObjectMapper() {
        ObjectMapper mapper = objectMapper.copy();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Feedback.class, new FeedbackDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    @FunctionName("notifyAdmin")
    public void run(
            @ServiceBusTopicTrigger(
                    name = "message",
                    topicName = "critical-feedbacks",
                    subscriptionName = "admin-notifications",
                    connection = "AzureServiceBusConnection"
            ) String message,
            final ExecutionContext context) {

        logger.info("Processando mensagem crítica do Service Bus");

        try {
            // Parse da mensagem JSON do Service Bus (usa deserializador customizado)
            ObjectMapper feedbackMapper = createFeedbackObjectMapper();
            Feedback criticalFeedback = feedbackMapper.readValue(message, Feedback.class);

            logger.info("Feedback crítico recebido do Service Bus - ID: {}", criticalFeedback.getId());

            // Delega processamento para o use case (seguindo Clean Architecture)
            notifyAdminUseCase.execute(criticalFeedback);

            logger.info("Notificação processada com sucesso pelo use case");

        } catch (NotificationException e) {
            logger.error("Erro ao processar notificação crítica: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao processar mensagem do Service Bus: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao processar notificação crítica", e);
        }
    }
}
