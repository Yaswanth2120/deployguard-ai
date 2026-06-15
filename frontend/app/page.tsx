"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { Deployment, getAiSummaries, getAllDeployments, getProjects, Project } from "@/lib/api";

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
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
        const summaries = await Promise.all(deploymentData.map((deployment) => getAiSummaries(deployment.id)));
        if (!isMounted) {
          return;
        }
        setProjects(projectData);
        setDeployments(deploymentData);
        setCompletedAnalyses(summaries.filter((deploymentSummaries) => deploymentSummaries.length > 0).length);
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
    { label: "Total projects", value: projects.length },
    { label: "Total deployments", value: deployments.length },
    { label: "High risk deployments", value: highRiskDeployments.length },
    { label: "Completed AI analyses", value: completedAnalyses },
  ];

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-normal text-ink">Dashboard</h1>
        <p className="max-w-3xl text-sm text-muted">
          Live deployment risk overview from the local DeployGuard API.
        </p>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {metrics.map((metric) => (
          <div key={metric.label} className="rounded border border-slate-200 bg-white p-4 shadow-panel">
            <div className="text-sm font-medium text-muted">{metric.label}</div>
            <div className="mt-3 text-3xl font-semibold text-ink">{isLoading ? "..." : metric.value}</div>
          </div>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.35fr_0.85fr]">
        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-ink">Recent deployments</h2>
          </div>
          {isLoading ? (
            <LoadingPanel label="Loading deployments..." />
          ) : deployments.length === 0 ? (
            <EmptyPanel label="No deployments found." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Service</th>
                    <th className="px-4 py-3">Environment</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Risk</th>
                    <th className="px-4 py-3">Commit</th>
                    <th className="px-4 py-3">Opened</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {deployments.slice(0, 6).map((deployment) => (
                    <tr key={deployment.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium text-ink">{serviceName(projects, deployment)}</td>
                      <td className="px-4 py-3 text-slate-600">{deployment.environment}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={deployment.status} />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-ink">{deployment.riskScore}</span>
                          <RiskBadge level={deployment.riskLevel} />
                        </div>
                      </td>
                      <td className="px-4 py-3 text-slate-600">{deployment.commitSha}</td>
                      <td className="px-4 py-3">
                        <Link href={`/deployments/${deployment.id}`} className="font-medium text-sky-700 hover:text-sky-900">
                          View detail
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="rounded border border-slate-200 bg-white p-4 shadow-panel">
          <h2 className="text-sm font-semibold text-ink">High risk focus</h2>
          {isLoading ? (
            <div className="mt-4 text-sm text-muted">Loading high risk deployments...</div>
          ) : highRiskDeployments.length === 0 ? (
            <div className="mt-4 text-sm text-muted">No high risk deployments found.</div>
          ) : (
            <div className="mt-4 space-y-3">
              {highRiskDeployments.map((deployment) => (
                <Link
                  key={deployment.id}
                  href={`/deployments/${deployment.id}`}
                  className="block rounded border border-slate-200 p-3 hover:border-slate-300 hover:bg-slate-50"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="font-medium text-ink">{serviceName(projects, deployment)}</div>
                    <RiskBadge level={deployment.riskLevel} />
                  </div>
                  <div className="mt-2 text-sm text-muted">
                    {deployment.environment} | {deployment.branch} | score {deployment.riskScore}
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function LoadingPanel({ label }: { label: string }) {
  return <div className="p-4 text-sm text-muted">{label}</div>;
}

function EmptyPanel({ label }: { label: string }) {
  return <div className="p-4 text-sm text-muted">{label}</div>;
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
      {message}
    </div>
  );
}

function serviceName(projects: Project[], deployment: Deployment) {
  return projects.find((project) => project.id === deployment.projectId)?.serviceName ?? deployment.projectId;
}
