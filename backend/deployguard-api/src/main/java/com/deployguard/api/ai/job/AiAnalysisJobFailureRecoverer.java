package com.deployguard.api.ai.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import com.deployguard.api.ai.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Runs once retries are exhausted: marks the job FAILED so clients stop polling, then rejects
 * the message without requeue so RabbitMQ routes it to the dead-letter queue for inspection
 * and manual replay.
 */
@Component
public class AiAnalysisJobFailureRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisJobFailureRecoverer.class);

    private final AiAnalysisJobRepository aiAnalysisJobRepository;
    private final ObjectMapper objectMapper;

    public AiAnalysisJobFailureRecoverer(AiAnalysisJobRepository aiAnalysisJobRepository, ObjectMapper objectMapper) {
        this.aiAnalysisJobRepository = aiAnalysisJobRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        Throwable root = domainCause(cause);
        try {
            // Decode the body directly: the converter only trusts java.* type headers.
            AiAnalysisJobMessage jobMessage = objectMapper.readValue(message.getBody(), AiAnalysisJobMessage.class);
            aiAnalysisJobRepository.findById(jobMessage.jobId()).ifPresent(job -> {
                job.markFailed("Failed after " + job.getAttempts() + " attempt(s): " + describe(root));
                aiAnalysisJobRepository.save(job);
            });
        } catch (IOException | RuntimeException ex) {
            log.error("Could not mark AI analysis job failed before dead-lettering", ex);
        }
        throw new AmqpRejectAndDontRequeueException("AI analysis retries exhausted; dead-lettering message", root);
    }

    /** Prefer the AI-service error over listener wrappers and low-level I/O exceptions. */
    private static Throwable domainCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            if (current instanceof AiServiceUnavailableException) {
                return current;
            }
            last = current;
            current = current.getCause() == current ? null : current.getCause();
        }
        return last;
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
