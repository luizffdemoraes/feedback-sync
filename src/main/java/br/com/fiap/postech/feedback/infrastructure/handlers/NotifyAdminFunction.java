package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import br.com.fiap.postech.feedback.infrastructure.config.FunctionProcessingException;
import br.com.fiap.postech.feedback.infrastructure.gateways.EmailNotificationGatewayImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
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
 * 
 * NOTA: Esta função NÃO usa CDI/Quarkus para evitar problemas de inicialização
 * do QuarkusAzureFunctionsMiddleware. Todas as dependências são criadas manualmente.
 */
public class NotifyAdminFunction {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminFunction.class);

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
        
        // Obtém dependências: cria manualmente (sem CDI para evitar problemas de inicialização)
        EmailNotificationGateway gateway = getEmailGateway();
        ObjectMapper mapper = getObjectMapper();
        
        logger.info("EmailGateway obtido: {}", gateway != null ? "SIM" : "NÃO");
        logger.info("ObjectMapper obtido: {}", mapper != null ? "SIM" : "NÃO");
        
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
            
            // Com messageEncoding="none" no host.json, o Azure Functions passa a mensagem
            // diretamente como texto puro (JSON) para a função, sem codificação Base64.
            // O QueueNotificationGatewayImpl também envia texto puro (QueueMessageEncoding.NONE),
            // então a mensagem já vem como JSON válido.
            logger.debug("Mensagem recebida (decodificada pelo Azure Functions): {}", 
                message.length() > 200 ? message.substring(0, 200) + "..." : message);

            if (mapper == null) {
                logger.error("ObjectMapper não disponível");
                throw new FunctionProcessingException("ObjectMapper não disponível");
            }

            if (gateway == null) {
                logger.error("EmailNotificationGateway não disponível");
                throw new FunctionProcessingException("EmailNotificationGateway não disponível");
            }

            logger.debug("Iniciando deserialização do JSON...");
            logger.debug("Mensagem a ser deserializada: {}", message);
            Feedback feedback = mapper.readValue(message, Feedback.class);
            
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
            gateway.sendAdminNotification(emailContent);
            
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

    /**
     * Obtém EmailNotificationGateway: cria manualmente (sem CDI).
     * Package-private para permitir mock em testes.
     */
    EmailNotificationGateway getEmailGateway() {
        logger.info("Criando EmailNotificationGateway manualmente");
        String mailtrapToken = System.getenv("MAILTRAP_API_TOKEN");
        String adminEmail = System.getenv("ADMIN_EMAIL");
        String mailtrapInboxId = System.getenv("MAILTRAP_INBOX_ID");
        
        EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(mailtrapToken, adminEmail, mailtrapInboxId);
        
        // Inicializa manualmente chamando o método init() via reflection
        try {
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gateway);
            logger.info("✓ EmailNotificationGateway inicializado manualmente");
        } catch (Exception e) {
            logger.warn("⚠ Não foi possível inicializar EmailNotificationGateway via reflection: {}", e.getMessage());
        }
        
        return gateway;
    }

    /**
     * Obtém ObjectMapper: cria manualmente (sem CDI).
     * Package-private para permitir mock em testes.
     */
    ObjectMapper getObjectMapper() {
        logger.info("Criando ObjectMapper manualmente");
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
