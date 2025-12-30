package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.FeedbackPersistenceException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para BlobReportStorageGatewayImpl")
class BlobReportStorageGatewayImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BlobServiceClient blobServiceClient;

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    private BlobReportStorageGatewayImpl gateway;

    @BeforeEach
    void setUp() throws Exception {
        gateway = new BlobReportStorageGatewayImpl(
            "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net",
            "test-container",
            objectMapper
        );
        
        // Injetar containerClient mockado usando reflection
        Field containerField = BlobReportStorageGatewayImpl.class.getDeclaredField("containerClient");
        containerField.setAccessible(true);
        containerField.set(gateway, containerClient);
    }

    @Test
    @DisplayName("Deve salvar relatório com sucesso")
    void deveSalvarRelatorioComSucesso() {
        String fileName = "relatorio.json";
        String content = "{\"total\":10}";
        
        when(containerClient.getBlobClient(fileName)).thenReturn(blobClient);
        doNothing().when(blobClient).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));
        doNothing().when(blobClient).setHttpHeaders(any(BlobHttpHeaders.class));

        String result = gateway.saveReport(fileName, content);

        assertEquals(fileName, result);
        verify(blobClient, times(1)).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));
        verify(blobClient, times(1)).setHttpHeaders(any(BlobHttpHeaders.class));
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando ocorre erro ao salvar")
    void deveLancarFeedbackPersistenceExceptionQuandoOcorreErroAoSalvar() {
        String fileName = "relatorio.json";
        String content = "{\"total\":10}";
        RuntimeException erro = new RuntimeException("Erro de conexão");
        
        when(containerClient.getBlobClient(fileName)).thenReturn(blobClient);
        doThrow(erro).when(blobClient).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.saveReport(fileName, content)
        );

        assertTrue(exception.getMessage().contains("Falha ao salvar relatório no Blob Storage"));
        assertEquals(erro, exception.getCause());
    }

    @Test
    @DisplayName("Deve retornar URL do relatório")
    void deveRetornarUrlDoRelatorio() {
        String fileName = "relatorio.json";
        String expectedUrl = "https://test.blob.core.windows.net/test-container/relatorio.json";
        
        when(containerClient.getBlobClient(fileName)).thenReturn(blobClient);
        when(blobClient.getBlobUrl()).thenReturn(expectedUrl);

        String url = gateway.getReportUrl(fileName);

        assertEquals(expectedUrl, url);
        verify(containerClient, times(1)).getBlobClient(fileName);
        verify(blobClient, times(1)).getBlobUrl();
    }

    @Test
    @DisplayName("Deve salvar relatório semanal convertendo para JSON")
    void deveSalvarRelatorioSemanalConvertendoParaJson() throws Exception {
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("total_avaliacoes", 10);
        reportData.put("media_avaliacoes", 7.5);
        
        String jsonReport = "{\"total_avaliacoes\":10,\"media_avaliacoes\":7.5}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonReport);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doNothing().when(blobClient).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));
        doNothing().when(blobClient).setHttpHeaders(any(BlobHttpHeaders.class));

        String fileName = gateway.saveWeeklyReport(reportData);

        assertNotNull(fileName);
        assertTrue(fileName.startsWith("relatorios/relatorio-"));
        assertTrue(fileName.endsWith(".json"));
        verify(objectMapper, times(1)).writeValueAsString(reportData);
        verify(blobClient, times(1)).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando ocorre erro ao serializar JSON")
    void deveLancarFeedbackPersistenceExceptionQuandoOcorreErroAoSerializarJson() throws Exception {
        Map<String, Object> reportData = new HashMap<>();
        JsonProcessingException erro = new com.fasterxml.jackson.core.JsonProcessingException("Erro ao serializar") {};
        
        when(objectMapper.writeValueAsString(any())).thenThrow(erro);

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.saveWeeklyReport(reportData)
        );

        assertTrue(exception.getMessage().contains("Falha ao salvar relatório semanal no Blob Storage"));
        assertEquals(erro, exception.getCause());
    }

    @Test
    @DisplayName("Deve usar nome de arquivo baseado na data atual")
    void deveUsarNomeDeArquivoBaseadoNaDataAtual() throws Exception {
        Map<String, Object> reportData = new HashMap<>();
        String jsonReport = "{}";
        
        when(objectMapper.writeValueAsString(any())).thenReturn(jsonReport);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doNothing().when(blobClient).upload(any(ByteArrayInputStream.class), anyLong(), eq(true));
        doNothing().when(blobClient).setHttpHeaders(any(BlobHttpHeaders.class));

        String fileName = gateway.saveWeeklyReport(reportData);

        assertTrue(fileName.matches("relatorios/relatorio-\\d{4}-\\d{2}-\\d{2}\\.json"));
    }

    @Test
    @DisplayName("Deve lançar FeedbackPersistenceException quando containerClient é null")
    void deveLancarFeedbackPersistenceExceptionQuandoContainerClientENull() throws Exception {
        Field containerField = BlobReportStorageGatewayImpl.class.getDeclaredField("containerClient");
        containerField.setAccessible(true);
        containerField.set(gateway, null);

        FeedbackPersistenceException exception = assertThrows(
            FeedbackPersistenceException.class,
            () -> gateway.saveReport("test.json", "{}")
        );

        assertTrue(exception.getMessage().contains("Falha ao salvar relatório no Blob Storage"));
    }

    @Test
    @DisplayName("Deve lançar NullPointerException quando containerClient é null ao obter URL")
    void deveLancarNullPointerExceptionQuandoContainerClientENullAoObterUrl() throws Exception {
        Field containerField = BlobReportStorageGatewayImpl.class.getDeclaredField("containerClient");
        containerField.setAccessible(true);
        containerField.set(gateway, null);

        // O método getReportUrl não valida null, então lança NullPointerException
        assertThrows(
            NullPointerException.class,
            () -> gateway.getReportUrl("test.json")
        );
    }

}
