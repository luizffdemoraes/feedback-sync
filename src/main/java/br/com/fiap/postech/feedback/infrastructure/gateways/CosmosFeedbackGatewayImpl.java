package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.infrastructure.mappers.CosmosFeedbackMapper;
import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementação do gateway de feedback usando Azure Cosmos DB.
 * 
 * Responsabilidade: Persistência e recuperação de feedbacks do Cosmos DB.
 */
@ApplicationScoped
public class CosmosFeedbackGatewayImpl implements FeedbackGateway {

    private static final Logger logger = LoggerFactory.getLogger(CosmosFeedbackGatewayImpl.class);

    @ConfigProperty(name = "azure.cosmos.endpoint", defaultValue = "https://localhost:8081")
    String cosmosEndpoint;

    @ConfigProperty(name = "azure.cosmos.key", defaultValue = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==")
    String cosmosKey;

    @ConfigProperty(name = "azure.cosmos.database", defaultValue = "feedback-db")
    String databaseName;

    @ConfigProperty(name = "azure.cosmos.container", defaultValue = "feedbacks")
    String containerName;

    @ConfigProperty(name = "azure.cosmos.disable-ssl-verification", defaultValue = "false")
    boolean disableSslVerification;

    private CosmosClient cosmosClient;
    private CosmosContainer container;

    @PostConstruct
    public void init() {
        try {
            CosmosClientBuilder builder = new CosmosClientBuilder()
                    .endpoint(cosmosEndpoint)
                    .key(cosmosKey);
            
            // Desabilitar verificação SSL para emulador local
            if (disableSslVerification) {
                // Para emulador local, configurar sistema para confiar em certificados auto-assinados
                // Isso é apenas para desenvolvimento local - NUNCA usar em produção
                try {
                    // Configurar JVM para confiar em todos os certificados (apenas para desenvolvimento)
                    System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
                    System.setProperty("com.azure.cosmos.directHttps.trustAllCertificates", "true");
                    // Tentar configurar TrustManager para aceitar todos os certificados
                    javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
                    logger.warn("SSL verification desabilitada para Cosmos DB emulador local - APENAS DESENVOLVIMENTO");
                } catch (Exception e) {
                    logger.warn("Não foi possível desabilitar SSL verification: {}", e.getMessage());
                }
            }
            
            this.cosmosClient = builder.buildClient();

            cosmosClient.createDatabaseIfNotExists(databaseName);
            CosmosDatabase database = cosmosClient.getDatabase(databaseName);

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties(containerName, "/id");

            ThroughputProperties throughputProperties =
                    ThroughputProperties.createManualThroughput(400);

            database.createContainerIfNotExists(containerProperties, throughputProperties);

            this.container = database.getContainer(containerName);

            logger.info("Cosmos DB conectado: {}/{}", databaseName, containerName);

        } catch (CosmosException e) {
            logger.error("Erro do Cosmos DB ao conectar: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException(
                String.format("Falha ao conectar ao Cosmos DB: %s", e.getMessage()), e);
        } catch (Exception e) {
            logger.error("Erro inesperado ao conectar ao Cosmos DB: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException(
                String.format("Falha ao conectar ao Cosmos DB: %s", e.getMessage()), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            try {
                cosmosClient.close();
                logger.info("Cosmos DB desconectado");
            } catch (Exception e) {
                logger.warn("Erro ao fechar conexão do Cosmos DB: {}", e.getMessage());
            }
        }
    }

    @Override
    public void save(Feedback feedback) {
        validateContainer();
        
        try {
            if (feedback.getId() == null) {
                feedback.setId(UUID.randomUUID().toString());
            }

            if (feedback.getCreatedAt() == null) {
                feedback.setCreatedAt(LocalDateTime.now());
            }

            Map<String, Object> document = CosmosFeedbackMapper.toDocument(feedback);
            container.upsertItem(document);

            logger.info("Feedback salvo no Cosmos DB: id={}", feedback.getId());

        } catch (Exception e) {
            throw new FeedbackPersistenceException("Falha ao salvar feedback no Cosmos DB", e);
        }
    }

    @Override
    public List<Feedback> findByPeriod(Instant from, Instant to) {
        validateContainer();
        
        try {
            // Converte Instant para LocalDateTime para comparar com o formato salvo
            LocalDateTime fromDateTime = LocalDateTime.ofInstant(from, java.time.ZoneId.systemDefault());
            LocalDateTime toDateTime = LocalDateTime.ofInstant(to, java.time.ZoneId.systemDefault());
            
            String query = "SELECT * FROM c " +
                    "WHERE c.createdAt >= @from AND c.createdAt <= @to " +
                    "ORDER BY c.createdAt DESC";

            SqlQuerySpec querySpec = new SqlQuerySpec(query);
            SqlParameter[] parameters = {
                    new SqlParameter("@from", fromDateTime.toString()),
                    new SqlParameter("@to", toDateTime.toString())
            };
            querySpec.setParameters(Arrays.asList(parameters));

            @SuppressWarnings("unchecked")
            CosmosPagedIterable<Map<String, Object>> response =
                    (CosmosPagedIterable<Map<String, Object>>) (CosmosPagedIterable<?>) 
                    container.queryItems(querySpec, new CosmosQueryRequestOptions(), Map.class);

            return response.stream()
                    .map(CosmosFeedbackMapper::toEntity)
                    .toList();

        } catch (Exception e) {
            throw new FeedbackPersistenceException("Falha ao buscar feedbacks do período", e);
        }
    }

    /**
     * Valida se o container está inicializado.
     * 
     * @throws FeedbackPersistenceException se o container não estiver inicializado
     */
    private void validateContainer() {
        if (container == null) {
            throw new FeedbackPersistenceException(
                "Cosmos DB container não foi inicializado. Verifique a conexão.");
        }
    }

}

