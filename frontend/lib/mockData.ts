export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";

export type Project = {
  id: string;
  name: string;
  serviceName: string;
  repo: string;
  owner: string;
  deployments: number;
  lastDeployment: string;
};

export type Deployment = {
  id: string;
  projectId: string;
  serviceName: string;
  environment: string;
  status: string;
  branch: string;
  commitSha: string;
  deployedBy: string;
  deployedAt: string;
  riskScore: number;
  riskLevel: RiskLevel;
  aiAnalysisStatus: "COMPLETED" | "PENDING" | "FAILED";
};

export type AiIncidentSummary = {
  summary: string;
  likelyRootCause: string;
  evidence: string[];
  recommendedActions: string[];
  severity: RiskLevel;
  confidence: RiskLevel;
};

export const projects: Project[] = [
  {
    id: "proj-api",
    name: "DeployGuard API",
    serviceName: "deployguard-api",
    repo: "github.com/acme/deployguard-api",
    owner: "Platform",
    deployments: 18,
    lastDeployment: "2026-06-12 17:44",
  },
  {
    id: "proj-billing",
    name: "Billing Worker",
    serviceName: "billing-worker",
    repo: "github.com/acme/billing-worker",
    owner: "Revenue",
    deployments: 9,
    lastDeployment: "2026-06-12 15:18",
  },
  {
    id: "proj-web",
    name: "Customer Portal",
    serviceName: "customer-portal",
    repo: "github.com/acme/customer-portal",
    owner: "Experience",
    deployments: 14,
    lastDeployment: "2026-06-11 22:09",
  },
];

export const deployments: Deployment[] = [
  {
    id: "dep-1042",
    projectId: "proj-api",
    serviceName: "deployguard-api",
    environment: "prod",
    status: "DEPLOYED",
    branch: "main",
    commitSha: "8f4a91c",
    deployedBy: "maya",
    deployedAt: "2026-06-12 17:44",
    riskScore: 82,
    riskLevel: "HIGH",
    aiAnalysisStatus: "COMPLETED",
  },
  {
    id: "dep-1041",
    projectId: "proj-billing",
    serviceName: "billing-worker",
    environment: "staging",
    status: "DEPLOYED",
    branch: "release/2026-06",
    commitSha: "7bc129e",
    deployedBy: "noah",
    deployedAt: "2026-06-12 15:18",
    riskScore: 46,
    riskLevel: "MEDIUM",
    aiAnalysisStatus: "PENDING",
  },
  {
    id: "dep-1040",
    projectId: "proj-web",
    serviceName: "customer-portal",
    environment: "prod",
    status: "ROLLED_BACK",
    branch: "hotfix/session-timeout",
    commitSha: "13ac88d",
    deployedBy: "lina",
    deployedAt: "2026-06-11 22:09",
    riskScore: 91,
    riskLevel: "HIGH",
    aiAnalysisStatus: "COMPLETED",
  },
  {
    id: "dep-1039",
    projectId: "proj-api",
    serviceName: "deployguard-api",
    environment: "dev",
    status: "DEPLOYED",
    branch: "feature/audit-events",
    commitSha: "55d0fa2",
    deployedBy: "owen",
    deployedAt: "2026-06-11 11:32",
    riskScore: 18,
    riskLevel: "LOW",
    aiAnalysisStatus: "COMPLETED",
  },
];

export const ciSignals = [
  { label: "GitHub Actions", status: "FAILED", detail: "3 failed tests in payment-contract suite" },
  { label: "Duration", status: "SLOW", detail: "12m 42s, 38% slower than baseline" },
  { label: "Commit checks", status: "PASSED", detail: "Static analysis and dependency scan passed" },
];

export const logs = [
  { level: "ERROR", service: "deployguard-api", message: "Timeout from billing-api after 5000 ms" },
  { level: "WARN", service: "deployguard-api", message: "Retry queue depth exceeded normal threshold" },
  { level: "INFO", service: "deployguard-api", message: "Deployment marker received for commit 8f4a91c" },
];

export const aiSummary: AiIncidentSummary = {
  summary:
    "The deployment carries high incident risk due to failed CI tests and post-deploy timeout errors in the payment path.",
  likelyRootCause:
    "Contract changes in the API layer likely increased downstream billing latency under production traffic.",
  evidence: [
    "Deployment risk score is 82.",
    "CI reported failed payment-contract tests.",
    "Application logs include billing-api timeout errors after deployment.",
  ],
  recommendedActions: [
    "Hold additional rollout until billing latency is stable.",
    "Review contract changes in commit 8f4a91c.",
    "Prepare rollback if timeout rate continues above baseline.",
  ],
  severity: "HIGH",
  confidence: "MEDIUM",
};

export function getDeployment(id: string) {
  return deployments.find((deployment) => deployment.id === id) ?? deployments[0];
}
