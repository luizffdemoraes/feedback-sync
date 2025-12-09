package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;

public interface CreateFeedbackUseCase {
    FeedbackResponse execute(FeedbackRequest request);
}
