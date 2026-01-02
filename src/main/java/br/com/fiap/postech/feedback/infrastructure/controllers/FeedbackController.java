package br.com.fiap.postech.feedback.infrastructure.controllers;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;
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
 * Controller REST para recebimento de feedbacks.
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
@Path("/avaliacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);

    private final CreateFeedbackUseCase createFeedbackUseCase;

    @Inject
    public FeedbackController(CreateFeedbackUseCase createFeedbackUseCase) {
        this.createFeedbackUseCase = createFeedbackUseCase;
    }

    /**
     * Recebe um feedback via POST.
     * 
     * Exceções são tratadas automaticamente pelo GlobalExceptionMapper.
     * 
     * @param request Dados do feedback (descricao, nota, urgencia)
     * @return Resposta com ID do feedback criado
     */
    @POST
    public Response submitFeedback(FeedbackRequest request) {
        logger.info("Recebendo feedback via REST endpoint");
        
        if (request == null) {
            logger.warn("Requisição recebida com corpo vazio");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "Corpo da requisição é obrigatório"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        
        FeedbackResponse result = createFeedbackUseCase.execute(request);
        
        logger.info("Feedback processado com sucesso, id={}", result.id());
        
        return Response.status(Response.Status.CREATED)
                .entity(java.util.Map.of(
                    "id", result.id(),
                    "status", "recebido"
                ))
                .build();
    }
}

