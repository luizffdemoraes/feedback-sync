package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCaseImpl;
import br.com.fiap.postech.feedback.infrastructure.gateways.BlobReportStorageGatewayImpl;
import br.com.fiap.postech.feedback.infrastructure.gateways.TableStorageFeedbackGatewayImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que gera relatório semanal de feedbacks automaticamente.
 * 
 * Executa conforme agendamento configurado via variável de ambiente REPORT_SCHEDULE_CRON.
 * Por padrão, executa a cada 5 minutos em ambiente local (para testes).
 * 
 * Responsabilidade única: Agendar e disparar a geração de relatórios semanais
 * 
 * NOTA: Esta função cria dependências manualmente (sem CDI) para evitar problemas
 * de inicialização com Azure Functions TimerTrigger.
 */
public class WeeklyReportFunction {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyReportFunction.class);

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
            // Cria dependências manualmente (sem CDI para evitar problemas de inicialização)
            logger.info("Criando dependências manualmente...");
            GenerateWeeklyReportUseCase useCase = getGenerateWeeklyReportUseCase();
            
            logger.info("Iniciando geração do relatório semanal...");
            var report = useCase.execute();

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

    /**
     * Obtém GenerateWeeklyReportUseCase: cria manualmente (sem CDI).
     * Package-private para permitir mock em testes.
     */
    GenerateWeeklyReportUseCase getGenerateWeeklyReportUseCase() {
        logger.info("Criando GenerateWeeklyReportUseCase manualmente");
        
        // Obter variáveis de ambiente
        String storageConnectionString = System.getenv("azure.storage.connection-string");
        if (storageConnectionString == null || storageConnectionString.isBlank()) {
            storageConnectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
        }
        if (storageConnectionString == null || storageConnectionString.isBlank()) {
            storageConnectionString = "UseDevelopmentStorage=true"; // Padrão para Azurite local
        }
        
        String tableName = System.getenv("azure.table.table-name");
        if (tableName == null || tableName.isBlank()) {
            tableName = "feedbacks";
        }
        
        String containerName = System.getenv("azure.storage.container-name");
        if (containerName == null || containerName.isBlank()) {
            containerName = "weekly-reports";
        }
        
        logger.info("Configurações - Table: {}, Container: {}", tableName, containerName);
        
        // Criar gateways manualmente usando reflection para configurar campos privados
        try {
            // Criar TableStorageFeedbackGatewayImpl
            TableStorageFeedbackGatewayImpl feedbackGateway = new TableStorageFeedbackGatewayImpl();
            java.lang.reflect.Field storageField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("storageConnectionString");
            storageField.setAccessible(true);
            storageField.set(feedbackGateway, storageConnectionString);
            
            java.lang.reflect.Field tableField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableName");
            tableField.setAccessible(true);
            tableField.set(feedbackGateway, tableName);
            
            // Inicializar via reflection
            java.lang.reflect.Method initMethod = TableStorageFeedbackGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(feedbackGateway);
            logger.info("✓ TableStorageFeedbackGatewayImpl inicializado");
            
            // Criar BlobReportStorageGatewayImpl
            BlobReportStorageGatewayImpl reportStorageGateway = new BlobReportStorageGatewayImpl(
                storageConnectionString,
                containerName,
                getObjectMapper()
            );
            
            // Inicializar via reflection
            java.lang.reflect.Method blobInitMethod = BlobReportStorageGatewayImpl.class.getDeclaredMethod("init");
            blobInitMethod.setAccessible(true);
            blobInitMethod.invoke(reportStorageGateway);
            logger.info("✓ BlobReportStorageGatewayImpl inicializado");
            
            // Criar use case
            GenerateWeeklyReportUseCase useCase = new GenerateWeeklyReportUseCaseImpl(
                feedbackGateway,
                reportStorageGateway
            );
            
            logger.info("✓ GenerateWeeklyReportUseCase criado manualmente");
            return useCase;
            
        } catch (Exception e) {
            logger.error("❌ Erro ao criar dependências manualmente: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar GenerateWeeklyReportUseCase", e);
        }
    }

    /**
     * Obtém ObjectMapper: cria manualmente (sem CDI).
     * Package-private para permitir mock em testes.
     */
    ObjectMapper getObjectMapper() {
        logger.info("Criando ObjectMapper manualmente");
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
