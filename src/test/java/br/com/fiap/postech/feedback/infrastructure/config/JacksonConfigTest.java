package br.com.fiap.postech.feedback.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para JacksonConfig")
class JacksonConfigTest {

    @Test
    @DisplayName("Deve criar ObjectMapper configurado corretamente")
    void deveCriarObjectMapperConfiguradoCorretamente() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        assertNotNull(mapper);
        assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    @DisplayName("Deve serializar LocalDateTime sem timestamps")
    void deveSerializarLocalDateTimeSemTimestamps() throws Exception {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        String json = mapper.writeValueAsString(dateTime);

        assertNotNull(json);
        assertFalse(json.matches("\\d+"));
        assertTrue(json.contains("2024"));
    }

    @Test
    @DisplayName("Deve deserializar LocalDateTime corretamente")
    void deveDeserializarLocalDateTimeCorretamente() throws Exception {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        String json = "\"2024-01-15T10:30:00\"";
        LocalDateTime dateTime = mapper.readValue(json, LocalDateTime.class);

        assertNotNull(dateTime);
        assertEquals(2024, dateTime.getYear());
        assertEquals(1, dateTime.getMonthValue());
        assertEquals(15, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
    }

    @Test
    @DisplayName("Deve retornar mesmo ObjectMapper em múltiplas chamadas (Singleton)")
    void deveRetornarMesmoObjectMapperEmMultiplasChamadas() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper1 = config.objectMapper();
        ObjectMapper mapper2 = config.objectMapper();

        assertNotNull(mapper1);
        assertNotNull(mapper2);
    }
}
