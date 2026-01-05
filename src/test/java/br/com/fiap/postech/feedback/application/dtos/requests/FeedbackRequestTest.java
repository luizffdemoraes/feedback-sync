package br.com.fiap.postech.feedback.application.dtos.requests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para FeedbackRequest")
class FeedbackRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deve criar FeedbackRequest com todos os campos")
    void deveCriarFeedbackRequestComTodosOsCampos() {
        String description = "Ótimo atendimento";
        Integer score = 8;
        String urgency = "HIGH";

        FeedbackRequest request = new FeedbackRequest(description, score, urgency);

        assertEquals(description, request.description());
        assertEquals(score, request.score());
        assertEquals(urgency, request.urgency());
    }

    @Test
    @DisplayName("Deve permitir campos null")
    void devePermitirCamposNull() {
        FeedbackRequest request = new FeedbackRequest(null, null, null);

        assertNull(request.description());
        assertNull(request.score());
        assertNull(request.urgency());
    }

    @Test
    @DisplayName("Deve deserializar JSON com campos em português")
    void deveDeserializarJsonComCamposEmPortugues() throws Exception {
        String json = """
            {
                "descricao": "Ótimo atendimento",
                "nota": 8,
                "urgencia": "HIGH"
            }
            """;

        FeedbackRequest request = objectMapper.readValue(json, FeedbackRequest.class);

        assertEquals("Ótimo atendimento", request.description());
        assertEquals(8, request.score());
        assertEquals("HIGH", request.urgency());
    }

    @Test
    @DisplayName("Deve deserializar JSON com campos opcionais faltando")
    void deveDeserializarJsonComCamposOpcionaisFaltando() throws Exception {
        String json = """
            {
                "descricao": "Teste",
                "nota": 5
            }
            """;

        FeedbackRequest request = objectMapper.readValue(json, FeedbackRequest.class);

        assertEquals("Teste", request.description());
        assertEquals(5, request.score());
        assertNull(request.urgency());
    }

    @Test
    @DisplayName("Deve serializar para JSON com campos em português")
    void deveSerializarParaJsonComCamposEmPortugues() throws Exception {
        FeedbackRequest request = new FeedbackRequest("Teste", 8, "HIGH");
        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"descricao\""));
        assertTrue(json.contains("\"nota\""));
        assertTrue(json.contains("\"urgencia\""));
        assertTrue(json.contains("Teste"));
        assertTrue(json.contains("8"));
        assertTrue(json.contains("HIGH"));
    }
}
