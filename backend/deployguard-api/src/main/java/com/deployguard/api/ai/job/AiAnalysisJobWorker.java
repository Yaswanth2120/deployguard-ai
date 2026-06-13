package com.deployguard.api.ai.job;

import com.deployguard.api.ai.AiIncidentAnalysisService;
import com.deployguard.api.ai.rabbit.AiAnalysisRabbitProperties;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void process(AiAnalysisJobMessage message) {
        AiAnalysisJob job = aiAnalysisJobRepository.findById(message.jobId())
                .orElseThrow(() -> new AiAnalysisJobNotFoundException(message.jobId()));

        job.markProcessing();
        aiAnalysisJobRepository.save(job);

        try {
            aiIncidentAnalysisService.analyzeDeployment(message.deploymentId());
            job.markCompleted();
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
