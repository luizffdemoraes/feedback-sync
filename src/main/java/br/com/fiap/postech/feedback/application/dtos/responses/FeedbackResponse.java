package br.com.fiap.postech.feedback.application.dtos.responses;

import java.time.LocalDateTime;

/**
 * DTO de resposta para feedback.
 * Usa record do Java 14+ para imutabilidade e simplicidade.
 */
public record FeedbackResponse(
    String id,
    Integer score,
    String description,
    LocalDateTime createdAt
) {
    public FeedbackResponse {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID não pode ser nulo ou vazio");
        }
    }
}
