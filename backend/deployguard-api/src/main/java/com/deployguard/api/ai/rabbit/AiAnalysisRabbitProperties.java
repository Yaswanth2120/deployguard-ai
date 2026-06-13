package com.deployguard.api.ai.rabbit;

public final class AiAnalysisRabbitProperties {

    public static final String QUEUE = "deployment.ai.analysis.queue";
    public static final String EXCHANGE = "deployment.ai.analysis.exchange";
    public static final String ROUTING_KEY = "deployment.ai.analysis.requested";

    private AiAnalysisRabbitProperties() {
    }
}
