// ============================================================================
// AgentHub API v1 Error Model, Unified Request Client, Retry, Cancel & Empty State
// ============================================================================

export const API_BASE = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "");

export const DEFAULT_WORKSPACE_PATH = "";
export const DEFAULT_PROJECT_ID = "";
export const DEFAULT_PREVIEW_URL = `${API_BASE}/api/deployments/preview/default`;

const TOKEN_KEY = "agenthub_token";

export function readAuthToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  code?: number;
  traceId?: string;
  retryable: boolean;

  constructor(message: string, status: number, code?: number, traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.traceId = traceId;
    this.retryable = status === 408 || status === 429 || status === 502 || status === 503 || status === 504;
  }

  isUnauthorized(): boolean {
    return this.status === 401 || this.code === 1001;
  }

  isForbidden(): boolean {
    return this.status === 403 || this.code === 1002;
  }

  isGone(): boolean {
    return this.status === 410;
  }
}

export interface EmptyResourceState<T> {
  items: T[];
  isEmpty: boolean;
  reason: string;
}

export function createEmptyResourceState<T>(reason = "当前上下文暂无资源记录"): EmptyResourceState<T> {
  return {
    items: [],
    isEmpty: true,
    reason,
  };
}

export interface RequestOptions extends RequestInit {
  idempotencyKey?: string;
  ifNoneMatch?: string;
  retries?: number;
  retryDelayMs?: number;
}

export function createCancelableRequest(): {
  signal: AbortSignal;
  cancel: (reason?: string) => void;
} {
  const controller = new AbortController();
  return {
    signal: controller.signal,
    cancel: (reason?: string) => controller.abort(reason),
  };
}

const etagCache = new Map<string, { etag: string; payload: unknown }>();

export async function fetchJson<T>(url: string, options?: RequestOptions): Promise<T> {
  const method = (options?.method || "GET").toUpperCase();
  const maxRetries = options?.retries ?? (method === "GET" ? 1 : 0);
  const retryDelayMs = options?.retryDelayMs ?? 250;

  let attempt = 0;
  while (true) {
    try {
      const token = readAuthToken();
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
        "X-API-Version": "v1",
        ...((options?.headers as Record<string, string>) || {}),
      };

      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      if (options?.idempotencyKey) {
        headers["Idempotency-Key"] = options.idempotencyKey;
      }
      const cachedEntry = method === "GET" ? etagCache.get(url) : undefined;
      if (options?.ifNoneMatch) {
        headers["If-None-Match"] = options.ifNoneMatch;
      } else if (cachedEntry?.etag) {
        headers["If-None-Match"] = cachedEntry.etag;
      }

      const res = await fetch(url, {
        ...options,
        credentials: "include",
        headers,
      });

      if (res.status === 304 && cachedEntry) {
        return cachedEntry.payload as T;
      }

      if (res.status === 401) {
        if (typeof window !== "undefined") {
          window.dispatchEvent(new CustomEvent("agenthub:unauthorized"));
        }
        throw new ApiError("认证失效或未登录，请登录后继续操作", 401, 1001);
      }

      if (res.status === 403) {
        throw new ApiError("访问拒绝：无权操作该资源", 403, 1002);
      }

      if (!res.ok) {
        let errMessage = `HTTP 请求失败 (${res.status})`;
        let errCode: number | undefined;
        let traceId: string | undefined;
        try {
          const errJson = await res.json();
          if (errJson.message) errMessage = errJson.message;
          if (errJson.code) errCode = errJson.code;
          if (errJson.traceId) traceId = errJson.traceId;
        } catch {}
        throw new ApiError(errMessage, res.status, errCode, traceId);
      }

      const json = await res.json();
      const data = (json.data !== undefined ? json.data : json) as T;
      const responseEtag = res.headers.get("ETag");
      if (method === "GET" && responseEtag) {
        etagCache.set(url, { etag: responseEtag, payload: data });
      }
      return data;
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        throw new ApiError("请求已取消", 499);
      }
      const isRetryable =
        err instanceof ApiError
          ? err.retryable
          : method === "GET";
      if (attempt < maxRetries && isRetryable) {
        attempt++;
        await new Promise((resolve) => setTimeout(resolve, retryDelayMs * attempt));
        continue;
      }
      throw err;
    }
  }
}
