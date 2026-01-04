package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.entities.Score;
import br.com.fiap.postech.feedback.domain.entities.Urgency;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso para criar um novo feedback.
 * 
 * Responsabilidades:
 * - Validar dados de entrada
 * - Criar entidade de domínio
 * - Persistir feedback
 * - Notificar admin se crítico (envio direto de email via Mailtrap)
 */
@ApplicationScoped
public class CreateFeedbackUseCaseImpl implements CreateFeedbackUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateFeedbackUseCaseImpl.class);

    private final FeedbackGateway feedbackGateway;
    private final EmailNotificationGateway emailNotificationGateway;

    @Inject
    public CreateFeedbackUseCaseImpl(
            FeedbackGateway feedbackGateway,
            EmailNotificationGateway emailNotificationGateway) {
        this.feedbackGateway = feedbackGateway;
        this.emailNotificationGateway = emailNotificationGateway;
    }

    @Override
    public FeedbackResponse execute(FeedbackRequest request) {
        logger.debug("Criando feedback: descricao={}, nota={}, urgencia={}", 
            request.description(), request.score(), request.urgency());

        if (request.description() == null || request.description().isBlank()) {
            throw new FeedbackDomainException("Descrição é obrigatória");
        }

        if (request.score() == null) {
            throw new FeedbackDomainException("Nota é obrigatória");
        }

        Score score = new Score(request.score());
        Urgency urgency = request.urgency() != null 
            ? Urgency.of(request.urgency()) 
            : Urgency.LOW;

        Feedback feedback = new Feedback(request.description(), score, urgency);

        feedbackGateway.save(feedback);

        if (feedback.isCritical()) {
            logger.info("Feedback crítico detectado (nota: {}) - Enviando notificação por email...", feedback.getScore().getValue());
            logger.info("EmailNotificationGateway disponível: {}", emailNotificationGateway != null ? "SIM" : "NÃO");
            try {
                String emailContent = buildEmailContent(feedback);
                emailNotificationGateway.sendAdminNotification(emailContent);
                logger.info("Notificação crítica enviada por email com sucesso - ID: {}", feedback.getId());
            } catch (NotificationException e) {
                logger.error("ERRO ao enviar notificação por email (feedback já salvo). ID: {}, Erro: {}", 
                    feedback.getId(), e.getMessage(), e);
                logger.error("Stack trace completo:", e);
                // Não relançar exceção - feedback já foi salvo, mas logamos o erro completo
            } catch (Exception e) {
                logger.error("ERRO inesperado ao enviar notificação por email (feedback já salvo). ID: {}, Erro: {}, Tipo: {}", 
                    feedback.getId(), e.getMessage(), e.getClass().getName(), e);
                logger.error("Stack trace completo:", e);
                // Não relançar exceção - feedback já foi salvo, mas logamos o erro completo
            }
        } else {
            logger.debug("Feedback não crítico (nota: {}) - Não será enviada notificação", feedback.getScore().getValue());
        }

        return new FeedbackResponse(
            feedback.getId(),
            feedback.getScore().getValue(),
            feedback.getDescription(),
            feedback.getCreatedAt()
        );
    }

    /**
     * Constrói o conteúdo do email para notificação de feedback crítico.
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
