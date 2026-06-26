export type Env = {
  port: number;
  databasePath: string;
  publicBaseUrl: string;
  webhookSecret?: string;
  pokeJsonRpcUrl?: string;
  pokeJsonRpcBearerToken?: string;
};

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return {
    port: Number(source.PORT ?? 8787),
    databasePath: source.DATABASE_PATH ?? "./data/poke-client.db",
    publicBaseUrl: source.PUBLIC_BASE_URL ?? `http://localhost:${source.PORT ?? 8787}`,
    webhookSecret: emptyToUndefined(source.WEBHOOK_SECRET),
    pokeJsonRpcUrl: emptyToUndefined(source.POKE_JSON_RPC_URL),
    pokeJsonRpcBearerToken: emptyToUndefined(source.POKE_JSON_RPC_BEARER_TOKEN)
  };
}

function emptyToUndefined(value: string | undefined): string | undefined {
  return value && value.trim().length > 0 ? value : undefined;
}
