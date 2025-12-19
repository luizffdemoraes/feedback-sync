package br.com.fiap.postech.feedback.application.usecases;

import br.com.fiap.postech.feedback.application.dtos.responses.WeeklyReportResponse;

public interface GenerateWeeklyReportUseCase {
    WeeklyReportResponse execute();
}
