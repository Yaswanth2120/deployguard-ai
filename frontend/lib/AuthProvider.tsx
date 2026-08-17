"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { generateCodeChallenge, generateCodeVerifier, generateState } from "@/lib/pkce";
import { clearSession, getSession, setSession, StoredSession } from "@/lib/authToken";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const GITHUB_CLIENT_ID = process.env.NEXT_PUBLIC_GITHUB_CLIENT_ID;
const VERIFIER_KEY = "deployguard.pkce_verifier";
const STATE_KEY = "deployguard.oauth_state";

type AuthContextValue = {
  user: StoredSession["user"] | null;
  isLoading: boolean;
  login: () => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function callbackRedirectUri() {
  return `${window.location.origin}/auth/callback`;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<StoredSession["user"] | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setUser(getSession()?.user ?? null);
    setIsLoading(false);
  }, []);

  const login = useCallback(async () => {
    if (!GITHUB_CLIENT_ID) {
      throw new Error("NEXT_PUBLIC_GITHUB_CLIENT_ID is not configured.");
    }

    const verifier = generateCodeVerifier();
    const challenge = await generateCodeChallenge(verifier);
    const state = generateState();

    window.sessionStorage.setItem(VERIFIER_KEY, verifier);
    window.sessionStorage.setItem(STATE_KEY, state);

    const params = new URLSearchParams({
      client_id: GITHUB_CLIENT_ID,
      redirect_uri: callbackRedirectUri(),
      scope: "read:user",
      state,
      code_challenge: challenge,
      code_challenge_method: "S256",
    });

    window.location.href = `https://github.com/login/oauth/authorize?${params.toString()}`;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>{children}</AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

export async function completeGithubLogin(code: string, state: string) {
  const expectedState = window.sessionStorage.getItem(STATE_KEY);
  const verifier = window.sessionStorage.getItem(VERIFIER_KEY);
  window.sessionStorage.removeItem(STATE_KEY);
  window.sessionStorage.removeItem(VERIFIER_KEY);

  if (!verifier || !expectedState || expectedState !== state) {
    throw new Error("Login state mismatch. Please try signing in again.");
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/github/callback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      code,
      codeVerifier: verifier,
      redirectUri: callbackRedirectUri(),
    }),
  });

  if (!response.ok) {
    let message = "GitHub sign-in failed.";
    try {
      const error = await response.json();
      message = error.message ?? message;
    } catch {
      // keep default message
    }
    throw new Error(message);
  }

  const session = (await response.json()) as StoredSession;
  setSession(session);
  return session;
}
