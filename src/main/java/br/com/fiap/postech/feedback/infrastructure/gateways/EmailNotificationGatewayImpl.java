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
 * Esta implementação é usada pelo CreateFeedbackUseCase para enviar emails diretamente.
 * 
 * Responsabilidade: Enviar emails ao admin via Mailtrap.
 * 
 * Fluxo:
 * 1. CreateFeedbackUseCase (quando feedback é crítico) → EmailNotificationGatewayImpl.sendAdminNotification()
 * 2. EmailNotificationGatewayImpl → Mailtrap API → Email ao admin
 */
@ApplicationScoped
public class EmailNotificationGatewayImpl implements EmailNotificationGateway {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationGatewayImpl.class);

    private final String mailtrapApiToken;
    private final String adminEmail;
    private final Long mailtrapInboxId;
    private MailtrapClient mailtrapClient;

    /**
     * Construtor para CDI (produção).
     * Usa @ConfigProperty para injetar valores de configuração.
     */
    @Inject
    public EmailNotificationGatewayImpl(
            @ConfigProperty(name = "mailtrap.api-token") String mailtrapApiToken,
            @ConfigProperty(name = "admin.email") String adminEmail,
            @ConfigProperty(name = "mailtrap.inbox-id") String mailtrapInboxIdStr) {
        this(mailtrapApiToken, adminEmail, parseInboxId(mailtrapInboxIdStr));
    }

    /**
     * Construtor público para testes e criação manual.
     * Permite criar instâncias sem depender de CDI.
     * 
     * @param mailtrapApiToken Token da API do Mailtrap
     * @param adminEmail Email do administrador
     * @param mailtrapInboxId ID da inbox do Mailtrap (pode ser null)
     */
    public EmailNotificationGatewayImpl(
            String mailtrapApiToken,
            String adminEmail,
            Long mailtrapInboxId) {
        this.mailtrapApiToken = mailtrapApiToken;
        this.adminEmail = adminEmail;
        this.mailtrapInboxId = mailtrapInboxId;
    }
    
    /**
     * Converte a string do inbox ID para Long.
     * Retorna null se não estiver configurado ou inválido.
     * Método estático para permitir uso no construtor CDI.
     */
    private static Long parseInboxId(String inboxIdStr) {
        if (inboxIdStr == null || inboxIdStr.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(inboxIdStr.trim());
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(EmailNotificationGatewayImpl.class)
                .warn("AVISO: Valor invalido para MAILTRAP_INBOX_ID: '{}'. Deve ser um numero.", inboxIdStr);
            return null;
        }
    }

    /**
     * Define o cliente Mailtrap.
     * Método package-private para permitir injeção em testes sem usar reflection.
     * 
     * @param mailtrapClient Cliente Mailtrap a ser usado
     */
    void setMailtrapClient(MailtrapClient mailtrapClient) {
        this.mailtrapClient = mailtrapClient;
    }

    @PostConstruct
    void init() {
        logger.info("=== Inicializando EmailNotificationGatewayImpl ===");
        logger.info("mailtrapApiToken configurado: {}", 
            mailtrapApiToken != null && !mailtrapApiToken.isBlank() ? "SIM (primeiros 8 chars: " + mailtrapApiToken.substring(0, Math.min(8, mailtrapApiToken.length())) + "...)" : "NAO");
        logger.info("adminEmail configurado: {}", adminEmail != null && !adminEmail.isBlank() ? adminEmail : "NAO");
        logger.info("mailtrapInboxId configurado: {}", mailtrapInboxId != null ? mailtrapInboxId : "NAO");
        
        if (mailtrapApiToken == null || mailtrapApiToken.isBlank()) {
            logger.warn("AVISO: Mailtrap API Token nao configurado. Emails nao serao enviados.");
            return;
        }
        
        if (mailtrapInboxId == null) {
            logger.warn("AVISO: Mailtrap Inbox ID nao configurado. Configure MAILTRAP_INBOX_ID. Emails nao serao enviados.");
            return;
        }
        
        try {
            logger.info("Criando configuracao do Mailtrap...");
            logger.info("Inbox ID: {}", mailtrapInboxId);
            logger.info("Token configurado: {}", mailtrapApiToken != null && !mailtrapApiToken.isBlank());
            
            // Seguindo o padrão do exemplo oficial do Mailtrap
            final MailtrapConfig config = new MailtrapConfig.Builder()
                    .sandbox(true)
                    .inboxId(mailtrapInboxId)
                    .token(mailtrapApiToken)
                    .build();
            
            logger.info("Criando cliente Mailtrap...");
            mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
            logger.info("Mailtrap client inicializado com sucesso");
        } catch (Exception e) {
            logger.error("ERRO ao inicializar Mailtrap. Tipo: {}, Mensagem: {}", 
                e.getClass().getName(), e.getMessage(), e);
            logger.error("Stack trace completo:", e);
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
        logger.info("=== sendEmailToAdmin INICIADO ===");
        logger.info("Subject: {}", subject);
        logger.info("Content length: {} caracteres", content != null ? content.length() : 0);
        logger.info("mailtrapClient é null: {}", mailtrapClient == null);
        logger.info("mailtrapApiToken está vazio: {}", 
            mailtrapApiToken == null || mailtrapApiToken.isBlank());
        logger.info("adminEmail: {}", adminEmail);

        if (mailtrapClient == null) {
            if (mailtrapApiToken == null || mailtrapApiToken.isBlank() || mailtrapInboxId == null) {
                logger.warn("AVISO: Mailtrap nao configurado completamente. Email nao sera enviado. Configure MAILTRAP_API_TOKEN e MAILTRAP_INBOX_ID.");
                throw new NotificationException("Mailtrap nao configurado completamente. Configure MAILTRAP_API_TOKEN e MAILTRAP_INBOX_ID.");
            }
            logger.error("ERRO: Mailtrap client e null mas token esta configurado. Tentando reinicializar...");
            // Tentar reinicializar seguindo o padrão do exemplo oficial do Mailtrap
            try {
                logger.info("Reinicializando Mailtrap client...");
                logger.info("Inbox ID: {}", mailtrapInboxId);
                final MailtrapConfig config = new MailtrapConfig.Builder()
                        .sandbox(true)
                        .inboxId(mailtrapInboxId)
                        .token(mailtrapApiToken)
                        .build();
                mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
                logger.info("Mailtrap client reinicializado com sucesso");
            } catch (Exception e) {
                logger.error("ERRO: Falha ao reinicializar Mailtrap client", e);
                logger.error("Stack trace completo:", e);
                throw new NotificationException("Mailtrap nao esta disponivel e nao foi possivel reinicializar: " + e.getMessage(), e);
            }
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            logger.error("ERRO: E-mail do administrador nao informado");
            throw new NotificationException("E-mail do administrador não informado.");
        }

        try {
            logger.info("Construindo objeto MailtrapMail...");
            // Seguindo o padrão do exemplo oficial do Mailtrap
            final MailtrapMail mail = MailtrapMail.builder()
                    .from(new Address("noreply@feedback-sync.com", "Feedback Sync"))
                    .to(List.of(new Address(adminEmail)))
                    .subject(subject)
                    .text(content)
                    .category("Notificações")
                    .build();

            logger.info("Enviando email via Mailtrap API...");
            logger.info("Detalhes do email:");
            logger.info("  - De: {} ({})", mail.getFrom().getEmail(), mail.getFrom().getName());
            logger.info("  - Para: {}", adminEmail);
            logger.info("  - Assunto: {}", subject);
            logger.info("  - Categoria: {}", mail.getCategory());
            
            // Capturar resposta do método send() (como no exemplo oficial)
            logger.info("Chamando mailtrapClient.send()...");
            Object response = mailtrapClient.send(mail);
            logger.info("Resposta do Mailtrap API: {}", response != null ? response.toString() : "null");
            
            if (response != null) {
                logger.info("Email enviado com sucesso para {} - Resposta: {}", adminEmail, response);
            } else {
                logger.info("Email enviado com sucesso para {} (sem resposta do servidor)", adminEmail);
            }
            
        } catch (NotificationException e) {
            logger.error("ERRO: NotificationException ao enviar email", e);
            logger.error("Stack trace completo:", e);
            throw e;
        } catch (Exception e) {
            logger.error("ERRO: Erro inesperado ao enviar notificacao para {}. Tipo: {}, Mensagem: {}", 
                adminEmail, e.getClass().getName(), e.getMessage(), e);
            logger.error("Stack trace completo:", e);
            throw new NotificationException("Falha ao enviar email via Mailtrap: " + e.getMessage(), e);
        }
    }

}
