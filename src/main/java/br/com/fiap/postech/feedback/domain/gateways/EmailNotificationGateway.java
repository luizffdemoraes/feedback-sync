package br.com.fiap.postech.feedback.domain.gateways;

/**
 * Gateway para envio de notificações por email.
 * 
 * Responsabilidade: Enviar notificações ao administrador via email.
 */
public interface EmailNotificationGateway {
    void sendAdminNotification(String message);
}
