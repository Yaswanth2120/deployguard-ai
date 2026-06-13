package com.deployguard.api.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.ai.AiIncidentAnalysisService;
import com.deployguard.api.deployment.Deployment;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiAnalysisJobWorkerTest {

    @Mock
    private AiAnalysisJobRepository aiAnalysisJobRepository;

    @Mock
    private AiIncidentAnalysisService aiIncidentAnalysisService;

    @Test
    void processMarksJobCompletedWhenAnalysisSucceeds() {
        AiAnalysisJob job = job();
        UUID jobId = job.getId();
        UUID deploymentId = job.getDeployment().getId();
        when(aiAnalysisJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker().process(new AiAnalysisJobMessage(jobId, deploymentId));

        assertThat(job.getStatus()).isEqualTo(AiAnalysisJobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getErrorMessage()).isNull();
        verify(aiIncidentAnalysisService).analyzeDeployment(deploymentId);

        ArgumentCaptor<AiAnalysisJob> jobCaptor = ArgumentCaptor.forClass(AiAnalysisJob.class);
        verify(aiAnalysisJobRepository, org.mockito.Mockito.times(2)).save(jobCaptor.capture());
        assertThat(jobCaptor.getAllValues().getLast().getStatus()).isEqualTo(AiAnalysisJobStatus.COMPLETED);
    }

    @Test
    void processMarksJobFailedWhenAnalysisFails() {
        AiAnalysisJob job = job();
        UUID jobId = job.getId();
        UUID deploymentId = job.getDeployment().getId();
        when(aiAnalysisJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("AI service unavailable"))
                .when(aiIncidentAnalysisService)
                .analyzeDeployment(deploymentId);

        worker().process(new AiAnalysisJobMessage(jobId, deploymentId));

        assertThat(job.getStatus()).isEqualTo(AiAnalysisJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("AI service unavailable");
        assertThat(job.getCompletedAt()).isNull();
        verify(aiAnalysisJobRepository, org.mockito.Mockito.times(2)).save(any(AiAnalysisJob.class));
    }

    private AiAnalysisJobWorker worker() {
        return new AiAnalysisJobWorker(aiAnalysisJobRepository, aiIncidentAnalysisService);
    }

    private AiAnalysisJob job() {
        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(UUID.randomUUID());
        AiAnalysisJob job = new AiAnalysisJob(deployment);
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        return job;
    }
}
