package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ServiceBusNotificationGatewayImpl implements NotificationGateway {

    // Inject ServiceBus client

    @Override
    public void publishCritical(Object payload) {
        // publish message to topic
    }

    @Override
    public void sendAdminNotification(String message) {
        // send email or log
    }
}
