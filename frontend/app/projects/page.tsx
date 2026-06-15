"use client";

import { useEffect, useState } from "react";
import { formatDateTime, getProjects, Project } from "@/lib/api";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadProjects() {
      try {
        setIsLoading(true);
        setError(null);
        const data = await getProjects();
        if (isMounted) {
          setProjects(data);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load projects.");
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    loadProjects();
    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-semibold text-ink">Projects</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">
          Projects fetched from the local DeployGuard API.
        </p>
      </section>

      {error ? <ErrorPanel message={error} /> : null}

      {isLoading ? (
        <div className="rounded border border-slate-200 bg-white p-4 text-sm text-muted shadow-panel">Loading projects...</div>
      ) : projects.length === 0 ? (
        <div className="rounded border border-slate-200 bg-white p-4 text-sm text-muted shadow-panel">No projects found.</div>
      ) : (
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {projects.map((project) => (
            <article key={project.id} className="rounded border border-slate-200 bg-white p-4 shadow-panel">
              <div>
                <h2 className="text-base font-semibold text-ink">{project.name}</h2>
                <p className="mt-1 text-sm text-muted">{project.serviceName}</p>
              </div>
              <dl className="mt-5 grid gap-3 text-sm">
                <div>
                  <dt className="text-xs uppercase text-slate-500">Repository</dt>
                  <dd className="mt-1 break-words font-medium text-ink">{project.githubRepoUrl}</dd>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <dt className="text-xs uppercase text-slate-500">Created</dt>
                    <dd className="mt-1 font-semibold text-ink">{formatDateTime(project.createdAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs uppercase text-slate-500">Updated</dt>
                    <dd className="mt-1 font-semibold text-ink">{formatDateTime(project.updatedAt)}</dd>
                  </div>
                </div>
              </dl>
            </article>
          ))}
        </section>
      )}
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
