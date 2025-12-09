package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CreateFeedbackUseCaseImpl implements CreateFeedbackUseCase {

    @Inject
    FeedbackGateway feedbackGateway;

    @Inject
    NotificationGateway notificationGateway;

    private static final int CRITICAL_THRESHOLD = 3;

    @Override
    public FeedbackResponse execute(FeedbackRequest request) {
        // basic validation
        if (request.score == null) throw new IllegalArgumentException("score is required");
        Feedback feedback = new Feedback(request.description, request.score, request.urgency == null ? "LOW" : request.urgency);
        feedbackGateway.save(feedback);
        if (feedback.getScore() <= CRITICAL_THRESHOLD) {
            notificationGateway.publishCritical(feedback);
        }
        return new FeedbackResponse(
                feedback.getId(),
                null,
                feedback.getScore(),
                feedback.getDescription(),
                feedback.getCreatedAt()
        );

    }
}