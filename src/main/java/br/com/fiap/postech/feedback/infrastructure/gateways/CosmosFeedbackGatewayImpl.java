package br.com.fiap.postech.feedback.infrastructure.gateways;


import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class CosmosFeedbackGatewayImpl implements FeedbackGateway {

    // Inject Cosmos DB client (use Azure SDK or Micronaut/Quarkus integration)
    @Override
    public void save(Feedback feedback) {
        // map to document and persist
    }

    @Override
    public List<Feedback> findByPeriod(Instant from, Instant to) {
        // query cosmos and map back
        return List.of();
    }
}
