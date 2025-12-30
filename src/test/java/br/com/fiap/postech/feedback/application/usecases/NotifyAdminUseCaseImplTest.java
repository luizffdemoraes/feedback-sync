package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para NotifyAdminUseCaseImpl")
class NotifyAdminUseCaseImplTest {

    @Mock
    private NotificationGateway notificationGateway;

    @InjectMocks
    private NotifyAdminUseCaseImpl notifyAdminUseCase;

    private Feedback feedbackCritico;

    @BeforeEach
    void setUp() {
        feedbackCritico = new Feedback(
            "Aula muito ruim, precisa melhorar",
            2,
            "HIGH"
        );
        feedbackCritico.setCreatedAt(LocalDateTime.now());
        feedbackCritico.setId("feedback-id-123");
    }

    @Test
    @DisplayName("Deve processar notificação crítica com sucesso")
    void deveProcessarNotificacaoCriticaComSucesso() {
        doNothing().when(notificationGateway).sendAdminNotification(anyString());

        assertDoesNotThrow(() -> notifyAdminUseCase.execute(feedbackCritico));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationGateway, times(1)).sendAdminNotification(messageCaptor.capture());

        String mensagemEnviada = messageCaptor.getValue();
        assertTrue(mensagemEnviada.contains("ALERTA: Feedback Crítico Recebido"));
        assertTrue(mensagemEnviada.contains(feedbackCritico.getId()));
        assertTrue(mensagemEnviada.contains(feedbackCritico.getDescription()));
        assertTrue(mensagemEnviada.contains(String.valueOf(feedbackCritico.getScore().getValue())));
        assertTrue(mensagemEnviada.contains(feedbackCritico.getUrgency().getValue()));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando gateway falha")
    void deveLancarNotificationExceptionQuandoGatewayFalha() {
        NotificationException exceptionEsperada = new NotificationException("Erro ao enviar notificação");
        doThrow(exceptionEsperada)
            .when(notificationGateway).sendAdminNotification(anyString());

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> notifyAdminUseCase.execute(feedbackCritico)
        );

        assertEquals(exceptionEsperada, exception);
        verify(notificationGateway, times(1)).sendAdminNotification(anyString());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre exceção genérica")
    void deveLancarNotificationExceptionQuandoOcorreExcecaoGenerica() {
        RuntimeException exceptionOriginal = new RuntimeException("Erro inesperado");
        doThrow(exceptionOriginal)
            .when(notificationGateway).sendAdminNotification(anyString());

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> notifyAdminUseCase.execute(feedbackCritico)
        );

        assertEquals("Falha ao processar notificação crítica", exception.getMessage());
        assertEquals(exceptionOriginal, exception.getCause());
        verify(notificationGateway, times(1)).sendAdminNotification(anyString());
    }

    @Test
    @DisplayName("Deve construir mensagem corretamente com todos os dados do feedback")
    void deveConstruirMensagemCorretamente() {
        doNothing().when(notificationGateway).sendAdminNotification(anyString());

        notifyAdminUseCase.execute(feedbackCritico);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationGateway).sendAdminNotification(messageCaptor.capture());

        String mensagem = messageCaptor.getValue();
        assertTrue(mensagem.contains("ID: " + feedbackCritico.getId()));
        assertTrue(mensagem.contains("Descrição: " + feedbackCritico.getDescription()));
        assertTrue(mensagem.contains("Nota: " + feedbackCritico.getScore().getValue() + "/10"));
        assertTrue(mensagem.contains("Urgência: " + feedbackCritico.getUrgency().getValue()));
        assertTrue(mensagem.contains("Data de Envio:"));
    }

    @Test
    @DisplayName("Deve lidar com feedback com createdAt nulo")
    void deveLidarComFeedbackComCreatedAtNulo() {
        Feedback feedbackSemData = new Feedback("Teste", 1, "HIGH");
        feedbackSemData.setId("id-sem-data");
        feedbackSemData.setCreatedAt(null);

        doNothing().when(notificationGateway).sendAdminNotification(anyString());

        assertDoesNotThrow(() -> notifyAdminUseCase.execute(feedbackSemData));

        verify(notificationGateway, times(1)).sendAdminNotification(anyString());
    }
}
