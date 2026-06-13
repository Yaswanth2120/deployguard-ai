package com.deployguard.api.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.ai.job.dto.AiAnalysisJobResponse;
import com.deployguard.api.ai.rabbit.AiAnalysisRabbitProperties;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiAnalysisJobServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private AiAnalysisJobRepository aiAnalysisJobRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void createJobSavesPendingJobAndPublishesMessage() {
        Deployment deployment = deployment();
        UUID deploymentId = deployment.getId();
        UUID jobId = UUID.randomUUID();
        AiAnalysisJobService service = service();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(aiAnalysisJobRepository.save(any(AiAnalysisJob.class))).thenAnswer(invocation -> {
            AiAnalysisJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", jobId);
            return job;
        });

        AiAnalysisJobResponse response = service.createJob(deploymentId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.deploymentId()).isEqualTo(deploymentId);
        assertThat(response.status()).isEqualTo(AiAnalysisJobStatus.PENDING);

        ArgumentCaptor<AiAnalysisJobMessage> messageCaptor = ArgumentCaptor.forClass(AiAnalysisJobMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(AiAnalysisRabbitProperties.EXCHANGE),
                eq(AiAnalysisRabbitProperties.ROUTING_KEY),
                messageCaptor.capture());
        assertThat(messageCaptor.getValue().jobId()).isEqualTo(jobId);
        assertThat(messageCaptor.getValue().deploymentId()).isEqualTo(deploymentId);
    }

    @Test
    void createJobThrowsWhenDeploymentNotFound() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createJob(deploymentId))
                .isInstanceOf(DeploymentNotFoundException.class);
    }

    @Test
    void createJobThrowsWhenPublishFails() {
        Deployment deployment = deployment();
        UUID deploymentId = deployment.getId();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(aiAnalysisJobRepository.save(any(AiAnalysisJob.class))).thenAnswer(invocation -> {
            AiAnalysisJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
            return job;
        });
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(AiAnalysisJobMessage.class));

        assertThatThrownBy(() -> service().createJob(deploymentId))
                .isInstanceOf(AiAnalysisJobPublishException.class)
                .hasMessageContaining("Failed to publish AI analysis job");
    }

    @Test
    void getJobByIdReturnsJob() {
        AiAnalysisJob job = job();
        UUID jobId = job.getId();
        when(aiAnalysisJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        AiAnalysisJobResponse response = service().getJobById(jobId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(AiAnalysisJobStatus.PENDING);
    }

    @Test
    void getJobsByDeploymentIdReturnsJobs() {
        AiAnalysisJob job = job();
        UUID deploymentId = job.getDeployment().getId();
        when(deploymentRepository.existsById(deploymentId)).thenReturn(true);
        when(aiAnalysisJobRepository.findByDeploymentId(deploymentId)).thenReturn(List.of(job));

        List<AiAnalysisJobResponse> responses = service().getJobsByDeploymentId(deploymentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().deploymentId()).isEqualTo(deploymentId);
    }

    private AiAnalysisJobService service() {
        return new AiAnalysisJobService(deploymentRepository, aiAnalysisJobRepository, rabbitTemplate);
    }

    private AiAnalysisJob job() {
        AiAnalysisJob job = new AiAnalysisJob(deployment());
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        return job;
    }

    private Deployment deployment() {
        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(UUID.randomUUID());
        return deployment;
    }
}
