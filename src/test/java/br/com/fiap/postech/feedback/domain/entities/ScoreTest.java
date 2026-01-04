package br.com.fiap.postech.feedback.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para Value Object Score")
class ScoreTest {

    @Test
    @DisplayName("Deve criar Score válido entre 0 e 10")
    void deveCriarScoreValido() {
        Score score = new Score(5);
        assertEquals(5, score.getValue());
    }

    @Test
    @DisplayName("Deve criar Score com valor mínimo (0)")
    void deveCriarScoreComValorMinimo() {
        Score score = new Score(0);
        assertEquals(0, score.getValue());
    }

    @Test
    @DisplayName("Deve criar Score com valor máximo (10)")
    void deveCriarScoreComValorMaximo() {
        Score score = new Score(10);
        assertEquals(10, score.getValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, 11, 100})
    @DisplayName("Deve lançar exceção para valores fora do range válido")
    void deveLancarExcecaoParaValoresInvalidos(int invalidValue) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Score(invalidValue)
        );
        
        assertTrue(exception.getMessage().contains("Score must be between 0 and 10"));
    }

    @Test
    @DisplayName("Deve identificar Score crítico (<= 3)")
    void deveIdentificarScoreCritico() {
        assertTrue(new Score(0).isCritical());
        assertTrue(new Score(1).isCritical());
        assertTrue(new Score(2).isCritical());
        assertTrue(new Score(3).isCritical());
        assertFalse(new Score(4).isCritical());
        assertFalse(new Score(5).isCritical());
        assertFalse(new Score(10).isCritical());
    }

    @Test
    @DisplayName("Deve comparar Scores corretamente usando equals")
    void deveCompararScoresCorretamente() {
        Score score1 = new Score(5);
        Score score2 = new Score(5);
        Score score3 = new Score(7);

        assertEquals(score1, score2);
        assertNotEquals(score1, score3);
    }

    @Test
    @DisplayName("Deve retornar hashCode consistente")
    void deveRetornarHashCodeConsistente() {
        Score score1 = new Score(5);
        Score score2 = new Score(5);

        assertEquals(score1.hashCode(), score2.hashCode());
    }

    @Test
    @DisplayName("Deve retornar string representando o valor")
    void deveRetornarStringRepresentandoValor() {
        Score score = new Score(7);
        assertEquals("7", score.toString());
    }
}
