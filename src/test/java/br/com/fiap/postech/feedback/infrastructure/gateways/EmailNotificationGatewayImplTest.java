package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para EmailNotificationGatewayImpl")
class EmailNotificationGatewayImplTest {

    @Mock
    private SendGrid sendGrid;

    @InjectMocks
    private EmailNotificationGatewayImpl gateway;

    private String apiKey;
    private String adminEmail;
    private String fromEmail;
    private com.sendgrid.Response successResponse;
    private com.sendgrid.Response errorResponse;

    @BeforeEach
    void setUp() throws Exception {
        apiKey = "SG.test-api-key";
        adminEmail = "admin@test.com";
        fromEmail = "noreply@test.com";

        // Injetar valores via reflection
        Field apiKeyField = EmailNotificationGatewayImpl.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(gateway, apiKey);

        Field adminEmailField = EmailNotificationGatewayImpl.class.getDeclaredField("adminEmail");
        adminEmailField.setAccessible(true);
        adminEmailField.set(gateway, adminEmail);

        Field fromEmailField = EmailNotificationGatewayImpl.class.getDeclaredField("fromEmail");
        fromEmailField.setAccessible(true);
        fromEmailField.set(gateway, fromEmail);

        Field sendGridField = EmailNotificationGatewayImpl.class.getDeclaredField("sendGrid");
        sendGridField.setAccessible(true);
        sendGridField.set(gateway, sendGrid);
    }

    @Test
    @DisplayName("Deve enviar notificação com sucesso")
    void deveEnviarNotificacaoComSucesso() throws Exception {
        String message = "Mensagem de teste";
        
        successResponse = mock(com.sendgrid.Response.class);
        when(successResponse.getStatusCode()).thenReturn(202);
        when(sendGrid.api(any(com.sendgrid.Request.class))).thenReturn(successResponse);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));

        ArgumentCaptor<com.sendgrid.Request> requestCaptor = ArgumentCaptor.forClass(com.sendgrid.Request.class);
        verify(sendGrid, times(1)).api(requestCaptor.capture());
        
        com.sendgrid.Request request = requestCaptor.getValue();
        assertNotNull(request);
        assertEquals("POST", request.getMethod().toString());
        assertEquals("mail/send", request.getEndpoint());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando status code indica erro")
    void deveLancarNotificationExceptionQuandoStatusCodeIndicaErro() throws Exception {
        String message = "Mensagem de teste";
        
        errorResponse = mock(com.sendgrid.Response.class);
        when(errorResponse.getStatusCode()).thenReturn(400);
        when(sendGrid.api(any(com.sendgrid.Request.class))).thenReturn(errorResponse);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("Falha ao enviar email"));
        assertTrue(exception.getMessage().contains("Status: 400"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando SendGrid lança exceção")
    void deveLancarNotificationExceptionQuandoSendGridLancaExcecao() throws Exception {
        String message = "Mensagem de teste";
        RuntimeException sendGridException = new RuntimeException("Erro do SendGrid");
        
        when(sendGrid.api(any(com.sendgrid.Request.class))).thenThrow(sendGridException);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("Falha ao enviar email via SendGrid"));
        assertEquals(sendGridException, exception.getCause());
    }

    @Test
    @DisplayName("Deve retornar silenciosamente quando SendGrid não está disponível e API key está vazia")
    void deveRetornarSilenciosamenteQuandoSendGridNaoDisponivelEApiKeyVazia() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover SendGrid
        Field sendGridField = EmailNotificationGatewayImpl.class.getDeclaredField("sendGrid");
        sendGridField.setAccessible(true);
        sendGridField.set(gateway, null);

        // API key vazia
        Field apiKeyField = EmailNotificationGatewayImpl.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(gateway, "");

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));
        verify(sendGrid, never()).api(any(com.sendgrid.Request.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando SendGrid não está disponível mas API key está configurada")
    void deveLancarNotificationExceptionQuandoSendGridNaoDisponivelMasApiKeyConfigurada() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover SendGrid mas manter API key
        Field sendGridField = EmailNotificationGatewayImpl.class.getDeclaredField("sendGrid");
        sendGridField.setAccessible(true);
        sendGridField.set(gateway, null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertEquals("SendGrid não está disponível", exception.getMessage());
    }
}
