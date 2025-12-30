package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.ReportStorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para GenerateWeeklyReportUseCaseImpl")
class GenerateWeeklyReportUseCaseImplTest {

    @Mock
    private FeedbackGateway feedbackGateway;

    @Mock
    private ReportStorageGateway reportStorageGateway;

    @InjectMocks
    private GenerateWeeklyReportUseCaseImpl generateWeeklyReportUseCase;

    private List<Feedback> feedbacks;

    @BeforeEach
    void setUp() {
        feedbacks = new ArrayList<>();
    }

    @Test
    @DisplayName("Deve gerar relatório com feedbacks existentes")
    void deveGerarRelatorioComFeedbacksExistentes() {
        LocalDateTime hoje = LocalDateTime.now();
        LocalDateTime ontem = hoje.minusDays(1);

        Feedback feedback1 = new Feedback("Boa aula", 7, "MEDIUM");
        feedback1.setCreatedAt(hoje);
        feedback1.setId("id1");

        Feedback feedback2 = new Feedback("Excelente", 9, "LOW");
        feedback2.setCreatedAt(ontem);
        feedback2.setId("id2");

        Feedback feedback3 = new Feedback("Ruim", 2, "HIGH");
        feedback3.setCreatedAt(hoje);
        feedback3.setId("id3");

        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        feedbacks.add(feedback3);

        when(feedbackGateway.findByPeriod(any(Instant.class), any(Instant.class)))
            .thenReturn(feedbacks);
        when(reportStorageGateway.saveWeeklyReport(anyMap()))
            .thenReturn("relatorio-2024-01-15.json");
        when(reportStorageGateway.getReportUrl("relatorio-2024-01-15.json"))
            .thenReturn("https://storage.blob.core.windows.net/reports/relatorio-2024-01-15.json");

        WeeklyReportResponse response = generateWeeklyReportUseCase.execute();

        assertNotNull(response);
        assertEquals(3, response.getTotalAvaliacoes());
        assertEquals(6.0, response.getMediaAvaliacoes(), 0.01);
        assertNotNull(response.getAvaliacoesPorDia());
        assertNotNull(response.getAvaliacoesPorUrgencia());
        assertNotNull(response.getReportUrl());

        verify(feedbackGateway, times(1)).findByPeriod(any(Instant.class), any(Instant.class));
        verify(reportStorageGateway, times(1)).saveWeeklyReport(anyMap());
        verify(reportStorageGateway, times(1)).getReportUrl(anyString());
    }

    @Test
    @DisplayName("Deve gerar relatório vazio quando não há feedbacks")
    void deveGerarRelatorioVazioQuandoNaoHaFeedbacks() {
        when(feedbackGateway.findByPeriod(any(Instant.class), any(Instant.class)))
            .thenReturn(new ArrayList<>());

        WeeklyReportResponse response = generateWeeklyReportUseCase.execute();

        assertNotNull(response);
        assertEquals(0, response.getTotalAvaliacoes());
        assertEquals(0.0, response.getMediaAvaliacoes());
        assertNotNull(response.getAvaliacoesPorDia());
        assertTrue(response.getAvaliacoesPorDia().isEmpty());
        assertNotNull(response.getAvaliacoesPorUrgencia());
        assertTrue(response.getAvaliacoesPorUrgencia().isEmpty());

        verify(feedbackGateway, times(1)).findByPeriod(any(Instant.class), any(Instant.class));
        verify(reportStorageGateway, never()).saveWeeklyReport(anyMap());
    }

    @Test
    @DisplayName("Deve calcular média corretamente")
    void deveCalcularMediaCorretamente() {
        Feedback feedback1 = new Feedback("Aula 1", 5, "LOW");
        feedback1.setCreatedAt(LocalDateTime.now());
        feedback1.setId("id1");

        Feedback feedback2 = new Feedback("Aula 2", 7, "MEDIUM");
        feedback2.setCreatedAt(LocalDateTime.now());
        feedback2.setId("id2");

        Feedback feedback3 = new Feedback("Aula 3", 9, "LOW");
        feedback3.setCreatedAt(LocalDateTime.now());
        feedback3.setId("id3");

        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        feedbacks.add(feedback3);

        when(feedbackGateway.findByPeriod(any(Instant.class), any(Instant.class)))
            .thenReturn(feedbacks);
        when(reportStorageGateway.saveWeeklyReport(anyMap()))
            .thenReturn("relatorio.json");
        when(reportStorageGateway.getReportUrl(anyString()))
            .thenReturn("https://storage.blob.core.windows.net/reports/relatorio.json");

        WeeklyReportResponse response = generateWeeklyReportUseCase.execute();

        assertEquals(7.0, response.getMediaAvaliacoes(), 0.01);
    }

    @Test
    @DisplayName("Deve agrupar avaliações por dia corretamente")
    void deveAgruparAvaliacoesPorDiaCorretamente() {
        LocalDateTime hoje = LocalDateTime.now();
        LocalDateTime ontem = hoje.minusDays(1);

        Feedback feedback1 = new Feedback("Aula 1", 5, "LOW");
        feedback1.setCreatedAt(hoje);
        feedback1.setId("id1");

        Feedback feedback2 = new Feedback("Aula 2", 7, "MEDIUM");
        feedback2.setCreatedAt(hoje);
        feedback2.setId("id2");

        Feedback feedback3 = new Feedback("Aula 3", 9, "LOW");
        feedback3.setCreatedAt(ontem);
        feedback3.setId("id3");

        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        feedbacks.add(feedback3);

        when(feedbackGateway.findByPeriod(any(Instant.class), any(Instant.class)))
            .thenReturn(feedbacks);
        when(reportStorageGateway.saveWeeklyReport(anyMap()))
            .thenReturn("relatorio.json");
        when(reportStorageGateway.getReportUrl(anyString()))
            .thenReturn("https://storage.blob.core.windows.net/reports/relatorio.json");

        WeeklyReportResponse response = generateWeeklyReportUseCase.execute();

        Map<String, Long> avaliacoesPorDia = response.getAvaliacoesPorDia();
        assertTrue(avaliacoesPorDia.containsKey(hoje.toLocalDate().toString()));
        assertTrue(avaliacoesPorDia.containsKey(ontem.toLocalDate().toString()));
    }

    @Test
    @DisplayName("Deve agrupar avaliações por urgência corretamente")
    void deveAgruparAvaliacoesPorUrgenciaCorretamente() {
        Feedback feedback1 = new Feedback("Aula 1", 5, "LOW");
        feedback1.setCreatedAt(LocalDateTime.now());
        feedback1.setId("id1");

        Feedback feedback2 = new Feedback("Aula 2", 7, "MEDIUM");
        feedback2.setCreatedAt(LocalDateTime.now());
        feedback2.setId("id2");

        Feedback feedback3 = new Feedback("Aula 3", 2, "HIGH");
        feedback3.setCreatedAt(LocalDateTime.now());
        feedback3.setId("id3");

        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        feedbacks.add(feedback3);

        when(feedbackGateway.findByPeriod(any(Instant.class), any(Instant.class)))
            .thenReturn(feedbacks);
        when(reportStorageGateway.saveWeeklyReport(anyMap()))
            .thenReturn("relatorio.json");
        when(reportStorageGateway.getReportUrl(anyString()))
            .thenReturn("https://storage.blob.core.windows.net/reports/relatorio.json");

        WeeklyReportResponse response = generateWeeklyReportUseCase.execute();

        Map<String, Long> avaliacoesPorUrgencia = response.getAvaliacoesPorUrgencia();
        assertEquals(1L, avaliacoesPorUrgencia.get("LOW"));
        assertEquals(1L, avaliacoesPorUrgencia.get("MEDIUM"));
        assertEquals(1L, avaliacoesPorUrgencia.get("HIGH"));
    }
}
