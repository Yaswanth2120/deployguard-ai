"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import {
  AiAnalysisJob,
  AiIncidentSummary,
  ApplicationLog,
  CiRun,
  Deployment,
  formatDateTime,
  getAiJobs,
  getAiSummaries,
  getCiRunsForDeployment,
  getDeployment,
  getDeploymentLogs,
  getProject,
  parseJsonTextList,
  Project,
  queueAsyncAiAnalysis,
  recalculateRisk,
  runSyncAiAnalysis,
} from "@/lib/api";

type DetailState = {
  deployment: Deployment | null;
  project: Project | null;
  logs: ApplicationLog[];
  ciRuns: CiRun[];
  summaries: AiIncidentSummary[];
  jobs: AiAnalysisJob[];
};

type ActionKey = "risk" | "sync-ai" | "async-ai";

const initialState: DetailState = {
  deployment: null,
  project: null,
  logs: [],
  ciRuns: [],
  summaries: [],
  jobs: [],
};

const actionLabels: Record<ActionKey, string> = {
  risk: "Recalculating risk...",
  "sync-ai": "Running sync analysis...",
  "async-ai": "Queueing async job...",
};

export default function DeploymentDetailPage() {
  const params = useParams<{ id: string }>();
  const deploymentId = params.id;
  const [state, setState] = useState<DetailState>(initialState);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [activeAction, setActiveAction] = useState<ActionKey | null>(null);

  const loadDetail = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const deployment = await getDeployment(deploymentId);
      const [project, logs, ciRuns, summaries, jobs] = await Promise.all([
        getProject(deployment.projectId),
        getDeploymentLogs(deployment.id),
        getCiRunsForDeployment(deployment),
        getAiSummaries(deployment.id),
        getAiJobs(deployment.id),
      ]);
      setState({
        deployment,
        project,
        logs: sortLogs(logs),
        ciRuns: sortCiRuns(ciRuns),
        summaries: sortSummaries(summaries),
        jobs: sortJobs(jobs),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load deployment detail.");
    } finally {
      setIsLoading(false);
    }
  }, [deploymentId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const latestSummary = useMemo(() => state.summaries[0] ?? null, [state.summaries]);

  async function runAction(key: ActionKey, action: () => Promise<unknown>, successMessage: string) {
    if (activeAction) {
      return;
    }

    try {
      setActiveAction(key);
      setActionError(null);
      setActionMessage(null);
      await action();
      setActionMessage(successMessage);
      await loadDetail();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : `${key} failed.`);
    } finally {
      setActiveAction(null);
    }
  }

  if (isLoading) {
    return <LoadingDetail />;
  }

  if (error) {
    return <ErrorPanel message={error} onRetry={loadDetail} />;
  }

  if (!state.deployment) {
    return <Panel>Deployment not found.</Panel>;
  }

  const deployment = state.deployment;
  const serviceName = state.project?.serviceName ?? deployment.projectId;

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0">
          <Link href="/deployments" className="text-sm font-semibold text-sky-700 hover:text-sky-900">
            Back to deployments
          </Link>
          <p className="mt-4 text-xs font-semibold uppercase tracking-wide text-sky-700">Deployment detail</p>
          <h1 className="mt-2 truncate text-3xl font-semibold text-ink">{serviceName}</h1>
          <p className="mt-2 max-w-3xl break-words text-sm leading-6 text-muted">
            {state.project?.name ?? "Unknown project"} / {deployment.environment} / {deployment.branch}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={deployment.status} />
          <RiskBadge level={deployment.riskLevel} />
        </div>
      </section>

      <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-ink">Deployment actions</h2>
            <p className="mt-1 text-xs text-muted">Run risk scoring and AI analysis against the current backend data.</p>
          </div>
          <div className="grid gap-2 sm:grid-cols-3 lg:w-auto">
            <ActionButton
              label="Recalculate Risk"
              busyLabel={actionLabels.risk}
              isBusy={activeAction === "risk"}
              disabled={Boolean(activeAction)}
              onClick={() => runAction("risk", () => recalculateRisk(deployment.id), "Risk score recalculated.")}
            />
            <ActionButton
              label="Run Sync AI Analysis"
              busyLabel={actionLabels["sync-ai"]}
              isBusy={activeAction === "sync-ai"}
              disabled={Boolean(activeAction)}
              onClick={() =>
                runAction("sync-ai", () => runSyncAiAnalysis(deployment.id), "Synchronous AI analysis completed.")
              }
            />
            <ActionButton
              label="Queue Async AI Analysis"
              busyLabel={actionLabels["async-ai"]}
              isBusy={activeAction === "async-ai"}
              disabled={Boolean(activeAction)}
              onClick={() => runAction("async-ai", () => queueAsyncAiAnalysis(deployment.id), "Async AI analysis job queued.")}
            />
          </div>
        </div>
        <div aria-live="polite" className="mt-3 min-h-5 text-sm">
          {activeAction ? <span className="text-sky-700">{actionLabels[activeAction]}</span> : null}
          {actionMessage ? <span className="text-emerald-700">{actionMessage}</span> : null}
          {actionError ? <span className="text-rose-700">{actionError}</span> : null}
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <MetadataPanel deployment={deployment} project={state.project} />
        <RiskPanel deployment={deployment} />
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <CiPanel ciRuns={state.ciRuns} />
        <LogsPanel logs={state.logs} />
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <AiSummaryPanel summary={latestSummary} />
        <JobsPanel jobs={state.jobs} />
      </section>
    </div>
  );
}

function ActionButton({
  label,
  busyLabel,
  isBusy,
  disabled,
  onClick,
}: {
  label: string;
  busyLabel: string;
  isBusy: boolean;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="rounded border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {isBusy ? busyLabel : label}
    </button>
  );
}

function MetadataPanel({ deployment, project }: { deployment: Deployment; project: Project | null }) {
  const rows = [
    ["Project", project?.name ?? deployment.projectId],
    ["Service", project?.serviceName ?? deployment.projectId],
    ["Environment", deployment.environment],
    ["Branch", deployment.branch],
    ["Commit", deployment.commitSha],
    ["Deployment status", deployment.status],
    ["Deployed by", deployment.deployedBy],
    ["Deployed at", formatDateTime(deployment.deployedAt)],
    ["Updated", formatDateTime(deployment.updatedAt)],
  ];

  return (
    <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
      <h2 className="text-sm font-semibold text-ink">Deployment metadata</h2>
      <dl className="mt-4 grid gap-3 text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="grid gap-1 sm:grid-cols-[140px_1fr] sm:gap-3">
            <dt className="text-slate-500">{label}</dt>
            <dd className="break-words font-medium text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function RiskPanel({ deployment }: { deployment: Deployment }) {
  const scoreTone =
    deployment.riskLevel === "HIGH"
      ? "border-rose-200 bg-rose-50 text-rose-700"
      : deployment.riskLevel === "MEDIUM"
        ? "border-amber-200 bg-amber-50 text-amber-700"
        : "border-emerald-200 bg-emerald-50 text-emerald-700";

  return (
    <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
      <h2 className="text-sm font-semibold text-ink">Risk analysis</h2>
      <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className={`grid h-28 w-28 place-items-center rounded border text-4xl font-semibold ${scoreTone}`}>
          {deployment.riskScore}
        </div>
        <div>
          <RiskBadge level={deployment.riskLevel} />
          <p className="mt-3 max-w-xl text-sm leading-6 text-muted">
            The backend risk engine scores deployments from failed CI runs, failed tests, deployment context, and ERROR
            logs linked to the deployment.
          </p>
        </div>
      </div>
    </section>
  );
}

function CiPanel({ ciRuns }: { ciRuns: CiRun[] }) {
  return (
    <section className="rounded border border-slate-200 bg-white shadow-panel">
      <SectionTitle title="CI/CD signals" description="Runs associated with this deployment commit." />
      {ciRuns.length === 0 ? (
        <EmptyRows label="No CI runs found for this deployment commit." />
      ) : (
        <div className="divide-y divide-slate-100">
          {ciRuns.map((run) => (
            <div key={run.id} className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_auto] sm:items-center">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{run.provider}</span>
                  <StatusBadge status={run.status} />
                </div>
                <p className="mt-2 truncate font-mono text-xs text-muted">{run.commitSha}</p>
              </div>
              <div className="text-sm text-muted sm:text-right">
                <div>{run.durationSeconds}s duration</div>
                <div className={run.failedTests > 0 ? "font-semibold text-rose-700" : ""}>
                  {run.failedTests} failed tests
                </div>
                <div className="text-xs">{formatDateTime(run.createdAt)}</div>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function LogsPanel({ logs }: { logs: ApplicationLog[] }) {
  return (
    <section className="rounded border border-slate-200 bg-white shadow-panel">
      <SectionTitle title="Application logs" description="Logs linked to this deployment." />
      {logs.length === 0 ? (
        <EmptyRows label="No logs found for this deployment." />
      ) : (
        <div className="divide-y divide-slate-100">
          {logs.map((log) => (
            <div key={log.id} className="px-4 py-4">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={log.level} />
                <span className="text-sm font-semibold text-ink">{log.serviceName}</span>
                <span className="text-xs text-muted">{formatDateTime(log.timestamp)}</span>
              </div>
              <p className="mt-2 break-words text-sm leading-6 text-slate-700">{log.message}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function AiSummaryPanel({ summary }: { summary: AiIncidentSummary | null }) {
  if (!summary) {
    return (
      <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
        <h2 className="text-sm font-semibold text-ink">AI incident summary</h2>
        <p className="mt-3 text-sm leading-6 text-muted">
          No AI summaries found. Run sync analysis or queue an async job to generate one.
        </p>
      </section>
    );
  }

  const evidence = parseJsonTextList(summary.evidence);
  const recommendedActions = parseJsonTextList(summary.recommendedActions);

  return (
    <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold text-ink">AI incident summary</h2>
          <p className="mt-1 text-xs text-muted">{summary.modelName} / {formatDateTime(summary.createdAt)}</p>
        </div>
        <div className="flex gap-2">
          <RiskBadge level={summary.severity} />
          <StatusBadge status={summary.confidence} />
        </div>
      </div>
      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <TextBlock title="Summary" value={summary.summary} />
        <TextBlock title="Likely root cause" value={summary.likelyRootCause} />
      </div>
      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <ListBlock title="Evidence" items={evidence} />
        <ListBlock title="Recommended actions" items={recommendedActions} />
      </div>
    </section>
  );
}

function JobsPanel({ jobs }: { jobs: AiAnalysisJob[] }) {
  return (
    <section className="rounded border border-slate-200 bg-white shadow-panel">
      <SectionTitle title="Async AI analysis jobs" description="Queue status for background incident analysis." />
      {jobs.length === 0 ? (
        <EmptyRows label="No async AI jobs found." />
      ) : (
        <div className="divide-y divide-slate-100">
          {jobs.map((job) => (
            <div key={job.id} className="px-4 py-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="break-all font-mono text-xs font-semibold text-ink">{job.id}</span>
                <StatusBadge status={job.status} />
              </div>
              <div className="mt-2 text-sm text-muted">
                Updated {formatDateTime(job.updatedAt)}
                {job.completedAt ? ` / completed ${formatDateTime(job.completedAt)}` : ""}
              </div>
              {job.errorMessage ? (
                <div className="mt-3 rounded border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
                  {job.errorMessage}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function SectionTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className="border-b border-slate-200 px-4 py-4">
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
      <p className="mt-1 text-xs text-muted">{description}</p>
    </div>
  );
}

function EmptyRows({ label }: { label: string }) {
  return <div className="px-4 py-4 text-sm text-muted">{label}</div>;
}

function TextBlock({ title, value }: { title: string; value: string }) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase text-slate-500">{title}</h3>
      <p className="mt-2 break-words text-sm leading-6 text-slate-700">{value}</p>
    </div>
  );
}

function ListBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase text-slate-500">{title}</h3>
      {items.length === 0 ? (
        <p className="mt-2 text-sm text-muted">No entries.</p>
      ) : (
        <ul className="mt-2 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-700">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function LoadingDetail() {
  return (
    <div className="space-y-4" role="status" aria-live="polite">
      <p className="text-sm text-muted">Loading deployment detail...</p>
      <div className="h-24 animate-pulse rounded border border-slate-200 bg-white shadow-panel" />
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="h-56 animate-pulse rounded border border-slate-200 bg-white shadow-panel" />
        <div className="h-56 animate-pulse rounded border border-slate-200 bg-white shadow-panel" />
      </div>
    </div>
  );
}

function Panel({ children }: { children: React.ReactNode }) {
  return <div className="rounded border border-slate-200 bg-white p-4 text-sm text-muted shadow-panel">{children}</div>;
}

function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
      <div className="font-semibold">Could not load deployment detail</div>
      <p className="mt-1">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded border border-rose-300 bg-white px-3 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2"
      >
        Retry
      </button>
    </div>
  );
}

function sortLogs(logs: ApplicationLog[]) {
  return [...logs].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
}

function sortCiRuns(ciRuns: CiRun[]) {
  return [...ciRuns].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

function sortSummaries(summaries: AiIncidentSummary[]) {
  return [...summaries].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

function sortJobs(jobs: AiAnalysisJob[]) {
  return [...jobs].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
}
