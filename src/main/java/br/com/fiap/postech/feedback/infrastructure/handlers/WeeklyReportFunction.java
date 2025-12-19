package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que gera relatório semanal de feedbacks automaticamente.
 * 
 * Executa toda segunda-feira às 08:00 (configurável via cron expression).
 * 
 * Responsabilidade única: Agendar e disparar a geração de relatórios semanais
 */
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
                    schedule = "0 0 8 * * MON"  // Toda segunda-feira às 08:00
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
