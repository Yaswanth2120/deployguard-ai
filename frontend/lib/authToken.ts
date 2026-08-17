const STORAGE_KEY = "deployguard.session";

export type StoredSession = {
  accessToken: string;
  expiresAt: string;
  user: {
    githubId: string;
    login: string;
    name: string | null;
    avatarUrl: string | null;
    role: string;
  };
};

let cachedSession: StoredSession | null = null;
let hydrated = false;

function hydrate(): StoredSession | null {
  if (hydrated) {
    return cachedSession;
  }
  hydrated = true;

  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as StoredSession;
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    cachedSession = parsed;
    return parsed;
  } catch {
    window.sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function getSession(): StoredSession | null {
  return hydrate();
}

export function setSession(session: StoredSession) {
  cachedSession = session;
  hydrated = true;
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

export function clearSession() {
  cachedSession = null;
  hydrated = true;
  if (typeof window !== "undefined") {
    window.sessionStorage.removeItem(STORAGE_KEY);
  }
}

export function getAccessToken(): string | null {
  return hydrate()?.accessToken ?? null;
}
