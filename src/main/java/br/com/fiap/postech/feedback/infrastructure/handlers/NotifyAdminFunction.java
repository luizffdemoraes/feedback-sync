package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Azure Function que processa mensagens críticas do Service Bus
 * e envia notificações aos administradores.
 * 
 * Responsabilidade única: Processar eventos críticos e notificar administradores
 */
public class NotifyAdminFunction {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminFunction.class);

    @Inject
    NotificationGateway notificationGateway;

    @Inject
    ObjectMapper objectMapper;

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
            // Parse da mensagem JSON do Service Bus
            Feedback criticalFeedback = objectMapper.readValue(message, Feedback.class);

            logger.info("Feedback crítico recebido - ID: {}, Nota: {}, Urgência: {}",
                    criticalFeedback.getId(),
                    criticalFeedback.getScore(),
                    criticalFeedback.getUrgency());

            // Monta mensagem de notificação para o administrador
            String notificationMessage = buildNotificationMessage(criticalFeedback);

            // Envia notificação via gateway (pode ser email, log, etc)
            notificationGateway.sendAdminNotification(notificationMessage);

            logger.info("Notificação enviada ao administrador com sucesso");

        } catch (Exception e) {
            logger.error("Erro ao processar mensagem crítica: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao processar notificação crítica", e);
        }
    }

    /**
     * Constrói a mensagem de notificação formatada para o administrador
     */
    private String buildNotificationMessage(Feedback feedback) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 ALERTA: Feedback Crítico Recebido\n\n");
        message.append("ID: ").append(feedback.getId()).append("\n");
        message.append("Descrição: ").append(feedback.getDescription()).append("\n");
        message.append("Nota: ").append(feedback.getScore()).append("/10\n");
        message.append("Urgência: ").append(feedback.getUrgency()).append("\n");
        message.append("Data de Envio: ").append(
                feedback.getCreatedAt() != null 
                    ? feedback.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ).append("\n");
        
        return message.toString();
    }
}
