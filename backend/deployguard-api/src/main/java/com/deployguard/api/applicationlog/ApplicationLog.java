package com.deployguard.api.applicationlog;

import com.deployguard.api.deployment.Deployment;
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
@Table(name = "application_logs")
public class ApplicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id")
    private Deployment deployment;

    @Column(name = "service_name", nullable = false, length = 120)
    private String serviceName;

    @Column(nullable = false, length = 30)
    private String level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ApplicationLog() {
    }

    public ApplicationLog(
            Project project,
            Deployment deployment,
            String serviceName,
            String level,
            String message,
            OffsetDateTime timestamp
    ) {
        this.project = project;
        this.deployment = deployment;
        this.serviceName = serviceName;
        this.level = level;
        this.message = message;
        this.timestamp = timestamp;
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

    public Deployment getDeployment() {
        return deployment;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
