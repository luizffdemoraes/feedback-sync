package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que gera relatório semanal de feedbacks automaticamente.
 * 
 * Executa conforme agendamento configurado via variável de ambiente REPORT_SCHEDULE_CRON.
 * Por padrão, executa a cada 5 minutos em ambiente local (para testes).
 * 
 * Responsabilidade única: Agendar e disparar a geração de relatórios semanais
 */
@ApplicationScoped
public class WeeklyReportFunction {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportFunction.class);

    private final GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    @Inject
    public WeeklyReportFunction(GenerateWeeklyReportUseCase generateWeeklyReportUseCase) {
        this.generateWeeklyReportUseCase = generateWeeklyReportUseCase;
    }

    @FunctionName("weeklyReport")
    public void run(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "%REPORT_SCHEDULE_CRON%"  // Configurado via variável de ambiente (application.properties/local.settings.json)
            ) String timerInfo,
            final ExecutionContext context) {

        logger.info("Timer disparado para geração de relatório semanal: {}", timerInfo);

        try {
            var report = generateWeeklyReportUseCase.execute();

            logger.info("Relatório semanal gerado com sucesso:");
            logger.info("  - Período: {} até {}", report.getPeriodoInicio(), report.getPeriodoFim());
            logger.info("  - Total de avaliações: {}", report.getTotalAvaliacoes());
            logger.info("  - Média: {}", report.getMediaAvaliacoes());
            logger.info("  - URL do relatório: {}", report.getReportUrl());

        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar relatório semanal", e);
        }
    }
}
