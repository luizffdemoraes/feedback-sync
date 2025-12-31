package br.com.fiap.postech.feedback.infrastructure.handlers;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;
import br.com.fiap.postech.feedback.application.usecases.GenerateWeeklyReportUseCase;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes para WeeklyReportFunction")
class WeeklyReportFunctionTest {

    @Mock
    private GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    @Mock
    private ExecutionContext executionContext;

    private WeeklyReportFunction function;

    @BeforeEach
    void setUp() {
        function = new WeeklyReportFunction(generateWeeklyReportUseCase);
    }

    @Test
    @DisplayName("Deve gerar relatório semanal com sucesso")
    void deveGerarRelatorioSemanalComSucesso() {
        String timerInfo = "{\"ScheduleStatus\":{\"Last\":\"2024-01-15T08:00:00Z\"}}";
        
        WeeklyReportResponse report = new WeeklyReportResponse();
        report.setPeriodoInicio(Instant.now().minusSeconds(604800));
        report.setPeriodoFim(Instant.now());
        report.setTotalAvaliacoes(10);
        report.setMediaAvaliacoes(7.5);
        report.setReportUrl("https://storage.blob.core.windows.net/reports/report.json");
        
        when(generateWeeklyReportUseCase.execute()).thenReturn(report);

        assertDoesNotThrow(() -> function.run(timerInfo, executionContext));

        verify(generateWeeklyReportUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("Deve lançar RuntimeException quando ocorre erro ao gerar relatório")
    void deveLancarRuntimeExceptionQuandoOcorreErroAoGerarRelatorio() {
        String timerInfo = "{\"ScheduleStatus\":{\"Last\":\"2024-01-15T08:00:00Z\"}}";
        RuntimeException exception = new RuntimeException("Erro ao gerar relatório");
        
        when(generateWeeklyReportUseCase.execute()).thenThrow(exception);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> function.run(timerInfo, executionContext)
        );

        assertTrue(thrown.getMessage().contains("Falha ao gerar relatório semanal"));
        assertEquals(exception, thrown.getCause());
        verify(generateWeeklyReportUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("Deve processar timerInfo corretamente")
    void deveProcessarTimerInfoCorretamente() {
        String timerInfo = "{\"ScheduleStatus\":{\"Last\":\"2024-01-15T08:00:00Z\",\"Next\":\"2024-01-22T08:00:00Z\"}}";
        
        WeeklyReportResponse report = new WeeklyReportResponse();
        report.setPeriodoInicio(Instant.now().minusSeconds(604800));
        report.setPeriodoFim(Instant.now());
        report.setTotalAvaliacoes(5);
        report.setMediaAvaliacoes(6.0);
        report.setReportUrl("https://storage.blob.core.windows.net/reports/report.json");
        
        when(generateWeeklyReportUseCase.execute()).thenReturn(report);

        function.run(timerInfo, executionContext);

        verify(generateWeeklyReportUseCase, times(1)).execute();
    }
}
