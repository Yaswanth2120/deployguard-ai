package com.deployguard.api.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubWebhookDeliveryRepository extends JpaRepository<GithubWebhookDelivery, String> {
}
