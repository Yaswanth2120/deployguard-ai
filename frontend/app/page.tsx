"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import {
  AiAnalysisJob,
  Deployment,
  formatDateTime,
  getAiJobs,
  getAiSummaries,
  getAllDeployments,
  getProjects,
  Project,
} from "@/lib/api";

type AiActivity = {
  deployment: Deployment;
  jobs: AiAnalysisJob[];
  summaryCount: number;
};

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [aiActivity, setAiActivity] = useState<AiActivity[]>([]);
  const [completedAnalyses, setCompletedAnalyses] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadDashboard() {
      try {
        setIsLoading(true);
        setError(null);
        const [projectData, deploymentData] = await Promise.all([getProjects(), getAllDeployments()]);
        const activity = await Promise.all(
          deploymentData.slice(0, 8).map(async (deployment) => {
            const [summaries, jobs] = await Promise.all([getAiSummaries(deployment.id), getAiJobs(deployment.id)]);
            return { deployment, jobs, summaryCount: summaries.length };
          }),
        );
        if (!isMounted) {
          return;
        }
        setProjects(projectData);
        setDeployments(sortByDate(deploymentData));
        setAiActivity(activity.filter((item) => item.jobs.length > 0 || item.summaryCount > 0));
        setCompletedAnalyses(activity.filter((item) => item.summaryCount > 0).length);
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load dashboard data.");
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    loadDashboard();
    return () => {
      isMounted = false;
    };
  }, []);

  const highRiskDeployments = useMemo(
    () => deployments.filter((deployment) => deployment.riskLevel === "HIGH"),
    [deployments],
  );

  const metrics = [
    { label: "Projects", value: projects.length, detail: "Tracked services", tone: "slate" },
    { label: "Deployments", value: deployments.length, detail: "Release events", tone: "slate" },
    { label: "High risk", value: highRiskDeployments.length, detail: "Needs review", tone: "rose" },
    { label: "AI analyses", value: completedAnalyses, detail: "Completed summaries", tone: "sky" },
  ];

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-700">Deployment intelligence</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal text-ink sm:text-4xl">
            DeployGuard AI dashboard
          </h1>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-muted">
            Monitor deployment risk, CI/CD signals, application errors, and AI incident analysis from the local
            DeployGuard API.
          </p>
        </div>
        <Link
          href="/deployments"
          className="inline-flex w-full items-center justify-center rounded border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2 sm:w-auto"
        >
          Review deployments
        </Link>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Deployment summary metrics">
        {metrics.map((metric) => (
          <MetricCard key={metric.label} metric={metric} isLoading={isLoading} />
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.35fr_0.85fr]">
        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <SectionHeader
            title="Recent deployments"
            description="Latest deployment events with risk and release context."
            href="/deployments"
            linkLabel="View all"
          />
          {isLoading ? (
            <LoadingRows label="Loading deployments..." />
          ) : deployments.length === 0 ? (
            <EmptyState
              title="No deployments yet"
              description="Run ./scripts/seed-demo.sh to create a high-risk production deployment for the demo."
            />
          ) : (
            <div className="divide-y divide-slate-100">
              {deployments.slice(0, 6).map((deployment) => (
                <Link
                  key={deployment.id}
                  href={`/deployments/${deployment.id}`}
                  className="grid gap-3 px-4 py-4 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-sky-500 md:grid-cols-[1.2fr_0.8fr_0.9fr_auto] md:items-center"
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-ink">{serviceName(projects, deployment)}</div>
                    <div className="mt-1 truncate text-xs text-muted">{deployment.branch}</div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={deployment.status} />
                    <span className="text-xs font-medium uppercase text-slate-500">{deployment.environment}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-ink">{deployment.riskScore}</span>
                    <RiskBadge level={deployment.riskLevel} />
                  </div>
                  <div className="text-xs text-muted md:text-right">{formatDateTime(deployment.deployedAt)}</div>
                </Link>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-6">
          <div className="rounded border border-rose-200 bg-rose-50 p-5 shadow-panel">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-sm font-semibold text-rose-950">High-risk focus</h2>
                <p className="mt-2 text-sm text-rose-800">
                  {isLoading
                    ? "Checking current deployment risk..."
                    : highRiskDeployments.length === 0
                      ? "No high-risk deployments in the current data set."
                      : `${highRiskDeployments.length} deployment${highRiskDeployments.length === 1 ? "" : "s"} need attention.`}
                </p>
              </div>
              <div className="grid h-14 w-14 shrink-0 place-items-center rounded border border-rose-200 bg-white text-2xl font-semibold text-rose-700">
                {isLoading ? "..." : highRiskDeployments.length}
              </div>
            </div>
            {!isLoading && highRiskDeployments.length > 0 ? (
              <div className="mt-4 space-y-3">
                {highRiskDeployments.slice(0, 3).map((deployment) => (
                  <Link
                    key={deployment.id}
                    href={`/deployments/${deployment.id}`}
                    className="block rounded border border-rose-200 bg-white p-3 transition hover:border-rose-300 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="truncate text-sm font-semibold text-ink">{serviceName(projects, deployment)}</span>
                      <RiskBadge level={deployment.riskLevel} />
                    </div>
                    <div className="mt-2 text-xs text-muted">
                      {deployment.environment} / {deployment.branch} / score {deployment.riskScore}
                    </div>
                  </Link>
                ))}
              </div>
            ) : null}
          </div>

          <div className="rounded border border-slate-200 bg-white shadow-panel">
            <SectionHeader title="Recent AI activity" description="Async jobs and stored AI summaries." />
            {isLoading ? (
              <LoadingRows label="Loading AI activity..." />
            ) : aiActivity.length === 0 ? (
              <EmptyState
                title="No AI activity yet"
                description="Open a deployment detail page to run sync analysis or queue an async job."
              />
            ) : (
              <div className="divide-y divide-slate-100">
                {aiActivity.slice(0, 5).map((item) => {
                  const latestJob = item.jobs[0];
                  return (
                    <Link
                      key={item.deployment.id}
                      href={`/deployments/${item.deployment.id}`}
                      className="block px-4 py-4 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-sky-500"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <span className="truncate text-sm font-semibold text-ink">
                          {serviceName(projects, item.deployment)}
                        </span>
                        {latestJob ? <StatusBadge status={latestJob.status} /> : <StatusBadge status="COMPLETED" />}
                      </div>
                      <p className="mt-2 text-xs text-muted">
                        {item.summaryCount} saved summar{item.summaryCount === 1 ? "y" : "ies"}
                        {latestJob ? ` / updated ${formatDateTime(latestJob.updatedAt)}` : ""}
                      </p>
                    </Link>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

function MetricCard({
  metric,
  isLoading,
}: {
  metric: { label: string; value: number; detail: string; tone: string };
  isLoading: boolean;
}) {
  const tone =
    metric.tone === "rose"
      ? "border-rose-200 bg-rose-50 text-rose-700"
      : metric.tone === "sky"
        ? "border-sky-200 bg-sky-50 text-sky-700"
        : "border-slate-200 bg-white text-ink";

  return (
    <div className={`rounded border p-5 shadow-panel ${tone}`}>
      <div className="text-sm font-medium opacity-80">{metric.label}</div>
      <div className="mt-3 text-4xl font-semibold">{isLoading ? "..." : metric.value}</div>
      <div className="mt-2 text-xs font-medium opacity-75">{metric.detail}</div>
    </div>
  );
}

function SectionHeader({
  title,
  description,
  href,
  linkLabel,
}: {
  title: string;
  description: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="flex flex-col gap-3 border-b border-slate-200 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h2 className="text-sm font-semibold text-ink">{title}</h2>
        <p className="mt-1 text-xs text-muted">{description}</p>
      </div>
      {href && linkLabel ? (
        <Link href={href} className="text-sm font-semibold text-sky-700 hover:text-sky-900">
          {linkLabel}
        </Link>
      ) : null}
    </div>
  );
}

function LoadingRows({ label }: { label: string }) {
  return (
    <div className="space-y-3 p-4" role="status" aria-live="polite">
      <p className="text-sm text-muted">{label}</p>
      {[0, 1, 2].map((item) => (
        <div key={item} className="h-12 animate-pulse rounded bg-slate-100" />
      ))}
    </div>
  );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="p-6 text-sm">
      <h3 className="font-semibold text-ink">{title}</h3>
      <p className="mt-2 max-w-xl leading-6 text-muted">{description}</p>
    </div>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
      <div className="font-semibold">Could not load dashboard</div>
      <p className="mt-1">{message}</p>
    </div>
  );
}

function serviceName(projects: Project[], deployment: Deployment) {
  return projects.find((project) => project.id === deployment.projectId)?.serviceName ?? deployment.projectId;
}

function sortByDate(deployments: Deployment[]) {
  return [...deployments].sort((a, b) => new Date(b.deployedAt).getTime() - new Date(a.deployedAt).getTime());
}
