package com.deployguard.api.ai;

import com.deployguard.api.deployment.Deployment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_incident_summaries")
public class AiIncidentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "likely_root_cause", nullable = false, columnDefinition = "TEXT")
    private String likelyRootCause;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommended_actions", nullable = false, columnDefinition = "TEXT")
    private String recommendedActions;

    @Column(nullable = false, length = 30)
    private String severity;

    @Column(nullable = false, length = 30)
    private String confidence;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AiIncidentSummary() {
    }

    public AiIncidentSummary(
            Deployment deployment,
            String summary,
            String likelyRootCause,
            String evidence,
            String recommendedActions,
            String severity,
            String confidence,
            String modelName) {
        this.deployment = deployment;
        this.summary = summary;
        this.likelyRootCause = likelyRootCause;
        this.evidence = evidence;
        this.recommendedActions = recommendedActions;
        this.severity = severity;
        this.confidence = confidence;
        this.modelName = modelName;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLikelyRootCause() {
        return likelyRootCause;
    }

    public void setLikelyRootCause(String likelyRootCause) {
        this.likelyRootCause = likelyRootCause;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(String recommendedActions) {
        this.recommendedActions = recommendedActions;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
