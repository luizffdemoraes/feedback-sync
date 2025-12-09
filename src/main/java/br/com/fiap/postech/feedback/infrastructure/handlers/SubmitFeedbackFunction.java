package br.com.fiap.postech.feedback.infrastructure.handlers;



import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.usecases.CreateFeedbackUseCase;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/feedbacks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SubmitFeedbackResource {

    private static final Logger LOG = Logger.getLogger(SubmitFeedbackResource.class);

    @Inject
    CreateFeedbackUseCase createFeedbackUseCase;

    @POST
    public Response submitFeedback(FeedbackRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("missing body")
                    .build();
        }
        try {
            var result = createFeedbackUseCase.execute(request);
            return Response.status(Response.Status.CREATED)
                    .entity(result)
                    .build();
        } catch (Exception e) {
            LOG.error("Error submitting feedback", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }
}

