package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCaseImpl;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.QueueNotificationGateway;
import br.com.fiap.postech.feedback.infrastructure.gateways.QueueNotificationGatewayImpl;
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
 * o mesmo padrão das outras funções Azure (NotifyAdminFunction, WeeklyReportFunction).
 */
public class FeedbackHttpFunction {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackHttpFunction.class);
    private static CreateFeedbackUseCase createFeedbackUseCase;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static {
        // Criar dependências manualmente (sem Quarkus CDI)
        try {
            logger.info("Inicializando CreateFeedbackUseCase manualmente...");
            
            // Obter configurações de variáveis de ambiente
            String storageConnectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
            if (storageConnectionString == null || storageConnectionString.isBlank()) {
                storageConnectionString = System.getenv("azure.storage.connection-string");
            }
            if (storageConnectionString == null || storageConnectionString.isBlank()) {
                storageConnectionString = "UseDevelopmentStorage=true"; // Padrão para Azurite local
            }
            
            String tableName = System.getenv("azure.table.table-name");
            if (tableName == null || tableName.isBlank()) {
                tableName = "feedbacks";
            }
            
            logger.info("Configurações - Table: {}", tableName);
            
            // Criar gateways manualmente usando reflection para configurar campos privados
            FeedbackGateway feedbackGateway = createFeedbackGateway(storageConnectionString, tableName);
            QueueNotificationGateway queueGateway = createQueueGateway(storageConnectionString);
            
            // Criar use case
            createFeedbackUseCase = new CreateFeedbackUseCaseImpl(feedbackGateway, queueGateway);
            
            logger.info("✓ CreateFeedbackUseCase inicializado com sucesso");
        } catch (Exception e) {
            logger.error("Erro ao inicializar CreateFeedbackUseCase", e);
        }
    }
    
    private static FeedbackGateway createFeedbackGateway(String connectionString, String tableName) {
        try {
            logger.info("Criando TableStorageFeedbackGatewayImpl manualmente");
            TableStorageFeedbackGatewayImpl gateway = new TableStorageFeedbackGatewayImpl();
            
            // Configurar campos privados via reflection (seguindo padrão do WeeklyReportFunction)
            java.lang.reflect.Field storageField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("storageConnectionString");
            storageField.setAccessible(true);
            storageField.set(gateway, connectionString);
            
            java.lang.reflect.Field tableField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableName");
            tableField.setAccessible(true);
            tableField.set(gateway, tableName);
            
            // Inicializar via reflection
            java.lang.reflect.Method initMethod = TableStorageFeedbackGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gateway);
            
            logger.info("✓ TableStorageFeedbackGatewayImpl inicializado");
            return gateway;
        } catch (Exception e) {
            logger.error("Erro ao criar TableStorageFeedbackGatewayImpl", e);
            throw new RuntimeException("Falha ao criar FeedbackGateway", e);
        }
    }
    
    private static QueueNotificationGateway createQueueGateway(String connectionString) {
        try {
            logger.info("Criando QueueNotificationGatewayImpl manualmente");
            // QueueNotificationGatewayImpl tem construtor com @ConfigProperty, então precisamos usar reflection
            // Criar instância vazia primeiro
            QueueNotificationGatewayImpl gateway = new QueueNotificationGatewayImpl(connectionString, objectMapper);
            
            // Chamar init() via reflection
            java.lang.reflect.Method initMethod = QueueNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gateway);
            
            logger.info("✓ QueueNotificationGatewayImpl inicializado");
            return gateway;
        } catch (Exception e) {
            logger.error("Erro ao criar QueueNotificationGatewayImpl", e);
            throw new RuntimeException("Falha ao criar QueueNotificationGateway", e);
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
            
            // Processar feedback
            if (createFeedbackUseCase == null) {
                logger.error("CreateFeedbackUseCase não está inicializado");
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\": \"Serviço não disponível - CreateFeedbackUseCase não inicializado\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            logger.info("Processando feedback: descricao={}, nota={}, urgencia={}", 
                feedbackRequest.description(), feedbackRequest.score(), feedbackRequest.urgency());
            
            FeedbackResponse response = createFeedbackUseCase.execute(feedbackRequest);
            
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
