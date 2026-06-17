"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Deployment, formatDateTime, getProjectDeployments, getProjects, Project } from "@/lib/api";

type ProjectWithDeployments = {
  project: Project;
  deployments: Deployment[];
};

export default function ProjectsPage() {
  const [items, setItems] = useState<ProjectWithDeployments[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProjects = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const projects = await getProjects();
      const groups = await Promise.all(
        projects.map(async (project) => ({
          project,
          deployments: await getProjectDeployments(project.id),
        })),
      );
      setItems(groups);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load projects.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-sky-700">Service inventory</p>
          <h1 className="mt-2 text-3xl font-semibold text-ink">Projects</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
            Track repositories and services that DeployGuard uses to correlate deployments, CI runs, logs, and AI analysis.
          </p>
        </div>
        <button
          type="button"
          onClick={loadProjects}
          className="inline-flex items-center justify-center rounded border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
        >
          Retry refresh
        </button>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      {isLoading ? (
        <LoadingGrid label="Loading projects..." />
      ) : items.length === 0 ? (
        <EmptyState />
      ) : (
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Projects">
          {items.map(({ project, deployments }) => {
            const latestDeployment = [...deployments].sort(
              (a, b) => new Date(b.deployedAt).getTime() - new Date(a.deployedAt).getTime(),
            )[0];
            return (
              <article key={project.id} className="rounded border border-slate-200 bg-white p-5 shadow-panel">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <h2 className="truncate text-lg font-semibold text-ink">{project.name}</h2>
                    <p className="mt-1 text-sm font-medium text-sky-700">{project.serviceName}</p>
                  </div>
                  <div className="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-center">
                    <div className="text-lg font-semibold text-ink">{deployments.length}</div>
                    <div className="text-[11px] font-medium uppercase text-slate-500">Deployments</div>
                  </div>
                </div>

                <dl className="mt-5 space-y-4 text-sm">
                  <div>
                    <dt className="text-xs font-semibold uppercase text-slate-500">Repository</dt>
                    <dd className="mt-1 break-words text-slate-700">
                      <a
                        href={project.githubRepoUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="font-medium text-sky-700 hover:text-sky-900 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
                      >
                        {project.githubRepoUrl}
                      </a>
                    </dd>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <dt className="text-xs font-semibold uppercase text-slate-500">Created</dt>
                      <dd className="mt-1 font-medium text-ink">{formatDateTime(project.createdAt)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs font-semibold uppercase text-slate-500">Latest deploy</dt>
                      <dd className="mt-1 font-medium text-ink">
                        {latestDeployment ? formatDateTime(latestDeployment.deployedAt) : "None yet"}
                      </dd>
                    </div>
                  </div>
                </dl>

                <div className="mt-5">
                  {latestDeployment ? (
                    <Link
                      href={`/deployments/${latestDeployment.id}`}
                      className="inline-flex w-full items-center justify-center rounded border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
                    >
                      Open latest deployment
                    </Link>
                  ) : (
                    <div className="rounded border border-dashed border-slate-300 px-3 py-2 text-center text-sm text-muted">
                      No deployments recorded
                    </div>
                  )}
                </div>
              </article>
            );
          })}
        </section>
      )}
    </div>
  );
}

function LoadingGrid({ label }: { label: string }) {
  return (
    <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" role="status" aria-live="polite">
      {[0, 1, 2].map((item) => (
        <div key={item} className="rounded border border-slate-200 bg-white p-5 shadow-panel">
          <p className="text-sm text-muted">{item === 0 ? label : "\u00a0"}</p>
          <div className="mt-4 h-24 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </section>
  );
}

function EmptyState() {
  return (
    <div className="rounded border border-slate-200 bg-white p-6 shadow-panel">
      <h2 className="text-lg font-semibold text-ink">No projects found</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
        Start the local backend and run <code className="rounded bg-slate-100 px-1.5 py-0.5">./scripts/seed-demo.sh</code>{" "}
        from the repository root to create a recruiter-ready demo project, deployment, CI run, log, and AI job.
      </p>
    </div>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700" role="alert">
      <div className="font-semibold">Could not load projects</div>
      <p className="mt-1">{message}</p>
    </div>
  );
}
