package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.requests.FeedbackRequest;
import br.com.fiap.postech.feedback.application.dtos.responses.FeedbackResponse;
import br.com.fiap.postech.feedback.domain.entities.Feedback;
import br.com.fiap.postech.feedback.domain.exceptions.FeedbackDomainException;
import br.com.fiap.postech.feedback.domain.exceptions.NotificationException;
import br.com.fiap.postech.feedback.domain.gateways.FeedbackGateway;
import br.com.fiap.postech.feedback.domain.gateways.NotificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para CreateFeedbackUseCaseImpl")
class CreateFeedbackUseCaseImplTest {

    @Mock
    private FeedbackGateway feedbackGateway;

    @Mock
    private NotificationGateway notificationGateway;

    @InjectMocks
    private CreateFeedbackUseCaseImpl createFeedbackUseCase;

    private FeedbackRequest requestValido;
    private FeedbackRequest requestCritico;

    @BeforeEach
    void setUp() {
        requestValido = new FeedbackRequest(
            "Aula muito boa",
            7,
            "MEDIUM"
        );

        requestCritico = new FeedbackRequest(
            "Aula muito ruim",
            2,
            "HIGH"
        );
    }

    @Test
    @DisplayName("Deve criar feedback com sucesso")
    void deveCriarFeedbackComSucesso() {
        FeedbackResponse response = createFeedbackUseCase.execute(requestValido);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(7, response.score());
        assertEquals("Aula muito boa", response.description());
        assertNotNull(response.createdAt());

        verify(feedbackGateway, times(1)).save(any(Feedback.class));
        verify(notificationGateway, never()).publishCritical(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve criar feedback crítico e enviar notificação")
    void deveCriarFeedbackCriticoEEnviarNotificacao() {
        FeedbackResponse response = createFeedbackUseCase.execute(requestCritico);

        assertNotNull(response);
        assertEquals(2, response.score());

        verify(feedbackGateway, times(1)).save(any(Feedback.class));
        verify(notificationGateway, times(1)).publishCritical(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve criar feedback com urgência padrão LOW quando não informada")
    void deveCriarFeedbackComUrgenciaPadrao() {
        FeedbackRequest requestSemUrgencia = new FeedbackRequest(
            "Aula boa",
            5,
            null
        );

        FeedbackResponse response = createFeedbackUseCase.execute(requestSemUrgencia);

        assertNotNull(response);
        verify(feedbackGateway, times(1)).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando descrição é nula")
    void deveLancarExcecaoQuandoDescricaoENula() {
        FeedbackRequest requestInvalido = new FeedbackRequest(
            null,
            5,
            "LOW"
        );

        FeedbackDomainException exception = assertThrows(
            FeedbackDomainException.class,
            () -> createFeedbackUseCase.execute(requestInvalido)
        );

        assertEquals("Descrição é obrigatória", exception.getMessage());
        verify(feedbackGateway, never()).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando descrição é vazia")
    void deveLancarExcecaoQuandoDescricaoEVazia() {
        FeedbackRequest requestInvalido = new FeedbackRequest(
            "   ",
            5,
            "LOW"
        );

        FeedbackDomainException exception = assertThrows(
            FeedbackDomainException.class,
            () -> createFeedbackUseCase.execute(requestInvalido)
        );

        assertEquals("Descrição é obrigatória", exception.getMessage());
        verify(feedbackGateway, never()).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando score é nulo")
    void deveLancarExcecaoQuandoScoreENulo() {
        FeedbackRequest requestInvalido = new FeedbackRequest(
            "Descrição válida",
            null,
            "LOW"
        );

        FeedbackDomainException exception = assertThrows(
            FeedbackDomainException.class,
            () -> createFeedbackUseCase.execute(requestInvalido)
        );

        assertEquals("Nota é obrigatória", exception.getMessage());
        verify(feedbackGateway, never()).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Não deve falhar quando notificação crítica falha")
    void naoDeveFalharQuandoNotificacaoCriticaFalha() {
        doThrow(new NotificationException("Erro ao enviar notificação"))
            .when(notificationGateway).publishCritical(any(Feedback.class));

        FeedbackResponse response = createFeedbackUseCase.execute(requestCritico);

        assertNotNull(response);
        verify(feedbackGateway, times(1)).save(any(Feedback.class));
        verify(notificationGateway, times(1)).publishCritical(any(Feedback.class));
    }

    @Test
    @DisplayName("Não deve falhar quando notificação crítica lança exceção genérica")
    void naoDeveFalharQuandoNotificacaoCriticaLancaExcecaoGenerica() {
        doThrow(new RuntimeException("Erro inesperado"))
            .when(notificationGateway).publishCritical(any(Feedback.class));

        FeedbackResponse response = createFeedbackUseCase.execute(requestCritico);

        assertNotNull(response);
        verify(feedbackGateway, times(1)).save(any(Feedback.class));
        verify(notificationGateway, times(1)).publishCritical(any(Feedback.class));
    }
}
