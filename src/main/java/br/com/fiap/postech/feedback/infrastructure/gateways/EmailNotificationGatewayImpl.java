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
        logger.info("=== Inicializando EmailNotificationGatewayImpl ===");
        logger.debug("mailtrapApiToken configurado: {}", 
            mailtrapApiToken != null && !mailtrapApiToken.isBlank() ? "SIM (primeiros 8 chars: " + mailtrapApiToken.substring(0, Math.min(8, mailtrapApiToken.length())) + "...)" : "NÃO");
        logger.debug("adminEmail configurado: {}", adminEmail != null && !adminEmail.isBlank() ? adminEmail : "NÃO");
        
        if (mailtrapApiToken == null || mailtrapApiToken.isBlank()) {
            logger.warn("⚠ Mailtrap API Token não configurado. Emails não serão enviados.");
            return;
        }
        
        try {
            logger.debug("Criando configuração do Mailtrap...");
            MailtrapConfig config = new MailtrapConfig.Builder()
                    .token(mailtrapApiToken)
                    .build();
            
            logger.debug("Criando cliente Mailtrap...");
            mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
            logger.info("✓ Mailtrap client inicializado com sucesso");
        } catch (Exception e) {
            logger.error("✗ Erro ao inicializar Mailtrap. Tipo: {}, Mensagem: {}", 
                e.getClass().getName(), e.getMessage(), e);
            mailtrapClient = null; // Garantir que seja null em caso de erro
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
        logger.debug("=== sendEmailToAdmin iniciado ===");
        logger.debug("Subject: {}", subject);
        logger.debug("Content length: {} caracteres", content != null ? content.length() : 0);
        logger.debug("mailtrapClient é null: {}", mailtrapClient == null);
        logger.debug("mailtrapApiToken está vazio: {}", 
            mailtrapApiToken == null || mailtrapApiToken.isBlank());
        logger.debug("adminEmail: {}", adminEmail);

        if (mailtrapClient == null) {
            if (mailtrapApiToken == null || mailtrapApiToken.isBlank()) {
                logger.warn("⚠ Mailtrap não configurado. Email não será enviado.");
                return; // Não falha se não estiver configurado (ambiente de desenvolvimento)
            }
            logger.error("✗ Mailtrap client é null mas token está configurado. Tentando reinicializar...");
            // Tentar reinicializar
            try {
                MailtrapConfig config = new MailtrapConfig.Builder()
                        .token(mailtrapApiToken)
                        .build();
                mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
                logger.info("✓ Mailtrap client reinicializado com sucesso");
            } catch (Exception e) {
                logger.error("✗ Falha ao reinicializar Mailtrap client", e);
                throw new NotificationException("Mailtrap não está disponível e não foi possível reinicializar: " + e.getMessage(), e);
            }
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            logger.error("✗ E-mail do administrador não informado");
            throw new NotificationException("E-mail do administrador não informado.");
        }

        try {
            logger.debug("Construindo objeto MailtrapMail...");
            MailtrapMail mail = MailtrapMail.builder()
                    .from(new Address("noreply@feedback-sync.com", "Feedback Sync"))
                    .to(List.of(new Address(adminEmail)))
                    .subject(subject)
                    .category("Notificações")
                    .text(content)
                    .build();

            logger.debug("Enviando email via Mailtrap API...");
            mailtrapClient.send(mail);
            logger.info("✓ Email enviado com sucesso para {}", adminEmail);
            
        } catch (NotificationException e) {
            logger.error("✗ NotificationException ao enviar email", e);
            throw e;
        } catch (Exception e) {
            logger.error("✗ Erro inesperado ao enviar notificação para {}. Tipo: {}, Mensagem: {}", 
                adminEmail, e.getClass().getName(), e.getMessage(), e);
            throw new NotificationException("Falha ao enviar email via Mailtrap: " + e.getMessage(), e);
        }
    }

}
