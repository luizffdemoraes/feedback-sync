package br.com.fiap.postech.feedback.infrastructure.handlers;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Azure Function HTTP Trigger para health check.
 * 
 * Expõe o endpoint GET /api/health que verifica o status da aplicação.
 */
public class HealthHttpFunction {

    private static final Logger logger = LoggerFactory.getLogger(HealthHttpFunction.class);

    @FunctionName("health")
    public HttpResponseMessage checkHealth(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "health"
            ) HttpRequestMessage<Void> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Recebendo requisição GET /api/health");
        
        try {
            // Verificar status básico da aplicação
            Map<String, Object> healthStatus = Map.of(
                "status", "UP",
                "service", "feedback-sync",
                "version", "1.0.0"
            );
            
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(healthStatus)
                    .header("Content-Type", "application/json")
                    .build();
                    
        } catch (Exception e) {
            logger.error("Erro ao verificar health", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "DOWN", "error", e.getMessage()))
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
