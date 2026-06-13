package com.deployguard.api.cirun;

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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ci_runs")
public class CiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "commit_sha", nullable = false, length = 120)
    private String commitSha;

    @Column(nullable = false, length = 80)
    private String provider;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "failed_tests", nullable = false)
    private Integer failedTests;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CiRun() {
    }

    public CiRun(
            Project project,
            String commitSha,
            String provider,
            String status,
            Integer durationSeconds,
            Integer failedTests
    ) {
        this.project = project;
        this.commitSha = commitSha;
        this.provider = provider;
        this.status = status;
        this.durationSeconds = durationSeconds;
        this.failedTests = failedTests;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
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

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Integer getFailedTests() {
        return failedTests;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
