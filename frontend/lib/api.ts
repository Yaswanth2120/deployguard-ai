export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";

export type Project = {
  id: string;
  name: string;
  githubRepoUrl: string;
  serviceName: string;
  createdAt: string;
  updatedAt: string;
};

export type Deployment = {
  id: string;
  projectId: string;
  commitSha: string;
  branch: string;
  environment: string;
  status: string;
  deployedBy: string;
  deployedAt: string;
  riskScore: number;
  riskLevel: RiskLevel;
  createdAt: string;
  updatedAt: string;
};

export type ApplicationLog = {
  id: string;
  projectId: string;
  deploymentId: string | null;
  serviceName: string;
  level: string;
  message: string;
  timestamp: string;
  createdAt: string;
};

export type CiRun = {
  id: string;
  projectId: string;
  commitSha: string;
  provider: string;
  status: string;
  durationSeconds: number;
  failedTests: number;
  createdAt: string;
};

export type AiIncidentSummary = {
  id: string;
  deploymentId: string;
  summary: string;
  likelyRootCause: string;
  evidence: string;
  recommendedActions: string;
  severity: RiskLevel;
  confidence: RiskLevel;
  modelName: string;
  createdAt: string;
};

export type AiAnalysisJob = {
  id: string;
  deploymentId: string;
  status: string;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const error = await response.json();
      message = error.message ?? message;
    } catch {
      // Keep the generic HTTP message when the backend does not return JSON.
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export async function getProjects() {
  return request<Project[]>("/api/projects");
}

export async function getProject(projectId: string) {
  return request<Project>(`/api/projects/${projectId}`);
}

export async function getProjectDeployments(projectId: string) {
  return request<Deployment[]>(`/api/projects/${projectId}/deployments`);
}

export async function getAllDeployments() {
  const projects = await getProjects();
  const deploymentGroups = await Promise.all(projects.map((project) => getProjectDeployments(project.id)));
  return deploymentGroups.flat();
}

export async function getDeployment(deploymentId: string) {
  return request<Deployment>(`/api/deployments/${deploymentId}`);
}

export async function getDeploymentLogs(deploymentId: string) {
  return request<ApplicationLog[]>(`/api/deployments/${deploymentId}/logs`);
}

export async function getCiRunsForDeployment(deployment: Deployment) {
  return request<CiRun[]>(`/api/projects/${deployment.projectId}/ci-runs/commit/${deployment.commitSha}`);
}

export async function getAiSummaries(deploymentId: string) {
  return request<AiIncidentSummary[]>(`/api/deployments/${deploymentId}/ai-summaries`);
}

export async function getAiJobs(deploymentId: string) {
  return request<AiAnalysisJob[]>(`/api/deployments/${deploymentId}/ai-analysis/jobs`);
}

export async function recalculateRisk(deploymentId: string) {
  return request<Deployment>(`/api/deployments/${deploymentId}/risk-score/recalculate`, {
    method: "POST",
  });
}

export async function runSyncAiAnalysis(deploymentId: string) {
  return request<AiIncidentSummary>(`/api/deployments/${deploymentId}/ai-analysis`, {
    method: "POST",
  });
}

export async function queueAsyncAiAnalysis(deploymentId: string) {
  return request<AiAnalysisJob>(`/api/deployments/${deploymentId}/ai-analysis/jobs`, {
    method: "POST",
  });
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "Not available";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function parseJsonTextList(value: string | null | undefined): string[] {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [value];
  }
}
