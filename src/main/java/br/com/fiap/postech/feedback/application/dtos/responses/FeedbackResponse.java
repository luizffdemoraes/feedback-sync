package br.com.fiap.postech.feedback.application.dtos.responses;

import java.time.LocalDateTime;

public class FeedbackResponse {

    private String id;
    private Long customerId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    public FeedbackResponse() {
    }

    public FeedbackResponse(String id, Long customerId, Integer rating, String comment, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
