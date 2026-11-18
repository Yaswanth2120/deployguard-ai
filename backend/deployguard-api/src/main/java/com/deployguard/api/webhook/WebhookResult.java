package com.deployguard.api.webhook;

import java.util.UUID;

public record WebhookResult(String event, String outcome, UUID entityId) {

    public static WebhookResult ignored(String event, String reason) {
        return new WebhookResult(event, "ignored: " + reason, null);
    }
}
