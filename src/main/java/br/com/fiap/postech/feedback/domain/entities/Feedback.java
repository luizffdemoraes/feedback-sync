package br.com.fiap.postech.feedback.domain.entities;

import br.com.fiap.postech.feedback.domain.values.Score;
import br.com.fiap.postech.feedback.domain.values.Urgency;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade de domínio Feedback.
 * Representa um feedback de avaliação de aula.
 * 
 * Imutável após criação (sem setters públicos).
 * Usa Value Objects para garantir invariantes.
 */
public class Feedback {
    private String id;
    private String description;
    private Score score;
    private Urgency urgency;
    private LocalDateTime createdAt;

    public Feedback(String description, Score score, Urgency urgency) {
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.score = score;
        this.urgency = urgency;
        this.createdAt = LocalDateTime.now();
    }

    public Feedback(String description, int scoreValue, String urgencyValue) {
        this(description, new Score(scoreValue), Urgency.of(urgencyValue));
    }

    public static Feedback reconstruct(String id, String description, int scoreValue, 
                                      String urgencyValue, LocalDateTime createdAt) {
        Feedback feedback = new Feedback(description, new Score(scoreValue), Urgency.of(urgencyValue));
        feedback.id = id;
        feedback.createdAt = createdAt;
        return feedback;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Score getScore() {
        return score;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCritical() {
        return score.isCritical();
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}