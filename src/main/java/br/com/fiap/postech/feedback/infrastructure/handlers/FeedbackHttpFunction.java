package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCaseImpl;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.infrastructure.gateways.EmailNotificationGatewayImpl;
import br.com.fiap.postech.feedback.infrastructure.gateways.TableStorageFeedbackGatewayImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Azure Function HTTP Trigger para receber feedbacks.
 * 
 * Expõe o endpoint POST /api/avaliacao que recebe feedbacks de avaliação.
 * 
 * Esta função cria as dependências manualmente (sem Quarkus CDI) seguindo
 * o mesmo padrão das outras funções Azure (WeeklyReportFunction).
 */
public class FeedbackHttpFunction {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackHttpFunction.class);
    private static volatile CreateFeedbackUseCase createFeedbackUseCase;
    private static final Object lock = new Object();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    
    /**
     * Obtém CreateFeedbackUseCase: cria manualmente (sem CDI) de forma lazy.
     * Thread-safe para garantir inicialização única.
     */
    private static CreateFeedbackUseCase getCreateFeedbackUseCase() {
        if (createFeedbackUseCase == null) {
            synchronized (lock) {
                if (createFeedbackUseCase == null) {
                    try {
                        logger.info("Inicializando CreateFeedbackUseCase...");
                        
                        String storageConnectionString = getStorageConnectionString();
                        String tableName = getTableName();
                        
                        // Criar gateways
                        FeedbackGateway feedbackGateway = createFeedbackGateway(storageConnectionString, tableName);
                        EmailNotificationGateway emailGateway = createEmailGateway();
                        
                        // Criar use case
                        createFeedbackUseCase = new CreateFeedbackUseCaseImpl(feedbackGateway, emailGateway);
                        
                        logger.info("CreateFeedbackUseCase inicializado com sucesso");
                    } catch (Exception e) {
                        logger.error("Erro ao inicializar CreateFeedbackUseCase: {}", e.getMessage(), e);
                        throw new RuntimeException("Falha ao inicializar CreateFeedbackUseCase: " + e.getMessage(), e);
                    }
                }
            }
        }
        return createFeedbackUseCase;
    }
    
    /**
     * Obtém a connection string do Storage Account.
     * Ordem de prioridade:
     * 1. AZURE_STORAGE_CONNECTION_STRING (padrão Azure Functions)
     * 2. AzureWebJobsStorage (usado pelo Azure Functions runtime)
     * 3. azure.storage.connection-string (formato Quarkus)
     * 4. Fallback para desenvolvimento local
     */
    private static String getStorageConnectionString() {
        String connectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
        if (connectionString == null || connectionString.isBlank()) {
            connectionString = System.getenv("AzureWebJobsStorage");
        }
        if (connectionString == null || connectionString.isBlank()) {
            connectionString = System.getenv("azure.storage.connection-string");
        }
        if (connectionString == null || connectionString.isBlank()) {
            logger.warn("AZURE_STORAGE_CONNECTION_STRING não configurada. Usando fallback local.");
            connectionString = "UseDevelopmentStorage=true";
        }
        
        // Validar produção
        if (connectionString.equals("UseDevelopmentStorage=true")) {
            String env = System.getenv("app.environment");
            if (env != null && (env.equals("production") || env.equals("prod"))) {
                throw new RuntimeException("AZURE_STORAGE_CONNECTION_STRING não configurada em ambiente de produção");
            }
        }
        
        logger.debug("Storage Connection String configurada: {}", 
            connectionString.length() > 50 ? connectionString.substring(0, 50) + "..." : connectionString);
        return connectionString;
    }
    
    /**
     * Obtém o nome da tabela do Table Storage.
     */
    private static String getTableName() {
        String tableName = System.getenv("azure.table.table-name");
        if (tableName == null || tableName.isBlank()) {
            tableName = "feedbacks";
        }
        logger.debug("Table Name: {}", tableName);
        return tableName;
    }
    
    private static FeedbackGateway createFeedbackGateway(String connectionString, String tableName) {
        try {
            logger.debug("Criando TableStorageFeedbackGatewayImpl...");
            
            TableStorageFeedbackGatewayImpl gateway = new TableStorageFeedbackGatewayImpl();
            
            // Configurar campos via reflection
            setField(gateway, "storageConnectionString", connectionString);
            setField(gateway, "tableName", tableName);
            
            // Inicializar
            invokeMethod(gateway, "init");
            
            // Verificar inicialização
            Object tableClient = getField(gateway, "tableClient");
            if (tableClient == null) {
                throw new RuntimeException("TableClient não foi inicializado corretamente");
            }
            
            logger.debug("TableStorageFeedbackGatewayImpl inicializado");
            return gateway;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("Erro ao inicializar TableStorageFeedbackGatewayImpl: {}", 
                cause != null ? cause.getMessage() : e.getMessage(), cause != null ? cause : e);
            throw new RuntimeException("Falha ao criar FeedbackGateway", cause != null ? cause : e);
        } catch (Exception e) {
            logger.error("Erro ao criar TableStorageFeedbackGatewayImpl: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar FeedbackGateway: " + e.getMessage(), e);
        }
    }
    
    private static EmailNotificationGateway createEmailGateway() {
        try {
            logger.debug("Criando EmailNotificationGatewayImpl...");
            
            String mailtrapToken = System.getenv("MAILTRAP_API_TOKEN");
            String adminEmail = System.getenv("ADMIN_EMAIL");
            String mailtrapInboxIdStr = System.getenv("MAILTRAP_INBOX_ID");
            
            Long mailtrapInboxId = null;
            if (mailtrapInboxIdStr != null && !mailtrapInboxIdStr.isBlank()) {
                try {
                    mailtrapInboxId = Long.parseLong(mailtrapInboxIdStr.trim());
                } catch (NumberFormatException e) {
                    logger.warn("MAILTRAP_INBOX_ID inválido: '{}'", mailtrapInboxIdStr);
                }
            }
            
            EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(mailtrapToken, adminEmail, mailtrapInboxId);
            invokeMethod(gateway, "init");
            
            Object mailtrapClient = getField(gateway, "mailtrapClient");
            if (mailtrapClient == null) {
                logger.warn("MailtrapClient não inicializado. Verifique configurações do Mailtrap.");
            }
            
            logger.debug("EmailNotificationGatewayImpl inicializado");
            return gateway;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("Erro ao inicializar EmailNotificationGatewayImpl: {}", 
                cause != null ? cause.getMessage() : e.getMessage(), cause != null ? cause : e);
            throw new RuntimeException("Falha ao criar EmailNotificationGateway", cause != null ? cause : e);
        } catch (Exception e) {
            logger.error("Erro ao criar EmailNotificationGatewayImpl: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar EmailNotificationGateway: " + e.getMessage(), e);
        }
    }
    
    /**
     * Métodos auxiliares para reflection (evita duplicação de código).
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    private static Object getField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
    
    private static void invokeMethod(Object target, String methodName) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    @FunctionName("avaliacao")
    public HttpResponseMessage submitFeedback(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "avaliacao"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Recebendo requisição POST /api/avaliacao");
        
        try {
            // Ler corpo da requisição
            Optional<String> bodyOptional = request.getBody();
            if (bodyOptional.isEmpty() || bodyOptional.get().isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Corpo da requisição é obrigatório\"}")
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .build();
            }
            
            String bodyString = bodyOptional.get();
            
            // Converter JSON para FeedbackRequest
            FeedbackRequest feedbackRequest = objectMapper.readValue(bodyString, FeedbackRequest.class);
            
            // Validar campos obrigatórios
            if (feedbackRequest.description() == null || feedbackRequest.description().trim().isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'descricao' é obrigatório\"}")
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .build();
            }
            
            if (feedbackRequest.score() == null) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'nota' é obrigatório\"}")
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .build();
            }
            
            if (feedbackRequest.score() < 0 || feedbackRequest.score() > 10) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'nota' deve estar entre 0 e 10\"}")
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .build();
            }
            
            // Processar feedback (inicialização lazy)
            CreateFeedbackUseCase useCase = getCreateFeedbackUseCase();
            
            logger.info("Processando feedback: descricao={}, nota={}, urgencia={}", 
                feedbackRequest.description(), feedbackRequest.score(), feedbackRequest.urgency());
            
            FeedbackResponse response = useCase.execute(feedbackRequest);
            
            logger.info("Feedback processado com sucesso: id={}", response.id());
            
            // Retornar resposta de sucesso
            String responseBody = objectMapper.writeValueAsString(Map.of(
                "id", response.id(),
                "status", "recebido"
            ));
            
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body(responseBody)
                    .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .build();
                    
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Erro ao processar JSON", e);
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"JSON inválido: " + e.getMessage() + "\"}")
                    .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            logger.error("Erro ao processar feedback", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Erro interno: " + e.getMessage() + "\"}")
                    .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .build();
        }
    }
}
