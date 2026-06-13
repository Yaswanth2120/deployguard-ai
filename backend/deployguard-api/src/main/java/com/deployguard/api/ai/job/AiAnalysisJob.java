package com.deployguard.api.ai.job;

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
@Table(name = "ai_analysis_jobs")
public class AiAnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public AiAnalysisJob() {
    }

    public AiAnalysisJob(Deployment deployment) {
        OffsetDateTime now = OffsetDateTime.now();
        this.deployment = deployment;
        this.status = AiAnalysisJobStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessing() {
        this.status = AiAnalysisJobStatus.PROCESSING;
        this.errorMessage = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markCompleted() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = AiAnalysisJobStatus.COMPLETED;
        this.errorMessage = null;
        this.updatedAt = now;
        this.completedAt = now;
    }

    public void markFailed(String errorMessage) {
        this.status = AiAnalysisJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = OffsetDateTime.now();
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
