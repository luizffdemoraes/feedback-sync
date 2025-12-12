package br.com.fiap.postech.feedback.domain.gateways;

public interface ReportStorageGateway {
    String saveReport(String fileName, String content);
    String getReportUrl(String fileName);
    String saveWeeklyReport(Object reportData);
}
