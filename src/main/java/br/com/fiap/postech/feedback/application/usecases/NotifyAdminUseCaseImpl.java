package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Implementação do caso de uso para notificar administradores sobre feedbacks críticos.
 * 
 * Responsabilidade: Processar feedback crítico recebido do Service Bus e notificar via gateway.
 * 
 * Fluxo correto:
 * 1. CreateFeedbackUseCase → publishCritical(feedback) → Service Bus (tópico: critical-feedbacks)
 * 2. Service Bus trigger → NotifyAdminFunction → NotifyAdminUseCase (este método)
 * 3. NotifyAdminUseCase → sendAdminNotification() → Service Bus (tópico: critical-feedbacks, subject: admin-notification)
 * 4. Logic App consome mensagem com subject "admin-notification" e envia email
 * 
 * Segue padrão do projeto: Use Case chama Gateway (igual CreateFeedbackUseCase e GenerateWeeklyReportUseCase).
 */
@ApplicationScoped
public class NotifyAdminUseCaseImpl implements NotifyAdminUseCase {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminUseCaseImpl.class);

    private final NotificationGateway notificationGateway;

    @Inject
    public NotifyAdminUseCaseImpl(NotificationGateway notificationGateway) {
        this.notificationGateway = notificationGateway;
    }

    @Override
    public void execute(Feedback criticalFeedback) {
        try {
            String notificationMessage = buildNotificationMessage(criticalFeedback);
            notificationGateway.sendAdminNotification(notificationMessage);
            logger.debug("Notificação crítica processada - ID: {}", criticalFeedback.getId());
        } catch (NotificationException e) {
            logger.error("Erro ao processar notificação crítica: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao processar notificação crítica: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao processar notificação crítica", e);
        }
    }

    private String buildNotificationMessage(Feedback feedback) {
        StringBuilder message = new StringBuilder();
        message.append("ALERTA: Feedback Crítico Recebido\n\n");
        message.append("ID: ").append(feedback.getId()).append("\n");
        message.append("Descrição: ").append(feedback.getDescription()).append("\n");
        message.append("Nota: ").append(feedback.getScore().getValue()).append("/10\n");
        message.append("Urgência: ").append(feedback.getUrgency().getValue()).append("\n");
        message.append("Data de Envio: ").append(
                feedback.getCreatedAt() != null 
                    ? feedback.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ).append("\n");
        
        return message.toString();
    }
}

