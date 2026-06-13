import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { aiSummary, ciSignals, getDeployment, logs } from "@/lib/mockData";

export default async function DeploymentDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const deployment = getDeployment(id);

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-ink">{deployment.serviceName}</h1>
          <p className="mt-2 text-sm text-muted">
            Deployment {deployment.id} · {deployment.commitSha} · {deployment.deployedAt}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={deployment.status} />
          <RiskBadge level={deployment.riskLevel} />
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="rounded border border-slate-200 bg-white p-4 shadow-panel">
          <h2 className="text-sm font-semibold text-ink">Deployment metadata</h2>
          <dl className="mt-4 grid gap-3 text-sm">
            {[
              ["Environment", deployment.environment],
              ["Branch", deployment.branch],
              ["Commit", deployment.commitSha],
              ["Deployed by", deployment.deployedBy],
              ["AI analysis", deployment.aiAnalysisStatus],
            ].map(([label, value]) => (
              <div key={label} className="grid grid-cols-[120px_1fr] gap-3">
                <dt className="text-slate-500">{label}</dt>
                <dd className="font-medium text-ink">{value}</dd>
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
                Mock risk score calculated from CI/CD failures, failed tests, production context, branch naming, and error logs.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-ink">CI/CD signals</h2>
          </div>
          <div className="divide-y divide-slate-100">
            {ciSignals.map((signal) => (
              <div key={signal.label} className="grid gap-1 px-4 py-3 sm:grid-cols-[140px_90px_1fr] sm:items-center">
                <div className="font-medium text-ink">{signal.label}</div>
                <StatusBadge status={signal.status} />
                <div className="text-sm text-muted">{signal.detail}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded border border-slate-200 bg-white shadow-panel">
          <div className="border-b border-slate-200 px-4 py-3">
            <h2 className="text-sm font-semibold text-ink">Logs</h2>
          </div>
          <div className="divide-y divide-slate-100">
            {logs.map((log) => (
              <div key={`${log.level}-${log.message}`} className="px-4 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge status={log.level} />
                  <span className="text-sm font-medium text-ink">{log.service}</span>
                </div>
                <p className="mt-2 text-sm text-muted">{log.message}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="rounded border border-slate-200 bg-white p-4 shadow-panel">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-sm font-semibold text-ink">AI incident summary</h2>
          <div className="flex gap-2">
            <RiskBadge level={aiSummary.severity} />
            <StatusBadge status={aiSummary.confidence} />
          </div>
        </div>
        <div className="mt-4 grid gap-5 lg:grid-cols-3">
          <div>
            <h3 className="text-xs font-semibold uppercase text-slate-500">Summary</h3>
            <p className="mt-2 text-sm text-slate-700">{aiSummary.summary}</p>
          </div>
          <div>
            <h3 className="text-xs font-semibold uppercase text-slate-500">Likely root cause</h3>
            <p className="mt-2 text-sm text-slate-700">{aiSummary.likelyRootCause}</p>
          </div>
          <div>
            <h3 className="text-xs font-semibold uppercase text-slate-500">Recommended actions</h3>
            <ul className="mt-2 space-y-2 text-sm text-slate-700">
              {aiSummary.recommendedActions.map((action) => (
                <li key={action}>{action}</li>
              ))}
            </ul>
          </div>
        </div>
        <div className="mt-5">
          <h3 className="text-xs font-semibold uppercase text-slate-500">Evidence</h3>
          <div className="mt-2 flex flex-wrap gap-2">
            {aiSummary.evidence.map((item) => (
              <span key={item} className="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {item}
              </span>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
