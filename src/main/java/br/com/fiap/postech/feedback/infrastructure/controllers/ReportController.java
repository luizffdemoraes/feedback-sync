package br.com.fiap.postech.feedback.infrastructure.controllers;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;
import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller REST para geração de relatórios semanais.
 * 
 * Este endpoint permite forçar a geração manual do relatório semanal,
 * independentemente do agendamento automático via Timer Trigger.
 * 
 * Uso:
 * - POST /relatorio - Gera relatório semanal imediatamente
 * - Útil para testes e validação do fluxo de relatórios
 * - O relatório gerado é salvo no Blob Storage (container weekly-reports)
 * 
 * Segue Clean Architecture:
 * - Camada de infraestrutura (controllers)
 * - Delega processamento para use cases (camada de aplicação)
 * - Não contém lógica de negócio
 * - Não trata exceções (delegado para GlobalExceptionMapper)
 * 
 * Responsabilidade única: Receber requisições HTTP REST e delegar ao use case.
 */
@ApplicationScoped
@Path("/relatorio")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    @Inject
    public ReportController(GenerateWeeklyReportUseCase generateWeeklyReportUseCase) {
        this.generateWeeklyReportUseCase = generateWeeklyReportUseCase;
    }

    /**
     * Gera relatório semanal consolidado de feedbacks manualmente.
     * 
     * Este método força a execução do relatório semanal, permitindo
     * gerar o relatório sob demanda, independentemente do agendamento
     * automático configurado no WeeklyReportFunction (Timer Trigger).
     * 
     * O relatório gerado:
     * - Busca feedbacks da semana atual (segunda-feira até hoje)
     * - Calcula métricas (total, média, por dia, por urgência)
     * - Salva arquivo JSON no Blob Storage (weekly-reports/relatorios/)
     * - Retorna dados do relatório incluindo URL de acesso
     * 
     * Exceções são tratadas automaticamente pelo GlobalExceptionMapper.
     * 
     * @return Resposta com dados do relatório gerado (período, métricas, URL)
     */
    @POST
    public Response generateWeeklyReport() {
        logger.info("Gerando relatório semanal via REST endpoint");
        
        WeeklyReportResponse report = generateWeeklyReportUseCase.execute();
        
        logger.info("Relatório gerado com sucesso: total={}, media={}, url={}", 
            report.getTotalAvaliacoes(), 
            report.getMediaAvaliacoes(), 
            report.getReportUrl());
        
        java.util.Map<String, Object> responseMap = new java.util.HashMap<>();
        responseMap.put("periodo_inicio", report.getPeriodoInicio() != null ? report.getPeriodoInicio().toString() : null);
        responseMap.put("periodo_fim", report.getPeriodoFim() != null ? report.getPeriodoFim().toString() : null);
        responseMap.put("total_avaliacoes", report.getTotalAvaliacoes());
        responseMap.put("media_avaliacoes", report.getMediaAvaliacoes());
        responseMap.put("avaliacoes_por_dia", report.getAvaliacoesPorDia());
        responseMap.put("avaliacoes_por_urgencia", report.getAvaliacoesPorUrgencia());
        responseMap.put("report_url", report.getReportUrl());
        
        return Response.ok()
                .entity(responseMap)
                .build();
    }
}

