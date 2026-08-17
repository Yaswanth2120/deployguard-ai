# DeployGuard AI data model

```text
Project 1 ── * PullRequest 1 ── * CiRun
                     │
                     ├── * DeploymentEvent ── * LogEntry
                     │             │
                     │             └── 0..1 DeploymentEvent (rollback target)
                     └── * RiskScore
CiRun 0..1 ──────────────── RiskScore
DeploymentEvent 0..1 ────── RiskScore
```

`Project` is the existing tenancy boundary for a repository/service: `id`, `githubRepoUrl`, and `serviceName`.

| Entity | Fields |
| --- | --- |
| PullRequest | `id` (UUID), `projectId` (FK), `provider`, `providerPullRequestId`, `number`, `title`, `authorId`, `sourceBranch`, `targetBranch`, `commitSha`, `status`, `createdAt`, `updatedAt`, `mergedAt` |
| CiRun | `id` (UUID), `pullRequestId` (nullable FK), `projectId` (FK), `providerRunId`, `provider`, `commitSha`, `workflowName`, `status`, `startedAt`, `completedAt`, `failedTests`, `metadata` |
| DeploymentEvent | `id` (UUID), `pullRequestId` (nullable FK), `ciRunId` (nullable FK), `projectId` (FK), `commitSha`, `environment`, `version`, `status`, `deployedBy`, `startedAt`, `completedAt`, `rollbackOfDeploymentId` (nullable self-FK) |
| LogEntry | `id` (UUID), `deploymentEventId` (nullable FK), `projectId` (FK), `serviceName`, `environment`, `timestamp`, `severity`, `message` (redacted), `traceId`, `requestId`, `attributes` |
| RiskScore | `id` (UUID), `pullRequestId` (nullable FK), `ciRunId` (nullable FK), `deploymentEventId` (nullable FK), `score` (0–100), `riskLevel`, `status`, `factors`, `rationale`, `modelVersion`, `calculatedAt` |

Direct foreign keys are preferred. `projectId` and `commitSha` additionally support correlation for provider events that arrive out of order. Logs and webhook payloads must be redacted before persistence.
