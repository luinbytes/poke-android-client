export type PokeSendResult = {
  ok: boolean;
  message?: string;
  upstreamStatus?: number;
  upstreamBody?: string;
};

export type PokeMessageSender = {
  configured: boolean;
  sendMessage(text: string): Promise<PokeSendResult>;
};

export class PokeApiClient implements PokeMessageSender {
  constructor(
    private readonly apiKey?: string,
    private readonly endpoint = "https://poke.com/api/v1/inbound/api-message"
  ) {}

  get configured(): boolean {
    return Boolean(this.apiKey);
  }

  async sendMessage(text: string): Promise<PokeSendResult> {
    if (!this.apiKey) {
      return {
        ok: false,
        message: "POKE_API_KEY is not configured on the backend"
      };
    }

    let response: Response;
    try {
      response = await fetch(this.endpoint, {
        method: "POST",
        headers: {
          authorization: `Bearer ${this.apiKey}`,
          "content-type": "application/json"
        },
        body: JSON.stringify({ message: text })
      });
    } catch (error) {
      return {
        ok: false,
        message: `Poke API unavailable: ${error instanceof Error ? error.message : "network error"}`
      };
    }

    const upstreamBody = await response.text();
    if (response.ok) {
      return {
        ok: true,
        upstreamStatus: response.status,
        upstreamBody: upstreamBody || undefined
      };
    }

    return {
      ok: false,
      upstreamStatus: response.status,
      upstreamBody: upstreamBody || undefined,
      message: describePokeError(response.status, upstreamBody)
    };
  }
}

function describePokeError(status: number, body: string): string {
  if (status === 401 || status === 403) return "Poke API rejected the backend key";
  if (status === 429) return "Poke API rate limited this backend; try again shortly";
  if (status >= 500) return "Poke API is temporarily unavailable";
  if (status === 400 || status === 422) {
    return `Poke API rejected the message payload${body ? `: ${body.slice(0, 160)}` : ""}`;
  }
  return `Poke API returned HTTP ${status}${body ? `: ${body.slice(0, 160)}` : ""}`;
}
