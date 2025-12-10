package br.com.fiap.postech.feedback.application.dtos.requests;

public record FeedbackRequest(
        String description,
        Integer score,
        String urgency) {
}