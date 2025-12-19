package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


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

    private CosmosContainer container;

    @PostConstruct
    public void init() {
        try {
            CosmosClient client = new CosmosClientBuilder()
                    .endpoint(cosmosEndpoint)
                    .key(cosmosKey)
                    .buildClient();

            CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists(databaseName);
            CosmosDatabase database = client.getDatabase(databaseName);

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties(containerName, "/id");

            ThroughputProperties throughputProperties =
                    ThroughputProperties.createManualThroughput(400);

            CosmosContainerResponse containerResponse = database
                    .createContainerIfNotExists(containerProperties, throughputProperties);

            this.container = database.getContainer(containerName);

            logger.info("Cosmos DB conectado: {}/{}", databaseName, containerName);

        } catch (CosmosException e) {
            logger.error("Erro ao conectar ao Cosmos DB: {}", e.getMessage(), e);
        }
    }

    @Override
    public void save(Feedback feedback) {
        try {
            if (feedback.getId() == null) {
                feedback.setId(UUID.randomUUID().toString());
            }

            if (feedback.getCreatedAt() == null) {
                feedback.setCreatedAt(LocalDateTime.now());
            }

            CosmosDocument doc = toDocument(feedback);
            container.upsertItem(doc);

            logger.info("Feedback salvo no Cosmos DB: id={}", feedback.getId());

        } catch (Exception e) {
            logger.error("Erro ao salvar feedback: {}", e.getMessage(), e);
            throw new FeedbackPersistenceException("Falha ao salvar feedback no Cosmos DB", e);
        }
    }

    @Override
    public List<Feedback> findByPeriod(Instant from, Instant to) {
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

            CosmosPagedIterable<CosmosDocument> response =
                    container.queryItems(querySpec, new CosmosQueryRequestOptions(), CosmosDocument.class);

            return response.stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Erro ao buscar feedbacks: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getWeeklyReportData(Instant startOfWeek, Instant endOfWeek) {
        List<Feedback> feedbacks = findByPeriod(startOfWeek, endOfWeek);

        double average = feedbacks.stream()
                .mapToInt(f -> f.getScore().getValue())
                .average()
                .orElse(0.0);

        Map<String, Long> dailyCount = feedbacks.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getCreatedAt()
                                .toLocalDate()
                                .toString(),
                        Collectors.counting()
                ));

        Map<String, Long> urgencyCount = feedbacks.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getUrgency().getValue(),
                        Collectors.counting()
                ));

        Map<String, Object> report = new HashMap<>();
        report.put("periodo_inicio", startOfWeek.toString());
        report.put("periodo_fim", endOfWeek.toString());
        report.put("total_avaliacoes", feedbacks.size());
        report.put("media_avaliacoes", Math.round(average * 100.0) / 100.0);
        report.put("avaliacoes_por_dia", dailyCount);
        report.put("avaliacoes_por_urgencia", urgencyCount);
        report.put("feedbacks", feedbacks.stream()
                .map(this::toMap)
                .collect(Collectors.toList()));

        return report;
    }

    private CosmosDocument toDocument(Feedback feedback) {
        CosmosDocument doc = new CosmosDocument();
        doc.id = feedback.getId();
        doc.description = feedback.getDescription();
        doc.score = feedback.getScore().getValue();
        doc.urgency = feedback.getUrgency().getValue();
        doc.createdAt = feedback.getCreatedAt().toString();
        return doc;
    }

    private Feedback toEntity(CosmosDocument doc) {
        // Usa método de reconstrução para manter imutabilidade
        return Feedback.reconstruct(
            doc.id,
            doc.description,
            doc.score,
            doc.urgency != null ? doc.urgency : "LOW",
            LocalDateTime.parse(doc.createdAt)
        );
    }

    private Map<String, Object> toMap(Feedback feedback) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", feedback.getId());
        map.put("description", feedback.getDescription());
        map.put("score", feedback.getScore().getValue());
        map.put("urgency", feedback.getUrgency().getValue());
        map.put("createdAt", feedback.getCreatedAt().toString());
        return map;
    }

    private static class CosmosDocument {
        public String id;
        public String description;
        public Integer score;
        public String urgency;
        public String createdAt;
    }
}

