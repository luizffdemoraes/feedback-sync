package br.com.fiap.postech.feedback.domain.gateways;

/**
 * Gateway para publicação de notificações críticas em fila.
 * 
 * Responsabilidade: Publicar mensagens críticas em fila para processamento assíncrono.
 */
public interface QueueNotificationGateway {
    void publishCritical(Object payload);
}
