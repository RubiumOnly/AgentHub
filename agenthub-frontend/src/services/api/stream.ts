// ============================================================================
// AgentHub SSE Event Stream Module (Bounded Context: execution)
// Authoritative SSE endpoint: /api/executions/runs/{runId}/stream
// ============================================================================

import { authApi } from "./auth";
import { API_BASE } from "./errors";

export interface StreamConnectionOptions {
  runId: string;
  lastEventId?: number;
  onOpen?: () => void;
  onMessage?: (event: MessageEvent) => void;
  onError?: (err: Event) => void;
}

export const streamApi = {
  buildRunStreamUrl(runId: string, lastEventId = 0, ticket?: string): string {
    const ticketParam = ticket ? `&ticket=${encodeURIComponent(ticket)}` : "";
    return `${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/stream?lastEventId=${lastEventId}${ticketParam}`;
  },

  async connectRunStream(options: StreamConnectionOptions): Promise<EventSource> {
    let ticket: string | undefined;
    try {
      ticket = await authApi.createStreamTicket();
    } catch {
      ticket = undefined;
    }
    const url = streamApi.buildRunStreamUrl(options.runId, options.lastEventId ?? 0, ticket);
    const es = new EventSource(url, { withCredentials: true });
    if (options.onOpen) es.onopen = options.onOpen;
    if (options.onMessage) es.onmessage = options.onMessage;
    if (options.onError) es.onerror = options.onError;
    return es;
  },
};
