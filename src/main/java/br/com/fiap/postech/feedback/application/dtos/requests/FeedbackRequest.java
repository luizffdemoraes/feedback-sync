package br.com.fiap.postech.feedback.application.dtos.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de requisição para criação de feedback.
 * 
 * Campos Java em inglês, JSON em português:
 * - description (Java) / descricao (JSON)
 * - score (Java) / nota (JSON)
 * - urgency (Java) / urgencia (JSON)
 */
public record FeedbackRequest(
        @JsonProperty("descricao")
        String description,
        
        @JsonProperty("nota")
        Integer score,
        
        @JsonProperty("urgencia")
        String urgency) {
}