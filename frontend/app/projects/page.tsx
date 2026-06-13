import { projects } from "@/lib/mockData";

export default function ProjectsPage() {
  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-semibold text-ink">Projects</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">
          Mock service registry for deployment monitoring and incident analysis.
        </p>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {projects.map((project) => (
          <article key={project.id} className="rounded border border-slate-200 bg-white p-4 shadow-panel">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-ink">{project.name}</h2>
                <p className="mt-1 text-sm text-muted">{project.serviceName}</p>
              </div>
              <span className="rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-600">
                {project.owner}
              </span>
            </div>
            <dl className="mt-5 grid gap-3 text-sm">
              <div>
                <dt className="text-xs uppercase text-slate-500">Repository</dt>
                <dd className="mt-1 font-medium text-ink">{project.repo}</dd>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <dt className="text-xs uppercase text-slate-500">Deployments</dt>
                  <dd className="mt-1 font-semibold text-ink">{project.deployments}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase text-slate-500">Last deployment</dt>
                  <dd className="mt-1 font-semibold text-ink">{project.lastDeployment}</dd>
                </div>
              </div>
            </dl>
          </article>
        ))}
      </section>
    </div>
  );
}
