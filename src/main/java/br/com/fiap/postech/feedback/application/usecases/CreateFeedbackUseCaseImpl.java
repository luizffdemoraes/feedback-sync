package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.QueueNotificationGateway;
import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;
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
 * - Notificar admin se crítico (via email direto)
 */
@ApplicationScoped
public class CreateFeedbackUseCaseImpl implements CreateFeedbackUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateFeedbackUseCaseImpl.class);

    private final FeedbackGateway feedbackGateway;
    private final QueueNotificationGateway queueNotificationGateway;

    @Inject
    public CreateFeedbackUseCaseImpl(
            FeedbackGateway feedbackGateway,
            QueueNotificationGateway queueNotificationGateway) {
        this.feedbackGateway = feedbackGateway;
        this.queueNotificationGateway = queueNotificationGateway;
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

        // Notificação não bloqueante - publica na fila se crítico
        if (feedback.isCritical()) {
            try {
                // Publica na fila Azure Queue Storage para processamento assíncrono
                queueNotificationGateway.publishCritical(feedback);
                logger.debug("Notificação crítica publicada na fila - ID: {}", feedback.getId());
            } catch (NotificationException e) {
                // Loga o erro mas não falha a requisição - o feedback já foi salvo
                logger.error("Erro ao publicar notificação crítica (feedback já salvo). ID: {}, Erro: {}", 
                    feedback.getId(), e.getMessage());
            } catch (Exception e) {
                // Captura qualquer outra exceção inesperada
                logger.error("Erro inesperado ao publicar notificação crítica (feedback já salvo). ID: {}, Erro: {}", 
                    feedback.getId(), e.getMessage());
            }
        }

        return new FeedbackResponse(
            feedback.getId(),
            feedback.getScore().getValue(),
            feedback.getDescription(),
            feedback.getCreatedAt()
        );
    }
}