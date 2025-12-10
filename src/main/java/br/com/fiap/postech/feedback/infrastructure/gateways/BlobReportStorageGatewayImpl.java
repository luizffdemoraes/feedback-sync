package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.gateways.ReportStorageGateway;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlobReportStorageGatewayImpl implements ReportStorageGateway {

    @Override
    public String saveReport(String fileName, String content) {
        // put blob and return URL
        return "https://...";
    }
}
