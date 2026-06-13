import Link from "next/link";
import { RiskBadge } from "@/components/RiskBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { deployments } from "@/lib/mockData";

export default function DeploymentsPage() {
  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-semibold text-ink">Deployments</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">
          Mock deployment list with risk scoring and AI analysis state.
        </p>
      </section>

      <section className="rounded border border-slate-200 bg-white shadow-panel">
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
                  <td className="px-4 py-3 font-medium text-ink">{deployment.serviceName}</td>
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
      </section>
    </div>
  );
}
