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

        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("⏰ TIMER TRIGGER DISPARADO - WeeklyReportFunction");
        logger.info("═══════════════════════════════════════════════════════════");
        logger.info("Timer Info: {}", timerInfo);
        logger.info("Schedule configurado: {}", System.getenv("REPORT_SCHEDULE_CRON"));
        logger.info("Timestamp: {}", java.time.Instant.now());
        context.getLogger().info("🔵 WeeklyReportFunction executada - " + java.time.Instant.now());

        try {
            logger.info("Iniciando geração do relatório semanal...");
            var report = generateWeeklyReportUseCase.execute();

            logger.info("═══════════════════════════════════════════════════════════");
            logger.info("✅ RELATÓRIO SEMANAL GERADO COM SUCESSO");
            logger.info("═══════════════════════════════════════════════════════════");
            logger.info("  - Período: {} até {}", report.getPeriodoInicio(), report.getPeriodoFim());
            logger.info("  - Total de avaliações: {}", report.getTotalAvaliacoes());
            logger.info("  - Média: {}", report.getMediaAvaliacoes());
            logger.info("  - URL do relatório: {}", report.getReportUrl() != null ? report.getReportUrl() : "N/A (relatório vazio)");
            
            if (report.getTotalAvaliacoes() == 0) {
                logger.warn("⚠️  ATENÇÃO: Nenhum feedback encontrado no período. Relatório não foi salvo no storage.");
            }
            
            context.getLogger().info("✅ Relatório gerado - Total: " + report.getTotalAvaliacoes());

        } catch (Exception e) {
            logger.error("❌ ERRO ao gerar relatório semanal", e);
            context.getLogger().severe("❌ ERRO: " + e.getMessage());
            throw new RuntimeException("Falha ao gerar relatório semanal", e);
        }
    }
}
