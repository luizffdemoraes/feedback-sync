package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementação do EmailNotificationGateway usando SendGrid para envio de emails.
 * 
 * Esta implementação é usada pela NotifyAdminFunction para enviar emails.
 * 
 * Responsabilidade: Enviar emails ao admin via SendGrid.
 * 
 * Fluxo:
 * 1. NotifyAdminFunction (Queue Trigger) → EmailNotificationGatewayImpl.sendAdminNotification()
 * 2. EmailNotificationGatewayImpl → SendGrid API → Email ao admin
 */
@ApplicationScoped
public class EmailNotificationGatewayImpl implements EmailNotificationGateway {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationGatewayImpl.class);

    private final String apiKey;
    private final String adminEmail;
    private final String fromEmail;
    private SendGrid sendGrid;

    @Inject
    public EmailNotificationGatewayImpl(
            @ConfigProperty(name = "sendgrid.api.key", defaultValue = "") String apiKey,
            @ConfigProperty(name = "admin.email", defaultValue = "admin@example.com") String adminEmail,
            @ConfigProperty(name = "sendgrid.from.email", defaultValue = "noreply@feedback-sync.com") String fromEmail) {
        this.apiKey = apiKey;
        this.adminEmail = adminEmail;
        this.fromEmail = fromEmail;
    }

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("SendGrid API Key não configurada. Emails não serão enviados.");
            return;
        }
        
        try {
            sendGrid = new SendGrid(apiKey);
        } catch (Exception e) {
            logger.error("Erro ao inicializar SendGrid", e);
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        String subject = "ALERTA: Feedback Crítico Recebido";
        sendEmailToAdmin(subject, message);
    }

    /**
     * Envia email ao admin usando SendGrid.
     */
    private void sendEmailToAdmin(String subject, String content) throws NotificationException {
        if (sendGrid == null) {
            if (apiKey == null || apiKey.isBlank()) {
                return; // Não falha se não estiver configurado (ambiente de desenvolvimento)
            }
            throw new NotificationException("SendGrid não está disponível");
        }

        try {
            Email from = new Email(fromEmail);
            Email to = new Email(adminEmail);
            Content emailContent = new Content("text/plain", content);
            Mail mail = new Mail(from, subject, to, emailContent);
            
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            com.sendgrid.Response response = sendGrid.api(request);
            
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                throw new NotificationException(String.format("Falha ao enviar email. Status: %d", response.getStatusCode()));
            }
            
        } catch (NotificationException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificationException("Falha ao enviar email via SendGrid: " + e.getMessage(), e);
        }
    }

}
