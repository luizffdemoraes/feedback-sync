package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para TableStorageFeedbackGatewayImpl")
class TableStorageFeedbackGatewayImplTest {

    @Mock
    private TableClient tableClient;

    private TableStorageFeedbackGatewayImpl gateway;

    @BeforeEach
    void setUp() throws Exception {
        gateway = new TableStorageFeedbackGatewayImpl();
        
        // Injetar tableClient mockado usando reflection
        Field tableClientField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableClient");
        tableClientField.setAccessible(true);
        tableClientField.set(gateway, tableClient);
    }

    @Test
    @DisplayName("Deve salvar feedback com sucesso")
    void deveSalvarFeedbackComSucesso() {
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");
        feedback.setId("feedback-id-123");
        feedback.setCreatedAt(LocalDateTime.now());
        
        doNothing().when(tableClient).upsertEntity(any(TableEntity.class));

        assertDoesNotThrow(() -> gateway.save(feedback));

        verify(tableClient, times(1)).upsertEntity(any(TableEntity.class));
    }

    @Test
    @DisplayName("Deve gerar ID automaticamente quando feedback não tem ID")
    void deveGerarIdAutomaticamenteQuandoFeedbackNaoTemId() {
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");
        String idAntes = feedback.getId();
        feedback.setId(null); // Remover ID gerado no construtor
        
        doNothing().when(tableClient).upsertEntity(any(TableEntity.class));

        gateway.save(feedback);

        assertNotNull(feedback.getId());
        assertNotEquals(idAntes, feedback.getId()); // Verificar que foi gerado um novo ID
        verify(tableClient, times(1)).upsertEntity(any(TableEntity.class));
    }

    @Test
    @DisplayName("Deve definir createdAt automaticamente quando feedback não tem createdAt")
    void deveDefinirCreatedAtAutomaticamenteQuandoFeedbackNaoTemCreatedAt() {
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");
        feedback.setCreatedAt(null);
        
        doNothing().when(tableClient).upsertEntity(any(TableEntity.class));

        gateway.save(feedback);

        assertNotNull(feedback.getCreatedAt());
        verify(tableClient, times(1)).upsertEntity(any(TableEntity.class));
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando ocorre erro ao salvar")
    void deveLancarFeedbackPersistenceExceptionQuandoOcorreErroAoSalvar() {
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");
        RuntimeException erro = new RuntimeException("Erro de conexão");
        
        doThrow(erro).when(tableClient).upsertEntity(any(TableEntity.class));

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.save(feedback)
        );

        assertTrue(exception.getMessage().contains("Falha ao salvar feedback no Table Storage"));
        assertEquals(erro, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando tableClient não está inicializado")
    void deveLancarFeedbackPersistenceExceptionQuandoTableClientNaoEstaInicializado() throws Exception {
        // Remover tableClient
        Field tableClientField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableClient");
        tableClientField.setAccessible(true);
        tableClientField.set(gateway, null);
        
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.save(feedback)
        );

        assertTrue(exception.getMessage().contains("Table Storage client não foi inicializado"));
    }

    @Test
    @DisplayName("Deve buscar feedbacks por período com sucesso")
    void deveBuscarFeedbacksPorPeriodoComSucesso() {
        Instant inicio = Instant.now().minusSeconds(86400); // 1 dia atrás
        Instant fim = Instant.now();
        
        // Criar entidades mockadas
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", LocalDateTime.now().minusHours(12));
        TableEntity entity2 = createMockTableEntity("id2", "Aula 2", 5, "LOW", LocalDateTime.now().minusHours(6));
        TableEntity entity3 = createMockTableEntity("id3", "Aula 3", 9, "HIGH", LocalDateTime.now().plusHours(1)); // Fora do período
        
        entities.add(entity1);
        entities.add(entity2);
        entities.add(entity3);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertNotNull(feedbacks);
        assertEquals(2, feedbacks.size()); // Apenas 2 dentro do período
        verify(tableClient, times(1)).listEntities();
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há feedbacks no período")
    void deveRetornarListaVaziaQuandoNaoHaFeedbacksNoPeriodo() {
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertNotNull(feedbacks);
        assertTrue(feedbacks.isEmpty());
    }

    @Test
    @DisplayName("Deve ordenar feedbacks por data (mais recente primeiro)")
    void deveOrdenarFeedbacksPorDataMaisRecentePrimeiro() {
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", LocalDateTime.now().minusHours(12));
        TableEntity entity2 = createMockTableEntity("id2", "Aula 2", 5, "LOW", LocalDateTime.now().minusHours(6));
        
        entities.add(entity1);
        entities.add(entity2);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(2, feedbacks.size());
        // Verificar que está ordenado (mais recente primeiro)
        assertTrue(feedbacks.get(0).getCreatedAt().isAfter(feedbacks.get(1).getCreatedAt()) ||
                   feedbacks.get(0).getCreatedAt().isEqual(feedbacks.get(1).getCreatedAt()));
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando ocorre erro ao buscar")
    void deveLancarFeedbackPersistenceExceptionQuandoOcorreErroAoBuscar() {
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();
        RuntimeException erro = new RuntimeException("Erro de conexão");
        
        when(tableClient.listEntities()).thenThrow(erro);

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.findByPeriod(inicio, fim)
        );

        assertTrue(exception.getMessage().contains("Falha ao buscar feedbacks do período"));
        assertEquals(erro, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando tableClient não está inicializado ao buscar")
    void deveLancarFeedbackPersistenceExceptionQuandoTableClientNaoEstaInicializadoAoBuscar() throws Exception {
        // Remover tableClient
        Field tableClientField = TableStorageFeedbackGatewayImpl.class.getDeclaredField("tableClient");
        tableClientField.setAccessible(true);
        tableClientField.set(gateway, null);
        
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.findByPeriod(inicio, fim)
        );

        assertTrue(exception.getMessage().contains("Table Storage client não foi inicializado"));
    }

    @Test
    @DisplayName("Deve ignorar entidades sem createdAt ao buscar por período")
    void deveIgnorarEntidadesSemCreatedAtAoBuscarPorPeriodo() {
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", LocalDateTime.now().minusHours(12));
        TableEntity entity2 = createMockTableEntity("id2", "Aula 2", 5, "LOW", null); // Sem createdAt
        
        entities.add(entity1);
        entities.add(entity2);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(1, feedbacks.size()); // Apenas 1 com createdAt válido
    }

    @Test
    @DisplayName("Deve fazer cleanup sem erros")
    void deveFazerCleanupSemErros() {
        assertDoesNotThrow(() -> gateway.cleanup());
    }

    @Test
    @DisplayName("Deve buscar feedbacks com createdAt exatamente no início do período")
    void deveBuscarFeedbacksComCreatedAtExatamenteNoInicioDoPeriodo() {
        Instant inicio = Instant.now().minusSeconds(3600); // 1 hora atrás
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", 
            LocalDateTime.ofInstant(inicio, java.time.ZoneId.systemDefault()));
        
        entities.add(entity1);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(1, feedbacks.size());
    }

    @Test
    @DisplayName("Deve buscar feedbacks com createdAt exatamente no fim do período")
    void deveBuscarFeedbacksComCreatedAtExatamenteNoFimDoPeriodo() {
        Instant inicio = Instant.now().minusSeconds(3600);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", 
            LocalDateTime.ofInstant(fim, java.time.ZoneId.systemDefault()));
        
        entities.add(entity1);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(1, feedbacks.size());
    }

    @Test
    @DisplayName("Deve buscar feedbacks antes do período")
    void deveBuscarFeedbacksAntesDoPeriodo() {
        Instant inicio = Instant.now().minusSeconds(3600);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", 
            LocalDateTime.ofInstant(inicio.minusSeconds(3600), java.time.ZoneId.systemDefault()));
        
        entities.add(entity1);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(0, feedbacks.size()); // Fora do período
    }

    @Test
    @DisplayName("Deve buscar feedbacks depois do período")
    void deveBuscarFeedbacksDepoisDoPeriodo() {
        Instant inicio = Instant.now().minusSeconds(3600);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = createMockTableEntity("id1", "Aula 1", 7, "MEDIUM", 
            LocalDateTime.ofInstant(fim.plusSeconds(3600), java.time.ZoneId.systemDefault()));
        
        entities.add(entity1);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        List<Feedback> feedbacks = gateway.findByPeriod(inicio, fim);

        assertEquals(0, feedbacks.size()); // Fora do período
    }

    @Test
    @DisplayName("Deve tratar erro ao parsear createdAt inválido")
    void deveTratarErroAoParsearCreatedAtInvalido() {
        Instant inicio = Instant.now().minusSeconds(86400);
        Instant fim = Instant.now();
        
        List<TableEntity> entities = new ArrayList<>();
        TableEntity entity1 = new TableEntity("feedback", "id1");
        entity1.addProperty("id", "id1");
        entity1.addProperty("description", "Aula 1");
        entity1.addProperty("score", 7);
        entity1.addProperty("urgency", "MEDIUM");
        entity1.addProperty("createdAt", "data-invalida"); // Data inválida
        
        entities.add(entity1);
        
        @SuppressWarnings("unchecked")
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        when(pagedIterable.iterator()).thenReturn(entities.iterator());

        // Deve lançar exceção ao tentar parsear data inválida
        assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.findByPeriod(inicio, fim)
        );
    }

    @Test
    @DisplayName("Deve salvar feedback quando ID e createdAt já estão definidos")
    void deveSalvarFeedbackQuandoIdECreatedAtJaEstaoDefinidos() {
        Feedback feedback = new Feedback("Aula boa", 7, "MEDIUM");
        String idEsperado = "id-predefinido";
        LocalDateTime createdAtEsperado = LocalDateTime.now().minusDays(1);
        
        feedback.setId(idEsperado);
        feedback.setCreatedAt(createdAtEsperado);
        
        doNothing().when(tableClient).upsertEntity(any(TableEntity.class));

        gateway.save(feedback);

        assertEquals(idEsperado, feedback.getId());
        assertEquals(createdAtEsperado, feedback.getCreatedAt());
        verify(tableClient, times(1)).upsertEntity(any(TableEntity.class));
    }

    private TableEntity createMockTableEntity(String id, String description, int score, String urgency, LocalDateTime createdAt) {
        TableEntity entity = new TableEntity("feedback", id);
        entity.addProperty("id", id);
        entity.addProperty("description", description);
        entity.addProperty("score", score);
        entity.addProperty("urgency", urgency);
        if (createdAt != null) {
            entity.addProperty("createdAt", createdAt.toString());
        }
        return entity;
    }
}
