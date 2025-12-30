package br.com.fiap.postech.feedback.application.dtos.responses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para WeeklyReportResponse")
class WeeklyReportResponseTest {

    @Test
    @DisplayName("Deve criar WeeklyReportResponse com construtor padrão")
    void deveCriarWeeklyReportResponseComConstrutorPadrao() {
        WeeklyReportResponse response = new WeeklyReportResponse();

        assertNotNull(response);
        assertNull(response.getPeriodoInicio());
        assertNull(response.getPeriodoFim());
        assertNull(response.getTotalAvaliacoes());
        assertNull(response.getMediaAvaliacoes());
        assertNull(response.getAvaliacoesPorDia());
        assertNull(response.getAvaliacoesPorUrgencia());
        assertNull(response.getReportUrl());
    }

    @Test
    @DisplayName("Deve criar WeeklyReportResponse com construtor completo")
    void deveCriarWeeklyReportResponseComConstrutorCompleto() {
        Instant inicio = Instant.now().minusSeconds(604800);
        Instant fim = Instant.now();
        Map<String, Long> porDia = new HashMap<>();
        porDia.put("2024-01-15", 5L);
        Map<String, Long> porUrgencia = new HashMap<>();
        porUrgencia.put("HIGH", 2L);
        String url = "https://storage.blob.core.windows.net/reports/relatorio.json";

        WeeklyReportResponse response = new WeeklyReportResponse(
            inicio, fim, 10, 7.5, porDia, porUrgencia, url
        );

        assertEquals(inicio, response.getPeriodoInicio());
        assertEquals(fim, response.getPeriodoFim());
        assertEquals(10, response.getTotalAvaliacoes());
        assertEquals(7.5, response.getMediaAvaliacoes());
        assertEquals(porDia, response.getAvaliacoesPorDia());
        assertEquals(porUrgencia, response.getAvaliacoesPorUrgencia());
        assertEquals(url, response.getReportUrl());
    }

    @Test
    @DisplayName("Deve permitir setar e obter todos os campos")
    void devePermitirSetarEObterTodosOsCampos() {
        WeeklyReportResponse response = new WeeklyReportResponse();
        Instant inicio = Instant.now();
        Instant fim = Instant.now();
        Map<String, Long> porDia = new HashMap<>();
        Map<String, Long> porUrgencia = new HashMap<>();

        response.setPeriodoInicio(inicio);
        response.setPeriodoFim(fim);
        response.setTotalAvaliacoes(5);
        response.setMediaAvaliacoes(8.0);
        response.setAvaliacoesPorDia(porDia);
        response.setAvaliacoesPorUrgencia(porUrgencia);
        response.setReportUrl("https://test.com/report.json");

        assertEquals(inicio, response.getPeriodoInicio());
        assertEquals(fim, response.getPeriodoFim());
        assertEquals(5, response.getTotalAvaliacoes());
        assertEquals(8.0, response.getMediaAvaliacoes());
        assertEquals(porDia, response.getAvaliacoesPorDia());
        assertEquals(porUrgencia, response.getAvaliacoesPorUrgencia());
        assertEquals("https://test.com/report.json", response.getReportUrl());
    }
}
