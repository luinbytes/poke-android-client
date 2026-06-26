export type JsonRpcRequest = {
  jsonrpc: "2.0";
  id: string | number;
  method: string;
  params?: unknown;
};

export class JsonRpcClient {
  constructor(
    private readonly endpoint?: string,
    private readonly bearerToken?: string
  ) {}

  get configured(): boolean {
    return Boolean(this.endpoint);
  }

  async call<T = unknown>(method: string, params?: unknown): Promise<T> {
    if (!this.endpoint) {
      throw new Error("POKE_JSON_RPC_URL is not configured");
    }
    const request: JsonRpcRequest = {
      jsonrpc: "2.0",
      id: crypto.randomUUID(),
      method,
      params
    };
    const response = await fetch(this.endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(this.bearerToken ? { authorization: `Bearer ${this.bearerToken}` } : {})
      },
      body: JSON.stringify(request)
    });
    if (!response.ok) {
      throw new Error(`JSON-RPC HTTP ${response.status}`);
    }
    const body = await response.json() as { result?: T; error?: { message?: string } };
    if (body.error) {
      throw new Error(body.error.message ?? "JSON-RPC call failed");
    }
    return body.result as T;
  }
}
