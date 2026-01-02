package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.infrastructure.mappers.TableStorageFeedbackMapper;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementação do gateway de feedback usando Azure Table Storage.
 * 
 * Responsabilidade: Persistência e recuperação de feedbacks do Table Storage.
 * 
 * Vantagens sobre Cosmos DB:
 * - Sem problemas de SSL no emulador local
 * - Implementação mais simples
 * - Custo menor
 * - Azurite já configurado no projeto
 */
@Startup
@ApplicationScoped
public class TableStorageFeedbackGatewayImpl implements FeedbackGateway {

    private static final Logger logger = LoggerFactory.getLogger(TableStorageFeedbackGatewayImpl.class);

    @ConfigProperty(name = "azure.storage.connection-string")
    String storageConnectionString;

    @ConfigProperty(name = "azure.table.table-name", defaultValue = "feedbacks")
    String tableName;

    private TableClient tableClient;
    private TableServiceClient tableServiceClient;

    @PostConstruct
    public void init() {
        try {
            tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(storageConnectionString)
                    .buildClient();
            
            try {
                tableServiceClient.createTableIfNotExists(tableName);
                logger.info("Tabela '{}' criada ou já existe no Table Storage", tableName);
            } catch (TableServiceException e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && (errorMessage.contains("TableAlreadyExists") || errorMessage.contains("409"))) {
                    logger.debug("Tabela '{}' já existe no Table Storage (isso é esperado)", tableName);
                } else {
                    logger.error("Erro ao criar tabela '{}' no Table Storage: {}", tableName, e.getMessage());
                    throw e;
                }
            }
            
            tableClient = new TableClientBuilder()
                    .connectionString(storageConnectionString)
                    .tableName(tableName)
                    .buildClient();

        } catch (TableServiceException e) {
            logger.error("Erro ao conectar ao Table Storage (TableServiceException). Mensagem: {}", 
                e.getMessage(), e);
            throw new FeedbackPersistenceException(
                String.format("Falha ao conectar ao Table Storage: %s", e.getMessage()), e);
        } catch (Exception e) {
            logger.error("Erro ao conectar ao Table Storage: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException(
                String.format("Falha ao conectar ao Table Storage: %s", e.getMessage()), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Table Storage desconectado");
    }

    @Override
    public void save(Feedback feedback) {
        validateTableClient();
        
        try {
            if (feedback.getId() == null) {
                feedback.setId(UUID.randomUUID().toString());
            }

            if (feedback.getCreatedAt() == null) {
                feedback.setCreatedAt(LocalDateTime.now());
            }

            TableEntity entity = TableStorageFeedbackMapper.toTableEntity(feedback);
            logger.debug("Salvando entidade no Table Storage: {}", entity.getRowKey());
            
            tableClient.upsertEntity(entity);

            logger.info("Feedback salvo no Table Storage: id={}", feedback.getId());

        } catch (Exception e) {
            logger.error("Erro ao salvar feedback no Table Storage: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException("Falha ao salvar feedback no Table Storage", e);
        }
    }

    @Override
    public List<Feedback> findByPeriod(Instant from, Instant to) {
        validateTableClient();
        
        try {
            LocalDateTime fromDateTime = LocalDateTime.ofInstant(from, ZoneId.systemDefault());
            LocalDateTime toDateTime = LocalDateTime.ofInstant(to, ZoneId.systemDefault());
            
            List<Feedback> feedbacks = new ArrayList<>();
            
            logger.debug("Buscando feedbacks no período: {} até {}", fromDateTime, toDateTime);
            
            for (TableEntity entity : tableClient.listEntities()) {
                String createdAtStr = (String) entity.getProperty("createdAt");
                if (createdAtStr != null) {
                    LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
                    
                    if (!createdAt.isBefore(fromDateTime) && !createdAt.isAfter(toDateTime)) {
                        Feedback feedback = TableStorageFeedbackMapper.toEntity(entity);
                        feedbacks.add(feedback);
                    }
                }
            }
            
            feedbacks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            
            logger.debug("Encontrados {} feedbacks no período", feedbacks.size());
            return feedbacks;

        } catch (Exception e) {
            logger.error("Erro ao buscar feedbacks do período: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException("Falha ao buscar feedbacks do período", e);
        }
    }

    private void validateTableClient() {
        if (tableClient == null) {
            throw new FeedbackPersistenceException(
                "Table Storage client não foi inicializado. Verifique a conexão.");
        }
    }
}

