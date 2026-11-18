package com.deployguard.api.ai.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deployguard.api.ai.AiServiceUnavailableException;
import com.deployguard.api.ai.job.AiAnalysisJob;
import com.deployguard.api.ai.job.AiAnalysisJobFailureRecoverer;
import com.deployguard.api.ai.job.AiAnalysisJobMessage;
import com.deployguard.api.ai.job.AiAnalysisJobRepository;
import com.deployguard.api.ai.job.AiAnalysisJobStatus;
import com.deployguard.api.deployment.Deployment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AiAnalysisRetryTest {

    @Test
    void transientFailuresAreRetriedUpToMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RetryTemplate template = RetryTemplate.builder().customPolicy(AiAnalysisRabbitConfig.retryPolicy(3)).noBackoff().build();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw new ListenerExecutionFailedException("listener failed",
                    new AiServiceUnavailableException("AI service is unavailable.", null));
        })).isInstanceOf(ListenerExecutionFailedException.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void permanentFailuresAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        RetryTemplate template = RetryTemplate.builder().customPolicy(AiAnalysisRabbitConfig.retryPolicy(3)).noBackoff().build();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw new ListenerExecutionFailedException("listener failed", new IllegalArgumentException("bad payload"));
        })).isInstanceOf(ListenerExecutionFailedException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void recovererMarksJobFailedAndDeadLetters() {
        AiAnalysisJobRepository repository = mock(AiAnalysisJobRepository.class);
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        Deployment deployment = mock(Deployment.class);
        AiAnalysisJob job = new AiAnalysisJob(deployment);
        UUID jobId = UUID.randomUUID();
        ReflectionTestUtils.setField(job, "id", jobId);
        job.startAttempt();
        job.startAttempt();
        job.startAttempt();
        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        Message message = converter.toMessage(new AiAnalysisJobMessage(jobId, UUID.randomUUID()), new MessageProperties());
        AiAnalysisJobFailureRecoverer recoverer = new AiAnalysisJobFailureRecoverer(repository, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThatThrownBy(() -> recoverer.recover(message, new ListenerExecutionFailedException("x",
                new AiServiceUnavailableException("AI service returned status 503", null))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(job.getStatus()).isEqualTo(AiAnalysisJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Failed after 3 attempt(s): AI service returned status 503");
    }
}
