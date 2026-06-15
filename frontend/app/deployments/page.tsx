"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { Deployment, getAllDeployments, getProjects, Project } from "@/lib/api";

export default function DeploymentsPage() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadDeployments() {
      try {
        setIsLoading(true);
        setError(null);
        const [projectData, deploymentData] = await Promise.all([getProjects(), getAllDeployments()]);
        if (isMounted) {
          setProjects(projectData);
          setDeployments(deploymentData);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load deployments.");
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    loadDeployments();
    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-semibold text-ink">Deployments</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">
          Deployment list fetched from the local DeployGuard API.
        </p>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      <section className="rounded border border-slate-200 bg-white shadow-panel">
        {isLoading ? (
          <div className="p-4 text-sm text-muted">Loading deployments...</div>
        ) : deployments.length === 0 ? (
          <div className="p-4 text-sm text-muted">No deployments found.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">Service name</th>
                  <th className="px-4 py-3">Environment</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Risk score</th>
                  <th className="px-4 py-3">Risk level</th>
                  <th className="px-4 py-3">Branch</th>
                  <th className="px-4 py-3">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deployments.map((deployment) => (
                  <tr key={deployment.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-ink">{serviceName(projects, deployment)}</td>
                    <td className="px-4 py-3 text-slate-600">{deployment.environment}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={deployment.status} />
                    </td>
                    <td className="px-4 py-3 font-semibold text-ink">{deployment.riskScore}</td>
                    <td className="px-4 py-3">
                      <RiskBadge level={deployment.riskLevel} />
                    </td>
                    <td className="px-4 py-3 text-slate-600">{deployment.branch}</td>
                    <td className="px-4 py-3">
                      <Link href={`/deployments/${deployment.id}`} className="font-medium text-sky-700 hover:text-sky-900">
                        Open
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
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
