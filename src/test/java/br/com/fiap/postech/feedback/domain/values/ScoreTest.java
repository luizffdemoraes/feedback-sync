package br.com.fiap.postech.feedback.domain.values;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para Value Object Score")
class ScoreTest {

    @Test
    @DisplayName("Deve criar Score com valor válido entre 0 e 10")
    void deveCriarScoreComValorValido() {
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
    @DisplayName("Deve lançar exceção para valores fora do intervalo válido")
    void deveLancarExcecaoParaValoresInvalidos(int valorInvalido) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Score(valorInvalido)
        );
        
        assertTrue(exception.getMessage().contains("Score must be between 0 and 10"));
    }

    @Test
    @DisplayName("Deve retornar true para Score crítico (<= 3)")
    void deveRetornarTrueParaScoreCritico() {
        Score score1 = new Score(0);
        Score score2 = new Score(1);
        Score score3 = new Score(2);
        Score score4 = new Score(3);
        
        assertTrue(score1.isCritical());
        assertTrue(score2.isCritical());
        assertTrue(score3.isCritical());
        assertTrue(score4.isCritical());
    }

    @Test
    @DisplayName("Deve retornar false para Score não crítico (> 3)")
    void deveRetornarFalseParaScoreNaoCritico() {
        Score score4 = new Score(4);
        Score score5 = new Score(5);
        Score score10 = new Score(10);
        
        assertFalse(score4.isCritical());
        assertFalse(score5.isCritical());
        assertFalse(score10.isCritical());
    }

    @Test
    @DisplayName("Deve ser igual quando valores são iguais")
    void deveSerIgualQuandoValoresSaoIguais() {
        Score score1 = new Score(5);
        Score score2 = new Score(5);
        
        assertEquals(score1, score2);
        assertEquals(score1.hashCode(), score2.hashCode());
    }

    @Test
    @DisplayName("Não deve ser igual quando valores são diferentes")
    void naoDeveSerIgualQuandoValoresSaoDiferentes() {
        Score score1 = new Score(5);
        Score score2 = new Score(7);
        
        assertNotEquals(score1, score2);
    }

    @Test
    @DisplayName("Deve retornar string do valor no toString")
    void deveRetornarStringDoValorNoToString() {
        Score score = new Score(7);
        assertEquals("7", score.toString());
    }
}
