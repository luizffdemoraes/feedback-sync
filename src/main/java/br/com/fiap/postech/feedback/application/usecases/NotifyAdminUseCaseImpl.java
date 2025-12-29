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
 * Responsabilidade: Processar feedback crítico e enviar notificação via gateway.
 * 
 * Nota: O envio real de email é feito pelo Logic App que escuta o Service Bus.
 * Este use case processa e loga a notificação para monitoramento.
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
        logger.info("Processando notificação crítica - ID: {}, Nota: {}, Urgência: {}",
                criticalFeedback.getId(),
                criticalFeedback.getScore().getValue(),
                criticalFeedback.getUrgency().getValue());

        try {
            // Monta mensagem formatada para o administrador
            String notificationMessage = buildNotificationMessage(criticalFeedback);
            
            // Envia notificação via gateway
            // Nota: A implementação atual envia para Service Bus, mas o Logic App
            // pegará a mensagem original e enviará o email real
            notificationGateway.sendAdminNotification(notificationMessage);
            
            logger.info("Notificação processada com sucesso. Logic App enviará email automaticamente.");

        } catch (NotificationException e) {
            logger.error("Erro ao processar notificação crítica: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao processar notificação crítica: {}", e.getMessage(), e);
            throw new NotificationException("Falha ao processar notificação crítica", e);
        }
    }

    /**
     * Constrói a mensagem de notificação formatada para o administrador.
     */
    private String buildNotificationMessage(Feedback feedback) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 ALERTA: Feedback Crítico Recebido\n\n");
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

