package br.com.fiap.postech.feedback.domain.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Value Object que representa o nível de urgência de um feedback.
 * Valores válidos: LOW, MEDIUM, HIGH.
 * 
 * Imutável e validado no construtor.
 */
public final class Urgency {
    
    public static final Urgency LOW = new Urgency("LOW");
    public static final Urgency MEDIUM = new Urgency("MEDIUM");
    public static final Urgency HIGH = new Urgency("HIGH");
    
    private final String value;
    
    @JsonCreator
    public Urgency(@JsonProperty("value") String value) {
        if (value == null || value.isBlank()) {
            this.value = "LOW";
            return;
        }
        
        String upperValue = value.toUpperCase();
        if (!upperValue.equals("LOW") && 
            !upperValue.equals("MEDIUM") && 
            !upperValue.equals("HIGH")) {
            throw new IllegalArgumentException(
                String.format("Urgency must be LOW, MEDIUM or HIGH, but was %s", value)
            );
        }
        this.value = upperValue;
    }
    
    public static Urgency of(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        
        String upperValue = value.toUpperCase();
        return switch (upperValue) {
            case "LOW" -> LOW;
            case "MEDIUM" -> MEDIUM;
            case "HIGH" -> HIGH;
            default -> throw new IllegalArgumentException(
                String.format("Urgency must be LOW, MEDIUM or HIGH, but was %s", value)
            );
        };
    }
    
    public String getValue() {
        return value;
    }
    
    public boolean isLow() {
        return value.equals("LOW");
    }
    
    public boolean isMedium() {
        return value.equals("MEDIUM");
    }
    
    public boolean isHigh() {
        return value.equals("HIGH");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Urgency urgency = (Urgency) o;
        return Objects.equals(value, urgency.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
