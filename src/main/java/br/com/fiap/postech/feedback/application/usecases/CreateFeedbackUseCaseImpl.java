package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
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
 * - Notificar se crítico
 */
@ApplicationScoped
public class CreateFeedbackUseCaseImpl implements CreateFeedbackUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateFeedbackUseCaseImpl.class);

    private final FeedbackGateway feedbackGateway;
    private final NotificationGateway notificationGateway;

    @Inject
    public CreateFeedbackUseCaseImpl(
            FeedbackGateway feedbackGateway,
            NotificationGateway notificationGateway) {
        this.feedbackGateway = feedbackGateway;
        this.notificationGateway = notificationGateway;
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

        // Notificação não bloqueante - não falha a requisição se o Service Bus estiver indisponível
        if (feedback.isCritical()) {
            logger.info("Feedback crítico detectado, enviando notificação. ID: {}", feedback.getId());
            try {
                notificationGateway.publishCritical(feedback);
                logger.info("Notificação crítica enviada com sucesso para o Service Bus");
            } catch (NotificationException e) {
                // Loga o erro mas não falha a requisição - o feedback já foi salvo
                logger.error("Erro ao enviar notificação crítica (feedback já salvo). ID: {}, Erro: {}", 
                    feedback.getId(), e.getMessage(), e);
                // Não relança a exceção - permite que a requisição seja concluída com sucesso
            } catch (Exception e) {
                // Captura qualquer outra exceção inesperada
                logger.error("Erro inesperado ao enviar notificação crítica (feedback já salvo). ID: {}, Erro: {}", 
                    feedback.getId(), e.getMessage(), e);
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