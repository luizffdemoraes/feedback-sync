package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.ReportStorageGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

/**
 * Caso de uso para gerar relatório semanal consolidado de feedbacks.
 * 
 * Responsabilidade: Orquestrar a coleta de dados, cálculo de métricas
 * e persistência do relatório.
 */
@ApplicationScoped
public class GenerateWeeklyReportUseCaseImpl implements GenerateWeeklyReportUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GenerateWeeklyReportUseCaseImpl.class);

    @Inject
    FeedbackGateway feedbackGateway;

    @Inject
    ReportStorageGateway reportStorageGateway;

    @Override
    public WeeklyReportResponse execute() {
        logger.info("Iniciando geração de relatório semanal");

        // Calcula período da semana anterior (segunda a domingo)
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate lastSunday = lastMonday.plusDays(6);

        Instant startOfWeek = lastMonday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfWeek = lastSunday.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        logger.info("Período do relatório: {} até {}", startOfWeek, endOfWeek);

        // Busca feedbacks do período
        var feedbacks = feedbackGateway.findByPeriod(startOfWeek, endOfWeek);

        if (feedbacks.isEmpty()) {
            logger.warn("Nenhum feedback encontrado no período");
            return createEmptyReport(startOfWeek, endOfWeek);
        }

        // Calcula métricas
        double average = feedbacks.stream()
                .mapToInt(f -> f.getScore())
                .average()
                .orElse(0.0);

        Map<String, Long> dailyCount = new HashMap<>();
        Map<String, Long> urgencyCount = new HashMap<>();

        feedbacks.forEach(f -> {
            // Contagem por dia
            String day = f.getCreatedAt().toLocalDate().toString();
            dailyCount.put(day, dailyCount.getOrDefault(day, 0L) + 1);

            // Contagem por urgência
            String urgency = f.getUrgency() != null ? f.getUrgency() : "LOW";
            urgencyCount.put(urgency, urgencyCount.getOrDefault(urgency, 0L) + 1);
        });

        // Monta dados do relatório
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("periodo_inicio", startOfWeek.toString());
        reportData.put("periodo_fim", endOfWeek.toString());
        reportData.put("total_avaliacoes", feedbacks.size());
        reportData.put("media_avaliacoes", Math.round(average * 100.0) / 100.0);
        reportData.put("avaliacoes_por_dia", dailyCount);
        reportData.put("avaliacoes_por_urgencia", urgencyCount);
        reportData.put("data_geracao", Instant.now().toString());

        // Salva relatório no Blob Storage
        String fileName = reportStorageGateway.saveWeeklyReport(reportData);
        String reportUrl = reportStorageGateway.getReportUrl(fileName);

        logger.info("Relatório semanal gerado e salvo: {}", fileName);

        // Retorna resposta
        WeeklyReportResponse response = new WeeklyReportResponse();
        response.setPeriodoInicio(startOfWeek);
        response.setPeriodoFim(endOfWeek);
        response.setTotalAvaliacoes(feedbacks.size());
        response.setMediaAvaliacoes(Math.round(average * 100.0) / 100.0);
        response.setAvaliacoesPorDia(dailyCount);
        response.setAvaliacoesPorUrgencia(urgencyCount);
        response.setReportUrl(reportUrl);

        return response;
    }

    private WeeklyReportResponse createEmptyReport(Instant startOfWeek, Instant endOfWeek) {
        WeeklyReportResponse response = new WeeklyReportResponse();
        response.setPeriodoInicio(startOfWeek);
        response.setPeriodoFim(endOfWeek);
        response.setTotalAvaliacoes(0);
        response.setMediaAvaliacoes(0.0);
        response.setAvaliacoesPorDia(new HashMap<>());
        response.setAvaliacoesPorUrgencia(new HashMap<>());
        return response;
    }
}
