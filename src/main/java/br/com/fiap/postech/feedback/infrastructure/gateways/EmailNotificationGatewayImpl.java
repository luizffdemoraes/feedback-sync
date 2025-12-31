package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.EmailNotificationGateway;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementação do EmailNotificationGateway usando Mailtrap para envio de emails.
 * 
 * Esta implementação é usada pela NotifyAdminFunction para enviar emails.
 * 
 * Responsabilidade: Enviar emails ao admin via Mailtrap.
 * 
 * Fluxo:
 * 1. NotifyAdminFunction (Queue Trigger) → EmailNotificationGatewayImpl.sendAdminNotification()
 * 2. EmailNotificationGatewayImpl → Mailtrap API → Email ao admin
 */
@ApplicationScoped
public class EmailNotificationGatewayImpl implements EmailNotificationGateway {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationGatewayImpl.class);

    private final String mailtrapApiToken;
    private final String adminEmail;
    private MailtrapClient mailtrapClient;

    @Inject
    public EmailNotificationGatewayImpl(
            @ConfigProperty(name = "mailtrap.api-token") String mailtrapApiToken,
            @ConfigProperty(name = "admin.email") String adminEmail) {
        this.mailtrapApiToken = mailtrapApiToken;
        this.adminEmail = adminEmail;
    }

    @PostConstruct
    void init() {
        if (mailtrapApiToken == null || mailtrapApiToken.isBlank()) {
            logger.warn("Mailtrap API Token não configurado. Emails não serão enviados.");
            return;
        }
        
        try {
            MailtrapConfig config = new MailtrapConfig.Builder()
                    .token(mailtrapApiToken)
                    .build();
            
            mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
            logger.info("Mailtrap client inicializado com sucesso");
        } catch (Exception e) {
            logger.error("Erro ao inicializar Mailtrap", e);
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        String subject = "ALERTA: Feedback Crítico Recebido";
        sendEmailToAdmin(subject, message);
    }

    /**
     * Envia email ao admin usando Mailtrap.
     */
    private void sendEmailToAdmin(String subject, String content) throws NotificationException {
        if (mailtrapClient == null) {
            if (mailtrapApiToken == null || mailtrapApiToken.isBlank()) {
                logger.warn("Mailtrap não configurado. Email não será enviado.");
                return; // Não falha se não estiver configurado (ambiente de desenvolvimento)
            }
            throw new NotificationException("Mailtrap não está disponível");
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            throw new NotificationException("E-mail do administrador não informado.");
        }

        try {
            MailtrapMail mail = MailtrapMail.builder()
                    .from(new Address("noreply@feedback-sync.com", "Feedback Sync"))
                    .to(List.of(new Address(adminEmail)))
                    .subject(subject)
                    .category("Notificações")
                    .text(content)
                    .build();

            mailtrapClient.send(mail);
            logger.info("Email enviado com sucesso para {}", adminEmail);
            
        } catch (NotificationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Erro ao enviar notificação para {}: {}", adminEmail, e.getMessage(), e);
            throw new NotificationException("Falha ao enviar email via Mailtrap: " + e.getMessage(), e);
        }
    }

}
