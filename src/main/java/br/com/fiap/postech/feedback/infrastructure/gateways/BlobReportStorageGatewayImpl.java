package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.gateways.ReportStorageGateway;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class BlobReportStorageGatewayImpl implements ReportStorageGateway {

    @ConfigProperty(name = "azure.storage.connection-string")
    String storageConnectionString;

    @ConfigProperty(name = "azure.storage.container")
    String containerName;

    @Inject
    ObjectMapper objectMapper;

    private BlobContainerClient containerClient;

    @PostConstruct
    void init() {
        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();

        containerClient = serviceClient.getBlobContainerClient(containerName);

        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    @Override
    public String saveReport(String fileName, String content) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(fileName);
            byte[] data = content.getBytes(StandardCharsets.UTF_8);

            blobClient.upload(new ByteArrayInputStream(data), data.length, true);
            blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType("application/json"));

            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar relatório", e);
        }
    }

    @Override
    public String getReportUrl(String fileName) {
        BlobClient blobClient = containerClient.getBlobClient(fileName);
        return blobClient.getBlobUrl();
    }

    public String saveWeeklyReport(Object reportData) {
        try {
            String jsonReport = objectMapper.writeValueAsString(reportData);
            String fileName = "relatorios/relatorio-" +
                    LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".json";

            return saveReport(fileName, jsonReport);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar relatório semanal", e);
        }
    }
}
