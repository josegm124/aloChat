package com.alo.orchestrator.messaging;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.events.DatasetAnalysisCompletedEvent;
import com.alo.contracts.events.DocumentAnalysisCompletedEvent;
import com.alo.orchestrator.api.AssessmentConsolidationRequest;
import com.alo.orchestrator.service.AssessmentConsolidationService;
import com.alo.orchestrator.service.ReportGenerationRequestedEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssessmentAnalysisCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AssessmentAnalysisCoordinator.class);
    private final Map<String, AnalysisCorrelationState> states = new ConcurrentHashMap<>();
    private final AssessmentConsolidationService assessmentConsolidationService;
    private final ReportGenerationRequestedEventPublisher reportGenerationRequestedEventPublisher;

    public AssessmentAnalysisCoordinator(
            AssessmentConsolidationService assessmentConsolidationService,
            ReportGenerationRequestedEventPublisher reportGenerationRequestedEventPublisher
    ) {
        this.assessmentConsolidationService = assessmentConsolidationService;
        this.reportGenerationRequestedEventPublisher = reportGenerationRequestedEventPublisher;
    }

    public void onDocumentCompleted(DocumentAnalysisCompletedEvent event) {
        log.info(
                "orchestrator received document result assessmentId={} traceId={}",
                event.assessment().assessmentId(),
                event.traceId()
        );
        AnalysisCorrelationState updated = states.compute(event.assessment().assessmentId(), (key, current) -> {
            AnalysisCorrelationState base = current == null
                    ? new AnalysisCorrelationState(event.assessment(), event.regulatoryProfile(), null, null)
                    : current;
            return base.withDocument(event.result());
        });
        tryPublishIfReady(event.traceId(), updated);
    }

    public void onDatasetCompleted(DatasetAnalysisCompletedEvent event) {
        log.info(
                "orchestrator received dataset result assessmentId={} traceId={}",
                event.assessment().assessmentId(),
                event.traceId()
        );
        AnalysisCorrelationState updated = states.compute(event.assessment().assessmentId(), (key, current) -> {
            AnalysisCorrelationState base = current == null
                    ? new AnalysisCorrelationState(event.assessment(), event.regulatoryProfile(), null, null)
                    : current;
            return base.withDataset(event.result());
        });
        tryPublishIfReady(event.traceId(), updated);
    }

    private void tryPublishIfReady(String traceId, AnalysisCorrelationState state) {
        if (!state.isReady()) {
            log.info(
                    "orchestrator waiting for remaining analysis assessmentId={} hasDocument={} hasDataset={} datasetProvided={}",
                    state.assessment().assessmentId(),
                    state.documentAnalysisResult() != null,
                    state.datasetAnalysisResult() != null,
                    state.assessment().datasetProvided()
            );
            return;
        }
        log.info(
                "orchestrator consolidating assessmentId={} traceId={}",
                state.assessment().assessmentId(),
                traceId
        );
        ConsolidatedAssessmentResult consolidated = assessmentConsolidationService.consolidate(
                new AssessmentConsolidationRequest(
                        state.assessment(),
                        state.regulatoryProfile(),
                        state.documentAnalysisResult(),
                        state.datasetAnalysisResult()
                )
        );
        states.remove(state.assessment().assessmentId());
        reportGenerationRequestedEventPublisher.publish(traceId, consolidated);
        log.info(
                "report generation requested assessmentId={} traceId={} findings={} evidence={}",
                consolidated.assessmentId(),
                traceId,
                consolidated.findings().size(),
                consolidated.evidenceItems().size()
        );
    }
}
