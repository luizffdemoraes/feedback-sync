package br.com.fiap.postech.feedback.infrastructure.gateways;

import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
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
    private MailtrapClient mailtrapClient;

    @InjectMocks
    private EmailNotificationGatewayImpl gateway;

    private String mailtrapApiToken;
    private String adminEmail;

    @BeforeEach
    void setUp() throws Exception {
        mailtrapApiToken = "test-api-token";
        adminEmail = "admin@test.com";

        // Injetar valores via reflection
        Field apiTokenField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapApiToken");
        apiTokenField.setAccessible(true);
        apiTokenField.set(gateway, mailtrapApiToken);

        Field adminEmailField = EmailNotificationGatewayImpl.class.getDeclaredField("adminEmail");
        adminEmailField.setAccessible(true);
        adminEmailField.set(gateway, adminEmail);

        Field mailtrapClientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        mailtrapClientField.setAccessible(true);
        mailtrapClientField.set(gateway, mailtrapClient);
    }

    @Test
    @DisplayName("Deve enviar notificação com sucesso")
    void deveEnviarNotificacaoComSucesso() {
        String message = "Mensagem de teste";
        
        // O método send() retorna um objeto, não é void
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));

        ArgumentCaptor<MailtrapMail> mailCaptor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mailtrapClient, times(1)).send(mailCaptor.capture());
        
        MailtrapMail mail = mailCaptor.getValue();
        assertNotNull(mail);
        assertEquals("ALERTA: Feedback Crítico Recebido", mail.getSubject());
        assertEquals(message, mail.getText());
        // Validar email remetente fixo
        assertNotNull(mail.getFrom());
        assertEquals("noreply@feedback-sync.com", mail.getFrom().getEmail());
        assertEquals("Feedback Sync", mail.getFrom().getName());
        // Validar destinatário
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
    @DisplayName("Deve retornar silenciosamente quando Mailtrap não está disponível e API token está vazio")
    void deveRetornarSilenciosamenteQuandoMailtrapNaoDisponivelEApiTokenVazio() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover MailtrapClient
        Field mailtrapClientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        mailtrapClientField.setAccessible(true);
        mailtrapClientField.set(gateway, null);

        // API token vazio
        Field apiTokenField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapApiToken");
        apiTokenField.setAccessible(true);
        apiTokenField.set(gateway, "");

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));
        verify(mailtrapClient, never()).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando Mailtrap não está disponível mas API token está configurado")
    void deveLancarNotificationExceptionQuandoMailtrapNaoDisponivelMasApiTokenConfigurado() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover MailtrapClient mas manter API token
        Field mailtrapClientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        mailtrapClientField.setAccessible(true);
        mailtrapClientField.set(gateway, null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertEquals("Mailtrap não está disponível", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando email do admin não está configurado (vazio)")
    void deveLancarNotificationExceptionQuandoEmailAdminNaoConfiguradoVazio() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover email do admin (vazio)
        Field adminEmailField = EmailNotificationGatewayImpl.class.getDeclaredField("adminEmail");
        adminEmailField.setAccessible(true);
        adminEmailField.set(gateway, "");

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando email do admin não está configurado (null)")
    void deveLancarNotificationExceptionQuandoEmailAdminNaoConfiguradoNull() throws Exception {
        String message = "Mensagem de teste";
        
        // Remover email do admin (null)
        Field adminEmailField = EmailNotificationGatewayImpl.class.getDeclaredField("adminEmail");
        adminEmailField.setAccessible(true);
        adminEmailField.set(gateway, null);

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

}
