package com.agenthub.agent.application;

import com.agenthub.agent.domain.evaluation.EvaluationReportView;

public interface OfflineEvaluationApplication {

    EvaluationReportView runEvaluationSuite(String modelOverride);

    EvaluationReportView getLatestReport();
}
