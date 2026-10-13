// ============================================================================
// AgentHub Identity & Authentication API Module (Bounded Context: identity / ops)
// ============================================================================

import { AuthTokenView, UserView } from "@/types";
import { API_BASE, fetchJson } from "./errors";

const TOKEN_KEY = "agenthub_token";
const USER_KEY = "agenthub_user";
const DEMO_MODE_KEY = "agenthub_demo_mode";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (typeof window === "undefined") return;
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
    document.cookie = `agenthub_token=${encodeURIComponent(token)}; path=/; max-age=86400; SameSite=Lax`;
  } else {
    localStorage.removeItem(TOKEN_KEY);
    document.cookie = `agenthub_token=; path=/; max-age=0; SameSite=Lax`;
  }
}

export function getStoredUser(): UserView | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setStoredUser(user: UserView | null): void {
  if (typeof window === "undefined") return;
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(USER_KEY);
  }
}

export function isDemoMode(): boolean {
  if (typeof window === "undefined") {
    return process.env.NEXT_PUBLIC_AGENTHUB_DEMO_MODE === "true";
  }
  const override = localStorage.getItem(DEMO_MODE_KEY);
  if (override !== null) {
    return override === "true";
  }
  return process.env.NEXT_PUBLIC_AGENTHUB_DEMO_MODE === "true";
}

export function setDemoMode(enabled: boolean): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(DEMO_MODE_KEY, String(enabled));
}

export const authApi = {
  async getHealth(): Promise<{ status: string; uptime?: string; version?: string; apiVersion?: string; timezone?: string }> {
    return fetchJson(`${API_BASE}/api/system/health`);
  },

  async login(email: string, password: string): Promise<AuthTokenView> {
    const res = await fetchJson<AuthTokenView>(`${API_BASE}/api/auth/login`, {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (res && res.token) {
      setStoredToken(res.token);
      setStoredUser(res.user);
    }
    return res;
  },

  async register(email: string, password: string, displayName?: string): Promise<AuthTokenView> {
    const res = await fetchJson<AuthTokenView>(`${API_BASE}/api/auth/register`, {
      method: "POST",
      body: JSON.stringify({ email, password, displayName: displayName || email.split("@")[0] }),
    });
    if (res && res.token) {
      setStoredToken(res.token);
      setStoredUser(res.user);
    }
    return res;
  },

  async logout(): Promise<void> {
    try {
      await fetchJson(`${API_BASE}/api/auth/logout`, { method: "POST" });
    } catch {}
    setStoredToken(null);
    setStoredUser(null);
  },

  async createStreamTicket(): Promise<string> {
    const data = await fetchJson<{ ticket: string } | string>(`${API_BASE}/api/auth/stream-ticket`, {
      method: "POST",
    });
    if (typeof data === "string") return data;
    return data.ticket;
  },
};
