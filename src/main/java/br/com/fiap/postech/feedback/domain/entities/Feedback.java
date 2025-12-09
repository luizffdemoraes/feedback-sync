package br.com.fiap.postech.feedback.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Feedback {
    private String id;
    private String description;
    private int score; // 0..10
    private String urgency; // LOW, MEDIUM, HIGH
    private LocalDateTime createdAt;

    public Feedback(String description, int score, String urgency) {
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.score = score;
        this.urgency = urgency;
        this.createdAt = LocalDateTime.now();
    }

    // getters and setters omitted for brevity

    public String getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public String getUrgency() {
        return urgency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}