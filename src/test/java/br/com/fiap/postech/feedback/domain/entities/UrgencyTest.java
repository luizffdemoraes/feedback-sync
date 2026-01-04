package br.com.fiap.postech.feedback.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para Value Object Urgency")
class UrgencyTest {

    @Test
    @DisplayName("Deve criar Urgency LOW usando método of")
    void deveCriarUrgencyLow() {
        Urgency urgency = Urgency.of("LOW");
        assertEquals("LOW", urgency.getValue());
        assertTrue(urgency.isLow());
        assertFalse(urgency.isMedium());
        assertFalse(urgency.isHigh());
    }

    @Test
    @DisplayName("Deve criar Urgency MEDIUM usando método of")
    void deveCriarUrgencyMedium() {
        Urgency urgency = Urgency.of("MEDIUM");
        assertEquals("MEDIUM", urgency.getValue());
        assertFalse(urgency.isLow());
        assertTrue(urgency.isMedium());
        assertFalse(urgency.isHigh());
    }

    @Test
    @DisplayName("Deve criar Urgency HIGH usando método of")
    void deveCriarUrgencyHigh() {
        Urgency urgency = Urgency.of("HIGH");
        assertEquals("HIGH", urgency.getValue());
        assertFalse(urgency.isLow());
        assertFalse(urgency.isMedium());
        assertTrue(urgency.isHigh());
    }

    @Test
    @DisplayName("Deve aceitar valores em minúsculas e converter para maiúsculas")
    void deveAceitarValoresEmMinusculas() {
        Urgency urgency1 = Urgency.of("low");
        Urgency urgency2 = Urgency.of("medium");
        Urgency urgency3 = Urgency.of("high");
        
        assertEquals("LOW", urgency1.getValue());
        assertEquals("MEDIUM", urgency2.getValue());
        assertEquals("HIGH", urgency3.getValue());
    }

    @Test
    @DisplayName("Deve retornar LOW quando valor é null")
    void deveRetornarLowQuandoValorENull() {
        Urgency urgency = Urgency.of(null);
        assertEquals("LOW", urgency.getValue());
        assertTrue(urgency.isLow());
    }

    @Test
    @DisplayName("Deve retornar LOW quando valor é vazio")
    void deveRetornarLowQuandoValorEVazio() {
        Urgency urgency1 = Urgency.of("");
        Urgency urgency2 = Urgency.of("   ");
        
        assertEquals("LOW", urgency1.getValue());
        assertEquals("LOW", urgency2.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "CRITICAL", "URGENT", "abc", "123"})
    @DisplayName("Deve lançar exceção para valores inválidos")
    void deveLancarExcecaoParaValoresInvalidos(String invalidValue) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Urgency.of(invalidValue)
        );
        
        assertTrue(exception.getMessage().contains("Urgency must be LOW, MEDIUM or HIGH"));
    }

    @Test
    @DisplayName("Deve usar constantes estáticas corretamente")
    void deveUsarConstantesEstaticasCorretamente() {
        assertEquals("LOW", Urgency.LOW.getValue());
        assertEquals("MEDIUM", Urgency.MEDIUM.getValue());
        assertEquals("HIGH", Urgency.HIGH.getValue());
    }

    @Test
    @DisplayName("Deve comparar Urgencies corretamente usando equals")
    void deveCompararUrgenciesCorretamente() {
        Urgency urgency1 = Urgency.of("HIGH");
        Urgency urgency2 = Urgency.of("HIGH");
        Urgency urgency3 = Urgency.of("LOW");

        assertEquals(urgency1, urgency2);
        assertEquals(Urgency.HIGH, urgency1);
        assertNotEquals(urgency1, urgency3);
    }

    @Test
    @DisplayName("Deve retornar hashCode consistente")
    void deveRetornarHashCodeConsistente() {
        Urgency urgency1 = Urgency.of("MEDIUM");
        Urgency urgency2 = Urgency.of("MEDIUM");

        assertEquals(urgency1.hashCode(), urgency2.hashCode());
    }

    @Test
    @DisplayName("Deve retornar string representando o valor")
    void deveRetornarStringRepresentandoValor() {
        Urgency urgency = Urgency.of("HIGH");
        assertEquals("HIGH", urgency.toString());
    }

    @Test
    @DisplayName("Deve criar Urgency usando construtor com valor válido")
    void deveCriarUrgencyUsandoConstrutor() {
        Urgency urgency = new Urgency("MEDIUM");
        assertEquals("MEDIUM", urgency.getValue());
    }

    @Test
    @DisplayName("Deve criar Urgency LOW usando construtor com valor null")
    void deveCriarUrgencyLowUsandoConstrutorComNull() {
        Urgency urgency = new Urgency(null);
        assertEquals("LOW", urgency.getValue());
    }
}
