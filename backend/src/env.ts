export type Env = {
  port: number;
  databasePath: string;
  webhookSecret?: string;
  pokeApiKey?: string;
};

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return {
    port: Number(source.PORT ?? 8787),
    databasePath: source.DATABASE_PATH ?? "./data/poke-client.db",
    webhookSecret: emptyToUndefined(source.WEBHOOK_SECRET),
    pokeApiKey: emptyToUndefined(source.POKE_API_KEY)
  };
}

function emptyToUndefined(value: string | undefined): string | undefined {
  return value && value.trim().length > 0 ? value : undefined;
}
