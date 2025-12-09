package br.com.fiap.postech.feedback.domain.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;

import java.time.Instant;
import java.util.List;

public interface FeedbackGateway {
    void save(Feedback feedback);
    List<Feedback> findByPeriod(Instant from, Instant to);
}
