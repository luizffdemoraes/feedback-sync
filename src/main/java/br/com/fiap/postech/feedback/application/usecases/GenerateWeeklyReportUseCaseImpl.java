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
 * 
 * Fluxo de execução:
 * 1. Calcula período: semana atual (segunda-feira até hoje)
 *    - Busca a segunda-feira da semana atual
 *    - Período vai até hoje para incluir feedbacks recentes
 * 2. Busca feedbacks do período via FeedbackGateway
 * 3. Calcula métricas:
 *    - Média das notas
 *    - Total de avaliações
 *    - Avaliações por dia
 *    - Avaliações por urgência (LOW, MEDIUM, HIGH)
 * 4. Monta estrutura JSON com todos os dados
 * 5. Salva relatório no Blob Storage via ReportStorageGateway
 * 6. Retorna WeeklyReportResponse com métricas e URL do relatório
 */
@ApplicationScoped
public class GenerateWeeklyReportUseCaseImpl implements GenerateWeeklyReportUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GenerateWeeklyReportUseCaseImpl.class);

    private final FeedbackGateway feedbackGateway;
    private final ReportStorageGateway reportStorageGateway;

    @Inject
    public GenerateWeeklyReportUseCaseImpl(
            FeedbackGateway feedbackGateway,
            ReportStorageGateway reportStorageGateway) {
        this.feedbackGateway = feedbackGateway;
        this.reportStorageGateway = reportStorageGateway;
    }

    /**
     * Executa a geração do relatório semanal.
     * 
     * Calcula o período da semana atual (segunda-feira até hoje) e busca
     * todos os feedbacks desse período. Calcula métricas consolidadas e
     * salva o relatório em formato JSON no Blob Storage.
     * 
     * @return WeeklyReportResponse com dados do relatório gerado
     */
    @Override
    public WeeklyReportResponse execute() {
        logger.info("Iniciando geração de relatório semanal");

        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate lastSunday = today;

        Instant startOfWeek = lastMonday.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfWeek = lastSunday.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        logger.info("Período do relatório: {} até {}", startOfWeek, endOfWeek);

        var feedbacks = feedbackGateway.findByPeriod(startOfWeek, endOfWeek);

        if (feedbacks.isEmpty()) {
            logger.warn("Nenhum feedback encontrado no período");
            return createEmptyReport(startOfWeek, endOfWeek);
        }

        double average = feedbacks.stream()
                .mapToInt(f -> f.getScore().getValue())
                .average()
                .orElse(0.0);

        Map<String, Long> dailyCount = new HashMap<>();
        Map<String, Long> urgencyCount = new HashMap<>();

        feedbacks.forEach(f -> {
            String day = f.getCreatedAt().toLocalDate().toString();
            dailyCount.put(day, dailyCount.getOrDefault(day, 0L) + 1);

            String urgency = f.getUrgency().getValue();
            urgencyCount.put(urgency, urgencyCount.getOrDefault(urgency, 0L) + 1);
        });
 
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("periodo_inicio", startOfWeek.toString());
        reportData.put("periodo_fim", endOfWeek.toString());
        reportData.put("total_avaliacoes", feedbacks.size());
        reportData.put("media_avaliacoes", Math.round(average * 100.0) / 100.0);
        reportData.put("avaliacoes_por_dia", dailyCount);
        reportData.put("avaliacoes_por_urgencia", urgencyCount);
        reportData.put("data_geracao", Instant.now().toString());
        reportData.put("feedbacks", feedbacks.stream()
                .map(f -> {
                    Map<String, Object> feedbackMap = new HashMap<>();
                    feedbackMap.put("descricao", f.getDescription());
                    feedbackMap.put("urgencia", f.getUrgency().getValue());
                    feedbackMap.put("data_envio", f.getCreatedAt().toString());
                    feedbackMap.put("nota", f.getScore().getValue());
                    return feedbackMap;
                })
                .toList());

        String fileName = reportStorageGateway.saveWeeklyReport(reportData);
        String reportUrl = reportStorageGateway.getReportUrl(fileName);

        logger.info("Relatório semanal gerado e salvo: {}", fileName);

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
