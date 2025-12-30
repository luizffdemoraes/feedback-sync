package br.com.fiap.postech.feedback.infrastructure.controllers;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;
import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para ReportController")
class ReportControllerTest {

    @Mock
    private GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    @InjectMocks
    private ReportController reportController;

    private WeeklyReportResponse reportResponse;

    @BeforeEach
    void setUp() {
        reportResponse = new WeeklyReportResponse();
        reportResponse.setPeriodoInicio(Instant.now().minusSeconds(604800)); // 7 dias atrás
        reportResponse.setPeriodoFim(Instant.now());
        reportResponse.setTotalAvaliacoes(10);
        reportResponse.setMediaAvaliacoes(7.5);
        reportResponse.setAvaliacoesPorDia(new HashMap<>());
        reportResponse.setAvaliacoesPorUrgencia(new HashMap<>());
        reportResponse.setReportUrl("https://storage.blob.core.windows.net/reports/relatorio.json");
    }

    @Test
    @DisplayName("Deve gerar relatório semanal com sucesso e retornar 200 OK")
    void deveGerarRelatorioSemanalComSucesso() {
        when(generateWeeklyReportUseCase.execute())
            .thenReturn(reportResponse);

        Response response = reportController.generateWeeklyReport();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        
        assertNotNull(entity.get("periodo_inicio"));
        assertNotNull(entity.get("periodo_fim"));
        assertEquals(10, entity.get("total_avaliacoes"));
        assertEquals(7.5, entity.get("media_avaliacoes"));
        assertNotNull(entity.get("avaliacoes_por_dia"));
        assertNotNull(entity.get("avaliacoes_por_urgencia"));
        assertEquals("https://storage.blob.core.windows.net/reports/relatorio.json", entity.get("report_url"));

        verify(generateWeeklyReportUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("Deve lidar com relatório vazio")
    void deveLidarComRelatorioVazio() {
        WeeklyReportResponse reportVazio = new WeeklyReportResponse();
        reportVazio.setPeriodoInicio(Instant.now());
        reportVazio.setPeriodoFim(Instant.now());
        reportVazio.setTotalAvaliacoes(0);
        reportVazio.setMediaAvaliacoes(0.0);
        reportVazio.setAvaliacoesPorDia(new HashMap<>());
        reportVazio.setAvaliacoesPorUrgencia(new HashMap<>());
        reportVazio.setReportUrl(null);

        when(generateWeeklyReportUseCase.execute())
            .thenReturn(reportVazio);

        Response response = reportController.generateWeeklyReport();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        
        assertEquals(0, entity.get("total_avaliacoes"));
        assertEquals(0.0, entity.get("media_avaliacoes"));
        assertNull(entity.get("report_url"));
    }

    @Test
    @DisplayName("Deve lidar com período nulo")
    void deveLidarComPeriodoNulo() {
        WeeklyReportResponse reportComPeriodoNulo = new WeeklyReportResponse();
        reportComPeriodoNulo.setPeriodoInicio(null);
        reportComPeriodoNulo.setPeriodoFim(null);
        reportComPeriodoNulo.setTotalAvaliacoes(5);
        reportComPeriodoNulo.setMediaAvaliacoes(8.0);
        reportComPeriodoNulo.setAvaliacoesPorDia(new HashMap<>());
        reportComPeriodoNulo.setAvaliacoesPorUrgencia(new HashMap<>());
        reportComPeriodoNulo.setReportUrl("https://storage.blob.core.windows.net/reports/relatorio.json");

        when(generateWeeklyReportUseCase.execute())
            .thenReturn(reportComPeriodoNulo);

        Response response = reportController.generateWeeklyReport();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        
        assertNull(entity.get("periodo_inicio"));
        assertNull(entity.get("periodo_fim"));
    }
}
