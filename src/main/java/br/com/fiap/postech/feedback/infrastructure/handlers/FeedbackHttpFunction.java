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
    
    /**
     * Obtém CreateFeedbackUseCase: cria manualmente (sem CDI) de forma lazy.
     * Thread-safe para garantir inicialização única.
     */
    private static CreateFeedbackUseCase getCreateFeedbackUseCase() {
        if (createFeedbackUseCase == null) {
            synchronized (lock) {
                if (createFeedbackUseCase == null) {
                    try {
                        logger.info("Inicializando CreateFeedbackUseCase manualmente (lazy initialization)...");
                        
                        // Obter configurações de variáveis de ambiente
                        // IMPORTANTE: Ordem de prioridade para compatibilidade com Azure Functions:
                        // 1. AZURE_STORAGE_CONNECTION_STRING (padrão Azure Functions)
                        // 2. AzureWebJobsStorage (usado pelo Azure Functions runtime)
                        // 3. azure.storage.connection-string (formato Quarkus, pode não funcionar no Azure Functions)
                        // 4. Fallback para desenvolvimento local
                        String storageConnectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
                        if (storageConnectionString == null || storageConnectionString.isBlank()) {
                            storageConnectionString = System.getenv("AzureWebJobsStorage");
                        }
                        if (storageConnectionString == null || storageConnectionString.isBlank()) {
                            storageConnectionString = System.getenv("azure.storage.connection-string");
                        }
                        if (storageConnectionString == null || storageConnectionString.isBlank()) {
                            logger.warn("AVISO: AZURE_STORAGE_CONNECTION_STRING não configurada! Usando valor padrão para desenvolvimento local.");
                            storageConnectionString = "UseDevelopmentStorage=true"; // Padrão para Azurite local
                        }
                        
                        // Validar se não está usando o fallback local em produção
                        if (storageConnectionString.equals("UseDevelopmentStorage=true")) {
                            String env = System.getenv("app.environment");
                            if (env != null && (env.equals("production") || env.equals("prod"))) {
                                logger.error("ERRO CRITICO: Usando connection string de desenvolvimento local em ambiente de produção!");
                                throw new RuntimeException("AZURE_STORAGE_CONNECTION_STRING não configurada em ambiente de produção");
                            }
                        }
                        
                        String tableName = System.getenv("azure.table.table-name");
                        if (tableName == null || tableName.isBlank()) {
                            tableName = "feedbacks"; // Valor padrão
                        }
                        
                        logger.info("Configurações obtidas:");
                        logger.info("  - Table Name: {}", tableName);
                        logger.info("  - Connection String configurada: {}", storageConnectionString != null && !storageConnectionString.isBlank());
                        if (storageConnectionString != null && storageConnectionString.length() > 50) {
                            logger.info("  - Connection String (primeiros 50 chars): {}...", storageConnectionString.substring(0, 50));
                        } else {
                            logger.info("  - Connection String: {}", storageConnectionString);
                        }
                        
                        // Criar gateways manualmente usando reflection para configurar campos privados
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
    
    private static FeedbackGateway createFeedbackGateway(String connectionString, String tableName) {
        try {
            logger.info("Criando TableStorageFeedbackGatewayImpl manualmente");
            logger.info("  Connection String: {} (tamanho: {} chars)", 
                connectionString != null && connectionString.length() > 20 
                    ? connectionString.substring(0, 20) + "..." 
                    : connectionString,
                connectionString != null ? connectionString.length() : 0);
            logger.info("  Table Name: {}", tableName);
            
            TableStorageFeedbackGatewayImpl gateway = new TableStorageFeedbackGatewayImpl();
            
            // Configurar campos privados via reflection (seguindo padrão do WeeklyReportFunction)
            java.lang.reflect.Field storageField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("storageConnectionString");
            storageField.setAccessible(true);
            storageField.set(gateway, connectionString);
            
            java.lang.reflect.Field tableField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableName");
            tableField.setAccessible(true);
            tableField.set(gateway, tableName);
            
            // Verificar se os campos foram definidos corretamente
            String storedConnectionString = (String) storageField.get(gateway);
            String storedTableName = (String) tableField.get(gateway);
            logger.info("  Campos configurados - Connection String: {}, Table Name: {}", 
                storedConnectionString != null && storedConnectionString.length() > 20 
                    ? storedConnectionString.substring(0, 20) + "..." 
                    : storedConnectionString,
                storedTableName);
            
            // Inicializar via reflection
            logger.info("  Chamando init() via reflection...");
            java.lang.reflect.Method initMethod = TableStorageFeedbackGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gateway);
            
            logger.info("TableStorageFeedbackGatewayImpl inicializado com sucesso");
            return gateway;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("Erro ao inicializar TableStorageFeedbackGatewayImpl (InvocationTargetException)", cause != null ? cause : e);
            logger.error("  Mensagem: {}", cause != null ? cause.getMessage() : e.getMessage());
            if (cause != null) {
                logger.error("  Stack trace:", cause);
            }
            throw new RuntimeException("Falha ao criar FeedbackGateway: " + (cause != null ? cause.getMessage() : e.getMessage()), cause != null ? cause : e);
        } catch (Exception e) {
            logger.error("Erro ao criar TableStorageFeedbackGatewayImpl", e);
            logger.error("  Tipo da exceção: {}", e.getClass().getName());
            logger.error("  Mensagem: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar FeedbackGateway: " + e.getMessage(), e);
        }
    }
    
    private static EmailNotificationGateway createEmailGateway() {
        try {
            logger.info("Criando EmailNotificationGatewayImpl manualmente");
            
            // Obter variáveis de ambiente do Mailtrap
            String mailtrapToken = System.getenv("MAILTRAP_API_TOKEN");
            String adminEmail = System.getenv("ADMIN_EMAIL");
            String mailtrapInboxIdStr = System.getenv("MAILTRAP_INBOX_ID");
            
            logger.info("  MAILTRAP_API_TOKEN configurado: {}", mailtrapToken != null && !mailtrapToken.isBlank() ? "SIM" : "NAO");
            logger.info("  ADMIN_EMAIL configurado: {}", adminEmail != null && !adminEmail.isBlank() ? adminEmail : "NAO");
            logger.info("  MAILTRAP_INBOX_ID configurado: {}", mailtrapInboxIdStr != null && !mailtrapInboxIdStr.isBlank() ? mailtrapInboxIdStr : "NAO");
            
            // Converter inbox ID de String para Long (construtor espera Long)
            Long mailtrapInboxId = null;
            if (mailtrapInboxIdStr != null && !mailtrapInboxIdStr.isBlank()) {
                try {
                    mailtrapInboxId = Long.parseLong(mailtrapInboxIdStr.trim());
                    logger.info("  MAILTRAP_INBOX_ID convertido para Long: {}", mailtrapInboxId);
                } catch (NumberFormatException e) {
                    logger.error("ERRO: MAILTRAP_INBOX_ID invalido: '{}'. Deve ser um numero.", mailtrapInboxIdStr);
                }
            }
            
            // Criar gateway passando os parâmetros diretamente
            EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(mailtrapToken, adminEmail, mailtrapInboxId);
            
            // Inicializar manualmente chamando o método init() via reflection
            logger.info("  Chamando init() via reflection para inicializar MailtrapClient...");
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gateway);
            logger.info("  Método init() executado com sucesso");
            
            // Verificar se mailtrapClient foi inicializado
            java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
            clientField.setAccessible(true);
            Object mailtrapClient = clientField.get(gateway);
            
            if (mailtrapClient != null) {
                logger.info("EmailNotificationGatewayImpl inicializado com sucesso - MailtrapClient criado");
            } else {
                logger.warn("AVISO: EmailNotificationGatewayImpl inicializado mas MailtrapClient é null");
                logger.warn("  Isso pode acontecer se as configurações do Mailtrap não estiverem completas");
                logger.warn("  Verifique: MAILTRAP_API_TOKEN, MAILTRAP_INBOX_ID, ADMIN_EMAIL");
            }
            
            return gateway;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("Erro ao inicializar EmailNotificationGatewayImpl (InvocationTargetException)", cause != null ? cause : e);
            logger.error("  Mensagem: {}", cause != null ? cause.getMessage() : e.getMessage());
            if (cause != null) {
                logger.error("  Stack trace:", cause);
            }
            throw new RuntimeException("Falha ao criar EmailNotificationGateway: " + (cause != null ? cause.getMessage() : e.getMessage()), cause != null ? cause : e);
        } catch (Exception e) {
            logger.error("Erro ao criar EmailNotificationGatewayImpl", e);
            logger.error("  Tipo da exceção: {}", e.getClass().getName());
            logger.error("  Mensagem: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar EmailNotificationGateway: " + e.getMessage(), e);
        }
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
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            String bodyString = bodyOptional.get();
            
            // Converter JSON para FeedbackRequest
            FeedbackRequest feedbackRequest = objectMapper.readValue(bodyString, FeedbackRequest.class);
            
            // Validar campos obrigatórios
            if (feedbackRequest.description() == null || feedbackRequest.description().trim().isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'descricao' é obrigatório\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            if (feedbackRequest.score() == null) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'nota' é obrigatório\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            if (feedbackRequest.score() < 0 || feedbackRequest.score() > 10) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Campo 'nota' deve estar entre 0 e 10\"}")
                        .header("Content-Type", "application/json")
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
                    .header("Content-Type", "application/json")
                    .build();
                    
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Erro ao processar JSON", e);
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"JSON inválido: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            logger.error("Erro ao processar feedback", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Erro interno: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
