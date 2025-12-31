package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.infrastructure.config.FunctionProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que processa notificações críticas de feedbacks.
 * 
 * Tipo: Queue Trigger
 * Responsabilidade única: Processar mensagens da fila e enviar emails via Mailtrap
 * 
 * Fluxo:
 * 1. CreateFeedbackUseCase publica feedback crítico na fila Azure Queue Storage
 * 2. Azure Queue Storage dispara esta função automaticamente
 * 3. Função deserializa o feedback e envia email via Mailtrap
 * 
 * Integração com Recursos Azure:
 * - Azure Queue Storage (trigger)
 * - Mailtrap (envio de emails)
 */
@ApplicationScoped
public class NotifyAdminFunction {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminFunction.class);

    private final EmailNotificationGateway emailGateway;
    private final ObjectMapper objectMapper;

    @Inject
    public NotifyAdminFunction(
            EmailNotificationGateway emailGateway,
            ObjectMapper objectMapper) {
        this.emailGateway = emailGateway;
        this.objectMapper = objectMapper;
    }

    @FunctionName("notifyAdmin")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "critical-feedbacks",
                    connection = "AzureWebJobsStorage"
            ) String message,
            final ExecutionContext context) {

        try {
            Feedback feedback = objectMapper.readValue(message, Feedback.class);
            logger.info("Processando feedback crítico - ID: {}, Nota: {}", 
                feedback.getId(), feedback.getScore().getValue());

            emailGateway.sendAdminNotification(buildEmailContent(feedback));
            logger.info("Notificação enviada com sucesso - ID: {}", feedback.getId());
        } catch (Exception e) {
            logger.error("Erro ao processar notificação", e);
            throw new FunctionProcessingException("Falha ao processar notificação crítica", e);
        }
    }

    /**
     * Constrói conteúdo do email a partir do feedback.
     */
    private String buildEmailContent(Feedback feedback) {
        StringBuilder content = new StringBuilder();
        content.append("ALERTA: Feedback Crítico Recebido\n\n");
        content.append("Detalhes do Feedback:\n");
        content.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        content.append("ID: ").append(feedback.getId()).append("\n");
        content.append("Descrição: ").append(feedback.getDescription()).append("\n");
        content.append("Nota: ").append(feedback.getScore().getValue()).append("/10\n");
        content.append("Urgência: ").append(feedback.getUrgency().toString()).append("\n");
        content.append("Data de Envio: ").append(feedback.getCreatedAt()).append("\n");
        content.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        content.append("Este é um email automático do sistema de feedback.\n");
        content.append("Por favor, analise este feedback crítico com urgência.\n");
        return content.toString();
    }
}
