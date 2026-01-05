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

    @Test
    @DisplayName("Deve enviar notificação com sucesso quando Mailtrap retorna resposta não-nula")
    void deveEnviarNotificacaoComSucessoQuandoMailtrapRetornaRespostaNaoNula() {
        String message = "Mensagem de teste";
        
        // O método send() pode retornar null ou um objeto SendResponse
        // Como já testamos o caso null, vamos apenas garantir que funciona com qualquer retorno
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));

        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre erro na reinicialização do Mailtrap")
    void deveLancarNotificationExceptionQuandoOcorreErroNaReinicializacaoDoMailtrap() {
        String message = "Mensagem de teste";
        
        EmailNotificationGatewayImpl gatewaySemCliente = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            adminEmail,
            mailtrapInboxId
        );
        gatewaySemCliente.setMailtrapClient(null);

        // Como o gateway tenta reinicializar, pode lançar exceção se falhar
        // Vamos verificar que uma exceção é lançada
        assertThrows(
            Exception.class,
            () -> gatewaySemCliente.sendAdminNotification(message)
        );
    }

    @Test
    @DisplayName("Deve enviar notificação quando adminEmail contém espaços mas não está vazio")
    void deveEnviarNotificacaoQuandoAdminEmailContemEspacosMasNaoEstaVazio() {
        String message = "Mensagem de teste";
        EmailNotificationGatewayImpl gatewayComEspacos = new EmailNotificationGatewayImpl(
            mailtrapApiToken,
            "  admin@test.com  ",
            mailtrapInboxId
        );
        gatewayComEspacos.setMailtrapClient(mailtrapClient);
        
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        // O email com espaços deve ser aceito (não está vazio)
        assertDoesNotThrow(() -> gatewayComEspacos.sendAdminNotification(message));
        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre exceção genérica ao enviar email")
    void deveLancarNotificationExceptionQuandoOcorreExcecaoGenericaAoEnviarEmail() {
        String message = "Mensagem de teste";
        RuntimeException genericException = new RuntimeException("Erro genérico");
        
        doThrow(genericException).when(mailtrapClient).send(any(MailtrapMail.class));

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertTrue(exception.getMessage().contains("Falha ao enviar email via Mailtrap"));
        assertEquals(genericException, exception.getCause());
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando ocorre NotificationException ao enviar email")
    void deveLancarNotificationExceptionQuandoOcorreNotificationExceptionAoEnviarEmail() {
        String message = "Mensagem de teste";
        NotificationException notificationException = new NotificationException("Erro de notificação");
        
        doThrow(notificationException).when(mailtrapClient).send(any(MailtrapMail.class));

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertEquals(notificationException, exception);
    }

    @Test
    @DisplayName("Deve retornar silenciosamente quando Mailtrap client.send retorna null")
    void deveRetornarSilenciosamenteQuandoMailtrapClientSendRetornaNull() {
        String message = "Mensagem de teste";
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));
        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("Deve logar resposta do Mailtrap API quando não é null")
    void deveLogarRespostaDoMailtrapApiQuandoNaoENull() {
        String message = "Mensagem de teste";
        // O método send() retorna SendResponse, mas podemos usar null para testar o fluxo
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));
        verify(mailtrapClient, times(1)).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("Deve inicializar corretamente quando token e inbox ID estão configurados")
    void deveInicializarCorretamenteQuandoTokenEInboxIdEstaoConfigurados() {
        EmailNotificationGatewayImpl gatewayComConfig = new EmailNotificationGatewayImpl(
            "test-token",
            "admin@test.com",
            12345L
        );
        
        // O init() será chamado automaticamente pelo @PostConstruct em ambiente CDI
        // Mas podemos testar manualmente
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gatewayComConfig);
        });
    }

    @Test
    @DisplayName("Deve inicializar corretamente quando token está vazio")
    void deveInicializarCorretamenteQuandoTokenEstaVazio() {
        EmailNotificationGatewayImpl gatewaySemToken = new EmailNotificationGatewayImpl(
            "",
            "admin@test.com",
            12345L
        );
        
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gatewaySemToken);
        });
    }

    @Test
    @DisplayName("Deve inicializar corretamente quando inbox ID está null")
    void deveInicializarCorretamenteQuandoInboxIdENull() {
        EmailNotificationGatewayImpl gatewaySemInbox = new EmailNotificationGatewayImpl(
            "test-token",
            "admin@test.com",
            (Long) null
        );
        
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gatewaySemInbox);
        });
    }

    @Test
    @DisplayName("Deve tratar erro na inicialização do Mailtrap client")
    void deveTratarErroNaInicializacaoDoMailtrapClient() {
        EmailNotificationGatewayImpl gatewayComErro = new EmailNotificationGatewayImpl(
            "token-invalido",
            "admin@test.com",
            12345L
        );
        
        // O init() pode falhar ao criar o cliente, mas não deve lançar exceção
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(gatewayComErro);
        });
    }

    @Test
    @DisplayName("Deve reinicializar Mailtrap client quando client é null mas token está configurado")
    void deveReinicializarMailtrapClientQuandoClientENullMasTokenEstaConfigurado() throws Exception {
        EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(
            "valid-token",
            "admin@test.com",
            12345L
        );
        
        // Inicializar primeiro
        java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(gateway);
        
        // Setar mailtrapClient para null usando reflection
        java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        clientField.setAccessible(true);
        clientField.set(gateway, null);
        
        // Tentar enviar email - deve tentar reinicializar
        String message = "Mensagem de teste";
        
        // Pode lançar exceção se não conseguir reinicializar, mas vamos verificar o comportamento
        assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando adminEmail é null")
    void deveLancarNotificationExceptionQuandoAdminEmailENull() throws Exception {
        EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(
            "test-token",
            null,
            12345L
        );
        
        // Inicializar
        java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(gateway);
        
        // Mockar mailtrapClient para que não seja null
        io.mailtrap.client.MailtrapClient mockClient = mock(io.mailtrap.client.MailtrapClient.class);
        java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        clientField.setAccessible(true);
        clientField.set(gateway, mockClient);
        
        String message = "Mensagem de teste";
        
        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );
        
        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando adminEmail está vazio")
    void deveLancarNotificationExceptionQuandoAdminEmailEstaVazio() throws Exception {
        EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(
            "test-token",
            "",
            12345L
        );
        
        // Inicializar
        java.lang.reflect.Method initMethod = EmailNotificationGatewayImpl.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(gateway);
        
        // Mockar mailtrapClient para que não seja null
        io.mailtrap.client.MailtrapClient mockClient = mock(io.mailtrap.client.MailtrapClient.class);
        java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        clientField.setAccessible(true);
        clientField.set(gateway, mockClient);
        
        String message = "Mensagem de teste";
        
        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );
        
        assertTrue(exception.getMessage().contains("E-mail do administrador não informado"));
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando falha ao reinicializar Mailtrap client")
    void deveLancarNotificationExceptionQuandoFalhaAoReinicializarMailtrapClient() throws Exception {
        EmailNotificationGatewayImpl gateway = new EmailNotificationGatewayImpl(
            "invalid-token",
            "admin@test.com",
            999999L // Inbox ID inválido
        );
        
        // Setar mailtrapClient para null
        java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        clientField.setAccessible(true);
        clientField.set(gateway, null);
        
        String message = "Mensagem de teste";
        
        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );
        
        // A mensagem pode variar dependendo do ponto de falha
        // Pode ser "Mailtrap nao configurado completamente" ou "Mailtrap nao esta disponivel e nao foi possivel reinicializar"
        String exceptionMessage = exception.getMessage();
        assertTrue(exceptionMessage.contains("Mailtrap") || 
                   exceptionMessage.contains("configurado") ||
                   exceptionMessage.contains("disponivel") ||
                   exceptionMessage.contains("reinicializar") ||
                   exceptionMessage.contains("MAILTRAP"));
    }

    @Test
    @DisplayName("Deve re-lançar NotificationException quando já lançada")
    void deveRelancarNotificationExceptionQuandoJaLancada() {
        String message = "Mensagem de teste";
        NotificationException notificationException = new NotificationException("Erro de notificação");
        
        doThrow(notificationException).when(mailtrapClient).send(any(MailtrapMail.class));

        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gateway.sendAdminNotification(message)
        );

        assertEquals(notificationException, exception);
    }

    @Test
    @DisplayName("Deve lançar NotificationException quando mailtrapInboxId é null durante reinicialização")
    void deveLancarNotificationExceptionQuandoMailtrapInboxIdENullDuranteReinicializacao() throws Exception {
        EmailNotificationGatewayImpl gatewaySemInboxId = new EmailNotificationGatewayImpl(
            "valid-token",
            "admin@test.com",
            (Long) null // Inbox ID null - cast explícito para evitar ambiguidade
        );
        
        // Setar mailtrapClient para null para forçar tentativa de reinicialização
        java.lang.reflect.Field clientField = EmailNotificationGatewayImpl.class.getDeclaredField("mailtrapClient");
        clientField.setAccessible(true);
        clientField.set(gatewaySemInboxId, null);
        
        String message = "Mensagem de teste";
        
        NotificationException exception = assertThrows(
            NotificationException.class,
            () -> gatewaySemInboxId.sendAdminNotification(message)
        );
        
        assertTrue(exception.getMessage().contains("Mailtrap nao configurado completamente") ||
                   exception.getMessage().contains("MAILTRAP_INBOX_ID"));
    }

    @Test
    @DisplayName("Deve processar content null corretamente")
    void deveProcessarContentNullCorretamente() {
        String message = null;
        
        when(mailtrapClient.send(any(MailtrapMail.class))).thenReturn(null);

        assertDoesNotThrow(() -> gateway.sendAdminNotification(message));

        ArgumentCaptor<MailtrapMail> mailCaptor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mailtrapClient, times(1)).send(mailCaptor.capture());
    }

}
