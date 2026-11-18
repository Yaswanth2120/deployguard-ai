package com.deployguard.api.ai.rabbit;

public final class AiAnalysisRabbitProperties {

    /*
     * v2: the queue is declared with dead-letter arguments. RabbitMQ refuses to redeclare an
     * existing queue with different arguments, so the name changed instead of mutating the
     * original queue in place.
     */
    public static final String QUEUE = "deployment.ai.analysis.queue.v2";
    public static final String EXCHANGE = "deployment.ai.analysis.exchange";
    public static final String ROUTING_KEY = "deployment.ai.analysis.requested";

    public static final String DEAD_LETTER_EXCHANGE = "deployment.ai.analysis.dlx";
    public static final String DEAD_LETTER_QUEUE = "deployment.ai.analysis.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "deployment.ai.analysis.dead";

    private AiAnalysisRabbitProperties() {
    }
}
