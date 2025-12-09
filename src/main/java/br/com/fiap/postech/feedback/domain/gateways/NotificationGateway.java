package br.com.fiap.postech.feedback.domain.gateways;

public interface NotificationGateway {
    void publishCritical(Object payload);
    void sendAdminNotification(String message);
}