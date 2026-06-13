package com.deployguard.api.ai.job;

import com.deployguard.api.ai.job.dto.AiAnalysisJobResponse;
import com.deployguard.api.ai.rabbit.AiAnalysisRabbitProperties;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisJobService {

    private final DeploymentRepository deploymentRepository;
    private final AiAnalysisJobRepository aiAnalysisJobRepository;
    private final RabbitTemplate rabbitTemplate;

    public AiAnalysisJobService(
            DeploymentRepository deploymentRepository,
            AiAnalysisJobRepository aiAnalysisJobRepository,
            RabbitTemplate rabbitTemplate) {
        this.deploymentRepository = deploymentRepository;
        this.aiAnalysisJobRepository = aiAnalysisJobRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public AiAnalysisJobResponse createJob(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        AiAnalysisJob job = aiAnalysisJobRepository.save(new AiAnalysisJob(deployment));

        try {
            rabbitTemplate.convertAndSend(
                    AiAnalysisRabbitProperties.EXCHANGE,
                    AiAnalysisRabbitProperties.ROUTING_KEY,
                    new AiAnalysisJobMessage(job.getId(), deploymentId));
        } catch (AmqpException ex) {
            throw new AiAnalysisJobPublishException("Failed to publish AI analysis job.", ex);
        }

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public AiAnalysisJobResponse getJobById(UUID jobId) {
        return aiAnalysisJobRepository.findById(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new AiAnalysisJobNotFoundException(jobId));
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisJobResponse> getJobsByDeploymentId(UUID deploymentId) {
        if (!deploymentRepository.existsById(deploymentId)) {
            throw new DeploymentNotFoundException(deploymentId);
        }

        return aiAnalysisJobRepository.findByDeploymentId(deploymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    AiAnalysisJobResponse toResponse(AiAnalysisJob job) {
        return new AiAnalysisJobResponse(
                job.getId(),
                job.getDeployment().getId(),
                job.getStatus(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }
}
