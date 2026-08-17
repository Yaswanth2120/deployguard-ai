"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { completeGithubLogin } from "@/lib/AuthProvider";

function AuthCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get("code");
    const state = searchParams.get("state");
    const oauthError = searchParams.get("error_description") ?? searchParams.get("error");

    if (oauthError) {
      setError(oauthError);
      return;
    }

    if (!code || !state) {
      setError("Missing authorization code from GitHub.");
      return;
    }

    completeGithubLogin(code, state)
      .then(() => router.replace("/"))
      .catch((err) => setError(err instanceof Error ? err.message : "Sign-in failed."));
  }, [router, searchParams]);

  return (
    <div className="mx-auto max-w-md rounded border border-slate-200 bg-white p-6 text-center shadow-panel">
      {error ? (
        <>
          <p className="text-sm font-semibold text-rose-700">Sign-in failed</p>
          <p className="mt-2 text-sm text-muted">{error}</p>
        </>
      ) : (
        <p className="text-sm text-muted">Completing GitHub sign-in...</p>
      )}
    </div>
  );
}

export default function AuthCallbackPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-md p-6 text-center text-sm text-muted">Loading...</div>}>
      <AuthCallbackContent />
    </Suspense>
  );
}
