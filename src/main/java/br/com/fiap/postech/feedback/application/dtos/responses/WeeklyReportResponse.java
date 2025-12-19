package br.com.fiap.postech.feedback.application.dtos.responses;

import java.time.Instant;
import java.util.Map;

public class WeeklyReportResponse {
    private Instant periodoInicio;
    private Instant periodoFim;
    private Integer totalAvaliacoes;
    private Double mediaAvaliacoes;
    private Map<String, Long> avaliacoesPorDia;
    private Map<String, Long> avaliacoesPorUrgencia;
    private String reportUrl;

    public WeeklyReportResponse() {
    }

    public WeeklyReportResponse(Instant periodoInicio, Instant periodoFim, Integer totalAvaliacoes,
                               Double mediaAvaliacoes, Map<String, Long> avaliacoesPorDia,
                               Map<String, Long> avaliacoesPorUrgencia, String reportUrl) {
        this.periodoInicio = periodoInicio;
        this.periodoFim = periodoFim;
        this.totalAvaliacoes = totalAvaliacoes;
        this.mediaAvaliacoes = mediaAvaliacoes;
        this.avaliacoesPorDia = avaliacoesPorDia;
        this.avaliacoesPorUrgencia = avaliacoesPorUrgencia;
        this.reportUrl = reportUrl;
    }

    public Instant getPeriodoInicio() {
        return periodoInicio;
    }

    public void setPeriodoInicio(Instant periodoInicio) {
        this.periodoInicio = periodoInicio;
    }

    public Instant getPeriodoFim() {
        return periodoFim;
    }

    public void setPeriodoFim(Instant periodoFim) {
        this.periodoFim = periodoFim;
    }

    public Integer getTotalAvaliacoes() {
        return totalAvaliacoes;
    }

    public void setTotalAvaliacoes(Integer totalAvaliacoes) {
        this.totalAvaliacoes = totalAvaliacoes;
    }

    public Double getMediaAvaliacoes() {
        return mediaAvaliacoes;
    }

    public void setMediaAvaliacoes(Double mediaAvaliacoes) {
        this.mediaAvaliacoes = mediaAvaliacoes;
    }

    public Map<String, Long> getAvaliacoesPorDia() {
        return avaliacoesPorDia;
    }

    public void setAvaliacoesPorDia(Map<String, Long> avaliacoesPorDia) {
        this.avaliacoesPorDia = avaliacoesPorDia;
    }

    public Map<String, Long> getAvaliacoesPorUrgencia() {
        return avaliacoesPorUrgencia;
    }

    public void setAvaliacoesPorUrgencia(Map<String, Long> avaliacoesPorUrgencia) {
        this.avaliacoesPorUrgencia = avaliacoesPorUrgencia;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }
}
