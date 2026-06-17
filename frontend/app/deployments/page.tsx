"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { Deployment, formatDateTime, getAllDeployments, getProjects, Project } from "@/lib/api";

export default function DeploymentsPage() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDeployments = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const [projectData, deploymentData] = await Promise.all([getProjects(), getAllDeployments()]);
      setProjects(projectData);
      setDeployments(sortByDate(deploymentData));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load deployments.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDeployments();
  }, [loadDeployments]);

  const highRiskCount = useMemo(
    () => deployments.filter((deployment) => deployment.riskLevel === "HIGH").length,
    [deployments],
  );

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-700">Release history</p>
          <h1 className="mt-2 text-3xl font-semibold text-ink">Deployments</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
            Review deployment status, environment, branch, commit, risk score, and incident context.
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="rounded border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700">
            {isLoading ? "..." : highRiskCount} high risk
          </div>
          <button
            type="button"
            onClick={loadDeployments}
            className="rounded border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
          >
            Retry refresh
          </button>
        </div>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      <section className="rounded border border-slate-200 bg-white shadow-panel">
        {isLoading ? (
          <LoadingRows label="Loading deployments..." />
        ) : deployments.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="divide-y divide-slate-100">
            {deployments.map((deployment) => (
              <Link
                key={deployment.id}
                href={`/deployments/${deployment.id}`}
                className="grid gap-4 px-4 py-4 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-sky-500 lg:grid-cols-[1.2fr_0.75fr_0.85fr_1fr_0.85fr] lg:items-center"
              >
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold text-ink">{serviceName(projects, deployment)}</div>
                  <div className="mt-1 truncate text-xs text-muted">{projectName(projects, deployment)}</div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold uppercase text-slate-700">
                    {deployment.environment}
                  </span>
                  <StatusBadge status={deployment.status} />
                </div>

                <div className="min-w-0 text-sm">
                  <div className="truncate font-medium text-ink">{deployment.branch}</div>
                  <div className="mt-1 truncate font-mono text-xs text-muted">{shortCommit(deployment.commitSha)}</div>
                </div>

                <div className="flex items-center gap-3">
                  <div className="grid h-11 w-11 place-items-center rounded border border-slate-200 bg-slate-50 text-sm font-semibold text-ink">
                    {deployment.riskScore}
                  </div>
                  <RiskBadge level={deployment.riskLevel} />
                </div>

                <div className="text-xs text-muted lg:text-right">
                  <div>Deployed</div>
                  <div className="mt-1 font-medium text-slate-700">{formatDateTime(deployment.deployedAt)}</div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function LoadingRows({ label }: { label: string }) {
  return (
    <div className="space-y-3 p-4" role="status" aria-live="polite">
      <p className="text-sm text-muted">{label}</p>
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="h-16 animate-pulse rounded bg-slate-100" />
      ))}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold text-ink">No deployments found</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
        Run <code className="rounded bg-slate-100 px-1.5 py-0.5">./scripts/seed-demo.sh</code> to create a production
        deployment with CI and log signals.
      </p>
    </div>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
      <div className="font-semibold">Could not load deployments</div>
      <p className="mt-1">{message}</p>
    </div>
  );
}

function serviceName(projects: Project[], deployment: Deployment) {
  return projects.find((project) => project.id === deployment.projectId)?.serviceName ?? deployment.projectId;
}

function projectName(projects: Project[], deployment: Deployment) {
  return projects.find((project) => project.id === deployment.projectId)?.name ?? "Unknown project";
}

function shortCommit(commitSha: string) {
  return commitSha.length > 12 ? commitSha.slice(0, 12) : commitSha;
}

function sortByDate(deployments: Deployment[]) {
  return [...deployments].sort((a, b) => new Date(b.deployedAt).getTime() - new Date(a.deployedAt).getTime());
}
