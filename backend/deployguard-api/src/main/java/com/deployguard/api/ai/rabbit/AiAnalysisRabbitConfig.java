package com.deployguard.api.ai.rabbit;

import com.deployguard.api.ai.AiServiceUnavailableException;
import com.deployguard.api.ai.job.AiAnalysisJobFailureRecoverer;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

@Configuration
public class AiAnalysisRabbitConfig {

    @Bean
    public Queue aiAnalysisQueue() {
        return QueueBuilder.durable(AiAnalysisRabbitProperties.QUEUE)
                .deadLetterExchange(AiAnalysisRabbitProperties.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AiAnalysisRabbitProperties.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public TopicExchange aiAnalysisExchange() {
        return new TopicExchange(AiAnalysisRabbitProperties.EXCHANGE, true, false);
    }

    @Bean
    public Binding aiAnalysisBinding(Queue aiAnalysisQueue, TopicExchange aiAnalysisExchange) {
        return BindingBuilder
                .bind(aiAnalysisQueue)
                .to(aiAnalysisExchange)
                .with(AiAnalysisRabbitProperties.ROUTING_KEY);
    }

    @Bean
    public Queue aiAnalysisDeadLetterQueue() {
        return QueueBuilder.durable(AiAnalysisRabbitProperties.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange aiAnalysisDeadLetterExchange() {
        return new DirectExchange(AiAnalysisRabbitProperties.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding aiAnalysisDeadLetterBinding(Queue aiAnalysisDeadLetterQueue, DirectExchange aiAnalysisDeadLetterExchange) {
        return BindingBuilder
                .bind(aiAnalysisDeadLetterQueue)
                .to(aiAnalysisDeadLetterExchange)
                .with(AiAnalysisRabbitProperties.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Only transient AI-service failures are retried; anything else (bad payload, missing
     * deployment) fails immediately. Causes are traversed because the listener container
     * wraps exceptions in ListenerExecutionFailedException.
     */
    public static SimpleRetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(maxAttempts, Map.of(AiServiceUnavailableException.class, true), true, false);
    }

    @Bean
    public Advice aiAnalysisRetryInterceptor(
            AiAnalysisJobFailureRecoverer recoverer,
            @Value("${deployguard.ai-analysis.max-attempts:3}") int maxAttempts,
            @Value("${deployguard.ai-analysis.initial-backoff-ms:1000}") long initialBackoffMs,
            @Value("${deployguard.ai-analysis.max-backoff-ms:10000}") long maxBackoffMs) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .backOffOptions(initialBackoffMs, 2.0, maxBackoffMs)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter,
            Advice aiAnalysisRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setAdviceChain(aiAnalysisRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
