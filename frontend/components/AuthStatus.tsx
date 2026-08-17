"use client";

import { useState } from "react";
import { useAuth } from "@/lib/AuthProvider";

export function AuthStatus() {
  const { user, isLoading, login, logout } = useAuth();
  const [error, setError] = useState<string | null>(null);

  if (isLoading) {
    return null;
  }

  if (user) {
    return (
      <div className="flex items-center gap-3">
        <span className="text-sm text-muted">
          Signed in as <span className="font-semibold text-ink">{user.login}</span>
        </span>
        <button
          type="button"
          onClick={logout}
          className="rounded border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
        >
          Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      {error ? <span className="text-xs text-rose-700">{error}</span> : null}
      <button
        type="button"
        onClick={() => login().catch((err) => setError(err instanceof Error ? err.message : "Sign-in failed."))}
        className="rounded bg-ink px-3 py-2 text-sm font-medium text-white transition hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
      >
        Sign in with GitHub
      </button>
    </div>
  );
}
