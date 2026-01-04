package br.com.fiap.postech.feedback.domain.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Value Object que representa a nota de um feedback.
 * Garante que o valor está sempre entre 0 e 10.
 * 
 * Imutável e validado no construtor.
 */
public final class Score {
    
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 10;
    
    private final int value;
    
    @JsonCreator
    public Score(@JsonProperty("value") int value) {
        if (value < MIN_SCORE || value > MAX_SCORE) {
            throw new IllegalArgumentException(
                String.format("Score must be between %d and %d, but was %d", 
                    MIN_SCORE, MAX_SCORE, value)
            );
        }
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public boolean isCritical() {
        return value <= 3;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Score score = (Score) o;
        return value == score.value;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
