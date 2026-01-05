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
            logger.info("Inicializando Table Storage Gateway...");
            logger.info("  - Table Name: {}", tableName);
            logger.info("  - Connection String configurada: {}", 
                storageConnectionString != null && !storageConnectionString.isBlank());
            
            if (storageConnectionString == null || storageConnectionString.isBlank()) {
                throw new FeedbackPersistenceException(
                    "Connection string do Table Storage não está configurada. Verifique a propriedade azure.storage.connection-string");
            }
            
            tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(storageConnectionString)
                    .buildClient();
            
            logger.info("TableServiceClient criado com sucesso");
            
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
            
            // Verificar se a tabela existe antes de criar o TableClient
            try {
                tableServiceClient.getTableClient(tableName);
                logger.info("Verificação de existência da tabela '{}': EXISTE", tableName);
            } catch (Exception e) {
                logger.warn("Não foi possível verificar a existência da tabela '{}': {}", tableName, e.getMessage());
            }
            
            tableClient = new TableClientBuilder()
                    .connectionString(storageConnectionString)
                    .tableName(tableName)
                    .buildClient();
            
            logger.info("TableClient criado com sucesso para a tabela '{}'", tableName);
            
            // Testar a conexão tentando listar entidades (mesmo que vazio)
            try {
                logger.info("Testando conexão com a tabela '{}'...", tableName);
                // Apenas verificar se consegue acessar a tabela (não importa se está vazia)
                tableClient.listEntities().iterator().hasNext(); // Força uma tentativa de acesso
                logger.info("Conexão com a tabela '{}' testada com sucesso", tableName);
            } catch (Exception e) {
                logger.error("ERRO ao testar conexão com a tabela '{}': {}", tableName, e.getMessage(), e);
                throw new FeedbackPersistenceException(
                    String.format("Falha ao conectar à tabela '%s': %s", tableName, e.getMessage()), e);
            }
            
            // Verificar se o tableClient foi criado corretamente
            if (tableClient == null) {
                throw new FeedbackPersistenceException(
                    "TableClient não foi criado corretamente. Verifique a connection string e o nome da tabela.");
            }
            
            logger.info("Table Storage Gateway inicializado com sucesso");

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
        
        int maxRetries = 3;
        int retryDelayMs = 500;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 1) {
                    logger.info("Tentativa {}/{} de salvamento...", attempt, maxRetries);
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2; // Exponential backoff
                } else {
                    logger.info("Iniciando salvamento de feedback no Table Storage...");
                }
                
                logger.info("  - Table Client inicializado: {}", tableClient != null);
                logger.info("  - Table Name: {}", tableName);
                
                if (feedback.getId() == null) {
                    feedback.setId(UUID.randomUUID().toString());
                    logger.debug("ID gerado para feedback: {}", feedback.getId());
                }

                if (feedback.getCreatedAt() == null) {
                    feedback.setCreatedAt(LocalDateTime.now());
                    logger.debug("CreatedAt definido para feedback: {}", feedback.getCreatedAt());
                }

                TableEntity entity = TableStorageFeedbackMapper.toTableEntity(feedback);
                logger.info("Entidade criada - PartitionKey: {}, RowKey: {}", 
                    entity.getPartitionKey(), entity.getRowKey());
                logger.debug("Propriedades da entidade: {}", entity.getProperties());
                
                logger.info("Chamando upsertEntity no Table Storage...");
                tableClient.upsertEntity(entity);
                logger.info("upsertEntity executado com sucesso");

                logger.info("✅ Feedback salvo no Table Storage: id={}, partitionKey={}, rowKey={}", 
                    feedback.getId(), entity.getPartitionKey(), entity.getRowKey());
                
                return; // Sucesso - sair do loop

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread interrompida durante salvamento", e);
                throw new FeedbackPersistenceException("Salvamento interrompido", e);
            } catch (com.azure.data.tables.models.TableTransactionFailedException e) {
                logger.error("Erro de transação ao salvar feedback (tentativa {}/{}): {}", 
                    attempt, maxRetries, e.getMessage(), e);
                if (attempt == maxRetries) {
                    throw new FeedbackPersistenceException("Falha ao salvar feedback no Table Storage (erro de transação)", e);
                }
                // Continuar para próxima tentativa
            } catch (com.azure.core.exception.HttpResponseException e) {
                int statusCode = e.getResponse() != null ? e.getResponse().getStatusCode() : 0;
                logger.error("Erro HTTP ao salvar feedback (tentativa {}/{}). Status: {}, Mensagem: {}", 
                    attempt, maxRetries, statusCode, e.getMessage(), e);
                
                // Retry apenas para erros temporários (5xx)
                if (statusCode >= 500 && attempt < maxRetries) {
                    logger.info("Erro temporário detectado, tentando novamente...");
                    // Continuar para próxima tentativa
                } else {
                    throw new FeedbackPersistenceException(
                        String.format("Falha ao salvar feedback no Table Storage (erro HTTP %d): %s", 
                            statusCode, e.getMessage()), e);
                }
            } catch (Exception e) {
                logger.error("Erro ao salvar feedback (tentativa {}/{}). Tipo: {}, Mensagem: {}", 
                    attempt, maxRetries, e.getClass().getName(), e.getMessage(), e);
                if (attempt == maxRetries) {
                    logger.error("Stack trace completo:", e);
                    throw new FeedbackPersistenceException("Falha ao salvar feedback no Table Storage", e);
                }
                // Continuar para próxima tentativa
            }
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
            logger.error("ERRO CRÍTICO: TableClient é null. Verifique se o método init() foi chamado corretamente.");
            logger.error("  - Table Name esperado: {}", tableName);
            logger.error("  - Connection String configurada: {}", 
                storageConnectionString != null && !storageConnectionString.isBlank());
            throw new FeedbackPersistenceException(
                "Table Storage client não foi inicializado. Verifique a conexão e se o método init() foi chamado.");
        }
        logger.debug("TableClient validado com sucesso");
    }
}

