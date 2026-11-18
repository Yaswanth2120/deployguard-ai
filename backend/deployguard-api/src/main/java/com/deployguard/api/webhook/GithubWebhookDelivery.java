package com.deployguard.api.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "github_webhook_deliveries")
public class GithubWebhookDelivery {

    @Id
    @Column(name = "delivery_id", length = 100)
    private String deliveryId;

    @Column(nullable = false, length = 60)
    private String event;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected GithubWebhookDelivery() {
    }

    public GithubWebhookDelivery(String deliveryId, String event) {
        this.deliveryId = deliveryId;
        this.event = event;
        this.receivedAt = OffsetDateTime.now();
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEvent() {
        return event;
    }
}
