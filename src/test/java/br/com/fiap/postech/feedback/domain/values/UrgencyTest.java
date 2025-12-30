package br.com.fiap.postech.feedback.domain.values;

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
        assertEquals(Urgency.LOW, urgency);
        assertTrue(urgency.isLow());
    }

    @Test
    @DisplayName("Deve retornar LOW quando valor é vazio")
    void deveRetornarLowQuandoValorEVazio() {
        Urgency urgency1 = Urgency.of("");
        Urgency urgency2 = Urgency.of("   ");
        
        assertEquals(Urgency.LOW, urgency1);
        assertEquals(Urgency.LOW, urgency2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "TEST", "ABC", "123"})
    @DisplayName("Deve lançar exceção para valores inválidos")
    void deveLancarExcecaoParaValoresInvalidos(String valorInvalido) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Urgency.of(valorInvalido)
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
    @DisplayName("Deve ser igual quando valores são iguais")
    void deveSerIgualQuandoValoresSaoIguais() {
        Urgency urgency1 = Urgency.of("HIGH");
        Urgency urgency2 = Urgency.of("HIGH");
        
        assertEquals(urgency1, urgency2);
        assertEquals(urgency1.hashCode(), urgency2.hashCode());
    }

    @Test
    @DisplayName("Não deve ser igual quando valores são diferentes")
    void naoDeveSerIgualQuandoValoresSaoDiferentes() {
        Urgency urgency1 = Urgency.of("LOW");
        Urgency urgency2 = Urgency.of("HIGH");
        
        assertNotEquals(urgency1, urgency2);
    }

    @Test
    @DisplayName("Deve retornar string do valor no toString")
    void deveRetornarStringDoValorNoToString() {
        Urgency urgency = Urgency.of("MEDIUM");
        assertEquals("MEDIUM", urgency.toString());
    }
}
