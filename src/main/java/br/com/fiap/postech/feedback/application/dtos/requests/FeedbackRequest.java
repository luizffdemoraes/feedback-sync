package br.com.fiap.postech.feedback.application.dtos.requests;

public class FeedbackRequest {
    public String description;
    public Integer score;
    public String urgency;

    // default constructor for JSON deserialization
    public FeedbackRequest() {}
}