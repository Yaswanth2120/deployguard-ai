"use client";

import { FormEvent, useState } from "react";
import { RiskBadge } from "@/components/RiskBadge";
import { getPullRequestRiskScore, PullRequestRiskScore } from "@/lib/api";

export default function RiskScoresPage() {
  const [pullRequestId, setPullRequestId] = useState("");
  const [riskScore, setRiskScore] = useState<PullRequestRiskScore | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      setRiskScore(await getPullRequestRiskScore(pullRequestId.trim()));
    } catch (requestError) {
      setRiskScore(null);
      setError(requestError instanceof Error ? requestError.message : "Could not load the risk score.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl space-y-6">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-sky-700">Pull request intelligence</p>
        <h1 className="mt-2 text-3xl font-semibold text-ink">Risk score lookup</h1>
        <p className="mt-2 text-sm text-muted">Retrieve the latest deterministic score for an ingested pull request.</p>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-3 rounded border border-slate-200 bg-white p-5 shadow-panel sm:flex-row">
        <label className="sr-only" htmlFor="pull-request-id">Pull request ID</label>
        <input id="pull-request-id" required value={pullRequestId} onChange={(event) => setPullRequestId(event.target.value)} placeholder="Pull request UUID" className="min-w-0 flex-1 rounded border border-slate-300 px-3 py-2 text-sm" />
        <button disabled={loading} className="rounded bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60">{loading ? "Loading…" : "Load score"}</button>
      </form>
      {error ? <p role="alert" className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{error}</p> : null}
      {riskScore ? <article className="rounded border border-slate-200 bg-white p-6 shadow-panel">
        <div className="flex items-center justify-between gap-4"><div><p className="text-sm text-muted">Risk score</p><p className="mt-1 text-4xl font-semibold text-ink">{riskScore.score}<span className="text-lg text-muted">/100</span></p></div><RiskBadge level={riskScore.riskLevel} /></div>
        <p className="mt-5 text-sm text-slate-700">{riskScore.rationale}</p>
        <h2 className="mt-6 text-sm font-semibold text-ink">Contributing factors</h2>
        {riskScore.factors.length ? <ul className="mt-3 space-y-2">{riskScore.factors.map((factor) => <li key={factor.name} className="rounded bg-slate-50 p-3 text-sm text-slate-700"><span className="font-semibold text-ink">+{factor.contribution} {factor.name}</span><span className="block text-muted">{factor.detail}</span></li>)}</ul> : <p className="mt-2 text-sm text-muted">No correlated risk factors.</p>}
      </article> : null}
    </section>
  );
}
