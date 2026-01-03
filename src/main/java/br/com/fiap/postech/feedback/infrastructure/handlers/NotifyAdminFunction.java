package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.infrastructure.config.FunctionProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Function que processa notificações críticas de feedbacks.
 * 
 * Tipo: Queue Trigger
 * Responsabilidade única: Processar mensagens da fila e enviar emails via Mailtrap
 * 
 * Fluxo:
 * 1. CreateFeedbackUseCase publica feedback crítico na fila Azure Queue Storage
 * 2. Azure Queue Storage dispara esta função automaticamente
 * 3. Função deserializa o feedback e envia email via Mailtrap
 * 
 * Integração com Recursos Azure:
 * - Azure Queue Storage (trigger)
 * - Mailtrap (envio de emails)
 */
@ApplicationScoped
public class NotifyAdminFunction {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminFunction.class);

    // Bloco estático para garantir que a classe seja carregada
    static {
        logger.info("🔵 NotifyAdminFunction CLASSE CARREGADA");
    }

    private final EmailNotificationGateway emailGateway;
    private final ObjectMapper objectMapper;

    @Inject
    public NotifyAdminFunction(
            EmailNotificationGateway emailGateway,
            ObjectMapper objectMapper) {
        logger.info("=== NotifyAdminFunction CONSTRUTOR CHAMADO ===");
        logger.info("EmailGateway injetado: {}", emailGateway != null ? "SIM" : "NÃO");
        logger.info("ObjectMapper injetado: {}", objectMapper != null ? "SIM" : "NÃO");
        this.emailGateway = emailGateway;
        this.objectMapper = objectMapper;
        logger.info("=== NotifyAdminFunction INSTANCIADA COM SUCESSO ===");
    }

    @FunctionName("notifyAdmin")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "critical-feedbacks",
                    connection = "AzureWebJobsStorage"
            ) String message,
            final ExecutionContext context) {
        
        // Log crítico para confirmar que a função foi executada
        context.getLogger().info("🔴🔴🔴 NOTIFYADMIN FUNCTION EXECUTADA 🔴🔴🔴");
        logger.info("=== NotifyAdminFunction INICIADA ===");
        logger.info("Mensagem recebida da fila (tamanho: {} chars)", message != null ? message.length() : 0);
        logger.info("Mensagem recebida da fila (primeiros 500 chars): {}", 
            message != null && message.length() > 500 ? message.substring(0, 500) + "..." : message);
        logger.info("Mensagem completa: {}", message);
        logger.info("EmailGateway é null: {}", emailGateway == null);
        logger.info("ObjectMapper é null: {}", objectMapper == null);
        
        // Log de variáveis de ambiente para diagnóstico
        String mailtrapToken = System.getenv("MAILTRAP_API_TOKEN");
        String adminEmail = System.getenv("ADMIN_EMAIL");
        String mailtrapInboxId = System.getenv("MAILTRAP_INBOX_ID");
        logger.info("Variáveis de ambiente - MAILTRAP_API_TOKEN: {}", mailtrapToken != null && !mailtrapToken.isBlank() ? "CONFIGURADO" : "NÃO CONFIGURADO");
        logger.info("Variáveis de ambiente - ADMIN_EMAIL: {}", adminEmail != null && !adminEmail.isBlank() ? adminEmail : "NÃO CONFIGURADO");
        logger.info("Variáveis de ambiente - MAILTRAP_INBOX_ID: {}", mailtrapInboxId != null && !mailtrapInboxId.isBlank() ? mailtrapInboxId : "NÃO CONFIGURADO");
        
        try {
            // Validação inicial
            if (message == null || message.isBlank()) {
                logger.error("Mensagem recebida está vazia ou nula");
                throw new FunctionProcessingException("Mensagem da fila está vazia ou nula");
            }
            
            // Tentar decodificar Base64 se necessário (Azure Queue Storage codifica mensagens em Base64)
            // O Azure Functions deve decodificar automaticamente se host.json tiver "messageEncoding": "base64"
            // Mas vamos fazer uma tentativa manual também para garantir compatibilidade
            String decodedMessage = message;
            try {
                // Verificar se a mensagem parece ser Base64 (não é JSON válido)
                if (!message.trim().startsWith("{") && !message.trim().startsWith("[")) {
                    // Tentar decodificar Base64
                    if (message.matches("^[A-Za-z0-9+/=]+$") && message.length() % 4 == 0) {
                        try {
                            byte[] decodedBytes = java.util.Base64.getDecoder().decode(message);
                            String potentialJson = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                            // Verificar se o resultado decodificado parece JSON
                            if (potentialJson.trim().startsWith("{") || potentialJson.trim().startsWith("[")) {
                                decodedMessage = potentialJson;
                                logger.info("✓ Mensagem decodificada de Base64 (tamanho original: {}, decodificado: {})", 
                                    message.length(), decodedMessage.length());
                                logger.debug("Conteúdo decodificado (primeiros 200 chars): {}", 
                                    decodedMessage.length() > 200 ? decodedMessage.substring(0, 200) + "..." : decodedMessage);
                            } else {
                                logger.warn("⚠ Decodificação Base64 não resultou em JSON válido. Usando mensagem original.");
                            }
                        } catch (IllegalArgumentException e) {
                            logger.debug("Tentativa de decodificar Base64 falhou (não é Base64 válido): {}", e.getMessage());
                            // Continuar com a mensagem original
                        }
                    } else {
                        logger.debug("Mensagem não parece ser Base64 válido. Usando como está.");
                    }
                } else {
                    logger.debug("Mensagem já parece ser JSON válido. Usando diretamente.");
                }
            } catch (Exception e) {
                logger.warn("⚠ Erro ao tentar decodificar mensagem: {}. Usando mensagem original.", e.getMessage());
                // Continuar com a mensagem original
            }
            
            message = decodedMessage;

            if (objectMapper == null) {
                logger.error("ObjectMapper não foi injetado corretamente");
                throw new FunctionProcessingException("ObjectMapper não disponível");
            }

            if (emailGateway == null) {
                logger.error("EmailNotificationGateway não foi injetado corretamente");
                throw new FunctionProcessingException("EmailNotificationGateway não disponível");
            }

            logger.debug("Iniciando deserialização do JSON...");
            logger.debug("Mensagem a ser deserializada: {}", message);
            Feedback feedback = objectMapper.readValue(message, Feedback.class);
            
            if (feedback == null) {
                logger.error("Feedback deserializado é nulo");
                throw new FunctionProcessingException("Feedback deserializado é nulo");
            }

            logger.info("Feedback deserializado com sucesso - ID: {}, Nota: {}, Descrição: {}", 
                feedback.getId(), 
                feedback.getScore() != null ? feedback.getScore().getValue() : "N/A",
                feedback.getDescription() != null ? feedback.getDescription().substring(0, Math.min(50, feedback.getDescription().length())) : "N/A");

            logger.debug("Construindo conteúdo do email...");
            String emailContent = buildEmailContent(feedback);
            
            logger.info("Enviando notificação via emailGateway...");
            logger.info("Conteúdo do email (primeiros 100 chars): {}", 
                emailContent != null && emailContent.length() > 100 ? emailContent.substring(0, 100) + "..." : emailContent);
            emailGateway.sendAdminNotification(emailContent);
            
            logger.info("✓✓✓ Notificação enviada com sucesso - ID: {}", feedback.getId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Erro ao deserializar JSON da mensagem. Mensagem: {}", message, e);
            throw new FunctionProcessingException("Falha ao deserializar mensagem JSON: " + e.getMessage(), e);
        } catch (FunctionProcessingException e) {
            logger.error("Erro conhecido ao processar notificação", e);
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao processar notificação. Tipo: {}, Mensagem: {}", 
                e.getClass().getName(), e.getMessage(), e);
            throw new FunctionProcessingException("Falha ao processar notificação crítica: " + e.getMessage(), e);
        }
    }

    /**
     * Constrói conteúdo do email a partir do feedback.
     */
    private String buildEmailContent(Feedback feedback) {
        StringBuilder content = new StringBuilder();
        content.append("ALERTA: Feedback Crítico Recebido\n\n");
        content.append("Detalhes do Feedback:\n");
        content.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        content.append("ID: ").append(feedback.getId()).append("\n");
        content.append("Descrição: ").append(feedback.getDescription()).append("\n");
        content.append("Nota: ").append(feedback.getScore().getValue()).append("/10\n");
        content.append("Urgência: ").append(feedback.getUrgency().toString()).append("\n");
        content.append("Data de Envio: ").append(feedback.getCreatedAt()).append("\n");
        content.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        content.append("Este é um email automático do sistema de feedback.\n");
        content.append("Por favor, analise este feedback crítico com urgência.\n");
        return content.toString();
    }
}
