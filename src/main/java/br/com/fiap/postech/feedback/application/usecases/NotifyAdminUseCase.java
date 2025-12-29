package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.domain.entities.Feedback;

/**
 * Caso de uso para processar notificações críticas aos administradores.
 * 
 * Responsabilidade: Processar feedback crítico e notificar administradores.
 */
public interface NotifyAdminUseCase {
    void execute(Feedback criticalFeedback);
}

