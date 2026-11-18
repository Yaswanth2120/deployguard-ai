package com.deployguard.api.ai.job;

import com.deployguard.api.ai.AiIncidentAnalysisService;
import com.deployguard.api.ai.AiServiceUnavailableException;
import com.deployguard.api.ai.rabbit.AiAnalysisRabbitProperties;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumes AI analysis jobs.
 *
 * <p>Not transactional on purpose: each attempt's PROCESSING state and attempt counter must be
 * committed even when the attempt fails and the retry interceptor re-invokes this method.
 * Transient AI-service failures are rethrown for retry with backoff; after the last attempt
 * {@link AiAnalysisJobFailureRecoverer} marks the job FAILED and dead-letters the message.
 * Other failures are permanent and fail the job immediately.
 */
@Service
public class AiAnalysisJobWorker {

    private final AiAnalysisJobRepository aiAnalysisJobRepository;
    private final AiIncidentAnalysisService aiIncidentAnalysisService;

    public AiAnalysisJobWorker(
            AiAnalysisJobRepository aiAnalysisJobRepository,
            AiIncidentAnalysisService aiIncidentAnalysisService) {
        this.aiAnalysisJobRepository = aiAnalysisJobRepository;
        this.aiIncidentAnalysisService = aiIncidentAnalysisService;
    }

    @RabbitListener(queues = AiAnalysisRabbitProperties.QUEUE)
    public void process(AiAnalysisJobMessage message) {
        AiAnalysisJob job = aiAnalysisJobRepository.findById(message.jobId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        new AiAnalysisJobNotFoundException(message.jobId())));

        if (AiAnalysisJobStatus.COMPLETED.equals(job.getStatus())) {
            return; // duplicate delivery; analysis already stored
        }

        job.startAttempt();
        aiAnalysisJobRepository.save(job);

        try {
            aiIncidentAnalysisService.analyzeDeployment(message.deploymentId());
            job.markCompleted();
        } catch (AiServiceUnavailableException ex) {
            job.recordAttemptFailure(errorMessage(ex));
            aiAnalysisJobRepository.save(job);
            throw ex;
        } catch (RuntimeException ex) {
            job.markFailed(errorMessage(ex));
        }

        aiAnalysisJobRepository.save(job);
    }

    private String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
