package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para EmailNotificationGatewayImpl")
class EmailNotificationGatewayImplTest {

    @Mock
    private MailtrapClient mailtrapClient;

    private EmailNotificationGatewayImpl gateway;
    private String mailtrapApiToken;
    private String adminEmail;
    private Long mailtrapInboxId;

    @BeforeEach
    void setUp() {
        mailtrapApiToken = "test-api-token";
        adminEmail = "admin@test.com";
        mailtrapInboxId = 12345L;

        gateway = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            adminEmail,
            mailtrapInboxId
        );

        gateway.setMailtrapClient(mailtrapClient);
    }

    @Test
    @DisplayName("Deve enviar notificação com sucesso")
    void deveEnviarNotificacaoComSucesso() {
        String message = "Mensagem de teste";
        
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));

        ArgumentCaptor<MailtrapMail> mailCaptor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mailtrapClient, times(1)).send(mailCaptor.capture());
        
        MailtrapMail mail = mailCaptor.getValue();
        assertNotNull(mail);
        assertEquals("ALERTA: Feedback Crítico Recebido", mail.getSubject());
        assertEquals(message, mail.getText());
        assertNotNull(mail.getFrom());
        assertEquals("noreply@feedback-sync.com", mail.getFrom().getEmail());
        assertEquals("Feedback Sync", mail.getFrom().getName());
        assertNotNull(mail.getTo());
        assertEquals(1, mail.getTo().size());
        assertEquals(adminEmail, mail.getTo().get(0).getEmail());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando Mailtrap lança exceção")
    void deveLancarNotificationExceptionQuandoMailtrapLancaExcecao() {
        String message = "Mensagem de teste";
        RuntimeException mailtrapException = new RuntimeException("Erro do Mailtrap");
        
        doThrow(mailtrapException).when(mailtrapClient).send(any(MailtrapMail.class));

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("Falha ao enviar email via Mailtrap"));
        assertEquals(mailtrapException, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando Mailtrap não está disponível e API token está vazio")
    void deveRetornarSilenciosamenteQuandoMailtrapNaoDisponivelEApiTokenVazio() {
        String message = "Mensagem de teste";
        
        EmailNotificationGatewayImpl gatewaySemToken = new EmailNotificationGatewayImpl(
            "",
            adminEmail,
            (Long) null
        );
        gatewaySemToken.setMailtrapClient(null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gatewaySemToken.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("Mailtrap nao configurado completamente"));
        assertTrue(exception.getMessage().contains("MAILTRAP_API_TOKEN"));
        assertTrue(exception.getMessage().contains("MAILTRAP_INBOX_ID"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando Mailtrap não está disponível mas API token e Inbox ID estão configurados")
    void deveLancarNotificationExceptionQuandoMailtrapNaoDisponivelMasApiTokenConfigurado() {
        String message = "Mensagem de teste";
        
        EmailNotificationGatewayImpl gatewaySemCliente = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            adminEmail,
            mailtrapInboxId
        );
        gatewaySemCliente.setMailtrapClient(null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gatewaySemCliente.sendAdminNotification(message)
        );

        assertNotNull(exception, "NotificationException deve ser lançada");
        assertTrue(
            exception.getMessage().contains("Mailtrap não está disponível") || 
            exception.getMessage().contains("Falha ao enviar email via Mailtrap"),
            "Mensagem esperada deve conter 'Mailtrap não está disponível' ou 'Falha ao enviar email via Mailtrap', mas foi: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando email do admin não está configurado (vazio)")
    void deveLancarNotificationExceptionQuandoEmailAdminNaoConfiguradoVazio() {
        String message = "Mensagem de teste";
        
        EmailNotificationGatewayImpl gatewaySemEmail = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            "",
            mailtrapInboxId
        );
        gatewaySemEmail.setMailtrapClient(mailtrapClient);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gatewaySemEmail.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando email do admin não está configurado (null)")
    void deveLancarNotificationExceptionQuandoEmailAdminNaoConfiguradoNull() {
        String message = "Mensagem de teste";
        
        EmailNotificationGatewayImpl gatewaySemEmail = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            null,
            mailtrapInboxId
        );
        gatewaySemEmail.setMailtrapClient(mailtrapClient);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gatewaySemEmail.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

}
