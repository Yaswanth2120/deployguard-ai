package com.deployguard.api.deployment;

import com.deployguard.api.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "commit_sha", nullable = false, length = 120)
    private String commitSha;

    @Column(nullable = false, length = 120)
    private String branch;

    @Column(nullable = false, length = 50)
    private String environment;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "deployed_by", nullable = false, length = 120)
    private String deployedBy;

    @Column(name = "deployed_at", nullable = false)
    private OffsetDateTime deployedAt;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "risk_level", nullable = false, length = 30)
    private String riskLevel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Deployment() {
    }

    public Deployment(
            Project project,
            String commitSha,
            String branch,
            String environment,
            String status,
            String deployedBy,
            OffsetDateTime deployedAt
    ) {
        this.project = project;
        this.commitSha = commitSha;
        this.branch = branch;
        this.environment = environment;
        this.status = status;
        this.deployedBy = deployedBy;
        this.deployedAt = deployedAt;
        this.riskScore = 0;
        this.riskLevel = "LOW";
    }

    public void updateRisk(Integer riskScore, String riskLevel) {
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (riskScore == null) {
            riskScore = 0;
        }
        if (riskLevel == null) {
            riskLevel = "LOW";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getBranch() {
        return branch;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getStatus() {
        return status;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public OffsetDateTime getDeployedAt() {
        return deployedAt;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
