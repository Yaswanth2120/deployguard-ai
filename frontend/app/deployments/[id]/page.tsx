"use client";

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

const initialState: DetailState = {
  deployment: null,
  project: null,
  logs: [],
  ciRuns: [],
  summaries: [],
  jobs: [],
};

export default function DeploymentDetailPage() {
  const params = useParams<{ id: string }>();
  const deploymentId = params.id;
  const [state, setState] = useState<DetailState>(initialState);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [activeAction, setActiveAction] = useState<string | null>(null);

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
      setState({ deployment, project, logs, ciRuns, summaries, jobs });
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

  async function runAction(label: string, action: () => Promise<unknown>, successMessage: string) {
    try {
      setActiveAction(label);
      setActionError(null);
      setActionMessage(null);
      await action();
      setActionMessage(successMessage);
      await loadDetail();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : `${label} failed.`);
    } finally {
      setActiveAction(null);
    }
  }

  if (isLoading) {
    return <Panel>Loading deployment detail...</Panel>;
  }

  if (error) {
    return <ErrorPanel message={error} />;
  }

  if (!state.deployment) {
    return <Panel>Deployment not found.</Panel>;
  }

  const deployment = state.deployment;
  const serviceName = state.project?.serviceName ?? deployment.projectId;

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-ink">{serviceName}</h1>
          <p className="mt-2 text-sm text-muted">
            Deployment {deployment.id} | {deployment.commitSha} | {formatDateTime(deployment.deployedAt)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={deployment.status} />
          <RiskBadge level={deployment.riskLevel} />
        </div>
      </section>

      <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
        <div className="flex flex-wrap gap-3">
          <ActionButton
            label="Recalculate Risk"
            isBusy={activeAction === "risk"}
            onClick={() => runAction("risk", () => recalculateRisk(deployment.id), "Risk score recalculated.")}
          />
          <ActionButton
            label="Run Sync AI Analysis"
            isBusy={activeAction === "sync-ai"}
            onClick={() => runAction("sync-ai", () => runSyncAiAnalysis(deployment.id), "Synchronous AI analysis completed.")}
          />
          <ActionButton
            label="Queue Async AI Analysis"
            isBusy={activeAction === "async-ai"}
            onClick={() => runAction("async-ai", () => queueAsyncAiAnalysis(deployment.id), "Async AI analysis job queued.")}
          />
        </div>
        {actionMessage ? <div className="mt-3 text-sm text-emerald-700">{actionMessage}</div> : null}
        {actionError ? <div className="mt-3 text-sm text-rose-700">{actionError}</div> : null}
      </section>

      <section className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="rounded border border-slate-200 bg-white p-4 shadow-panel">
          <h2 className="text-sm font-semibold text-ink">Deployment metadata</h2>
          <dl className="mt-4 grid gap-3 text-sm">
            {[
              ["Project", state.project?.name ?? deployment.projectId],
              ["Environment", deployment.environment],
              ["Branch", deployment.branch],
              ["Commit", deployment.commitSha],
              ["Deployed by", deployment.deployedBy],
              ["Updated", formatDateTime(deployment.updatedAt)],
            ].map(([label, value]) => (
              <div key={label} className="grid grid-cols-[120px_1fr] gap-3">
                <dt className="text-slate-500">{label}</dt>
                <dd className="break-words font-medium text-ink">{value}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="rounded border border-slate-200 bg-white p-4 shadow-panel">
          <h2 className="text-sm font-semibold text-ink">Risk score</h2>
          <div className="mt-4 flex items-center gap-4">
            <div className="grid h-24 w-24 place-items-center rounded border border-slate-200 bg-slate-50 text-3xl font-semibold text-ink">
              {deployment.riskScore}
            </div>
            <div>
              <RiskBadge level={deployment.riskLevel} />
              <p className="mt-3 max-w-xl text-sm text-muted">
                Risk is calculated by the backend from CI/CD failures, failed tests, deployment context, and error logs.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <SectionTitle title="CI/CD signals" />
          {state.ciRuns.length === 0 ? (
            <EmptyRows label="No CI runs found for this deployment commit." />
          ) : (
            <div className="divide-y divide-slate-100">
              {state.ciRuns.map((run) => (
                <div key={run.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[140px_100px_1fr] sm:items-center">
                  <div className="font-medium text-ink">{run.provider}</div>
                  <StatusBadge status={run.status} />
                  <div className="text-sm text-muted">
                    {run.durationSeconds}s | failed tests {run.failedTests} | {formatDateTime(run.createdAt)}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <SectionTitle title="Logs" />
          {state.logs.length === 0 ? (
            <EmptyRows label="No logs found for this deployment." />
          ) : (
            <div className="divide-y divide-slate-100">
              {state.logs.map((log) => (
                <div key={log.id} className="px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={log.level} />
                    <span className="text-sm font-medium text-ink">{log.serviceName}</span>
                    <span className="text-xs text-muted">{formatDateTime(log.timestamp)}</span>
                  </div>
                  <p className="mt-2 text-sm text-muted">{log.message}</p>
                </div>
              ))}
            </div>
          )}
        </div>
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
  isBusy,
  onClick,
}: {
  label: string;
  isBusy: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isBusy}
      className="rounded border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {isBusy ? "Working..." : label}
    </button>
  );
}

function AiSummaryPanel({ summary }: { summary: AiIncidentSummary | null }) {
  if (!summary) {
    return (
      <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
        <h2 className="text-sm font-semibold text-ink">AI incident summary</h2>
        <p className="mt-3 text-sm text-muted">No AI summaries found for this deployment.</p>
      </section>
    );
  }

  const evidence = parseJsonTextList(summary.evidence);
  const recommendedActions = parseJsonTextList(summary.recommendedActions);

  return (
    <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-sm font-semibold text-ink">AI incident summary</h2>
        <div className="flex gap-2">
          <RiskBadge level={summary.severity} />
          <StatusBadge status={summary.confidence} />
        </div>
      </div>
      <div className="mt-4 grid gap-5 lg:grid-cols-2">
        <div>
          <h3 className="text-xs font-semibold uppercase text-slate-500">Summary</h3>
          <p className="mt-2 text-sm text-slate-700">{summary.summary}</p>
        </div>
        <div>
          <h3 className="text-xs font-semibold uppercase text-slate-500">Likely root cause</h3>
          <p className="mt-2 text-sm text-slate-700">{summary.likelyRootCause}</p>
        </div>
      </div>
      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <ListBlock title="Evidence" items={evidence} />
        <ListBlock title="Recommended actions" items={recommendedActions} />
      </div>
      <p className="mt-4 text-xs text-muted">
        {summary.modelName} | {formatDateTime(summary.createdAt)}
      </p>
    </section>
  );
}

function JobsPanel({ jobs }: { jobs: AiAnalysisJob[] }) {
  return (
    <section className="rounded border border-slate-200 bg-white shadow-panel">
      <SectionTitle title="Async AI jobs" />
      {jobs.length === 0 ? (
        <EmptyRows label="No async AI jobs found." />
      ) : (
        <div className="divide-y divide-slate-100">
          {jobs.map((job) => (
            <div key={job.id} className="px-4 py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium text-ink">{job.id}</span>
                <StatusBadge status={job.status} />
              </div>
              <div className="mt-2 text-sm text-muted">
                Updated {formatDateTime(job.updatedAt)}
                {job.completedAt ? ` | completed ${formatDateTime(job.completedAt)}` : ""}
              </div>
              {job.errorMessage ? <div className="mt-2 text-sm text-rose-700">{job.errorMessage}</div> : null}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function SectionTitle({ title }: { title: string }) {
  return (
    <div className="border-b border-slate-200 px-4 py-3">
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
    </div>
  );
}

function EmptyRows({ label }: { label: string }) {
  return <div className="px-4 py-3 text-sm text-muted">{label}</div>;
}

function ListBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase text-slate-500">{title}</h3>
      {items.length === 0 ? (
        <p className="mt-2 text-sm text-muted">No entries.</p>
      ) : (
        <ul className="mt-2 space-y-2 text-sm text-slate-700">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Panel({ children }: { children: React.ReactNode }) {
  return <div className="rounded border border-slate-200 bg-white p-4 text-sm text-muted shadow-panel">{children}</div>;
}

function ErrorPanel({ message }: { message: string }) {
  return <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{message}</div>;
}
