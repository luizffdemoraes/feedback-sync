package br.com.fiap.postech.feedback.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Configuração do Jackson para serialização/deserialização JSON.
 * 
 * Configuração simplificada - usa apenas @JsonProperty no FeedbackRequest.
 * Campos em português: descricao, nota, urgencia
 */
@ApplicationScoped
public class JacksonConfig {

    @Produces
    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}