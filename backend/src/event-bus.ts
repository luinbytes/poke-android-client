import type { ConversationEvent } from "./types.js";

export class EventBus {
  private readonly listeners = new Map<string, Set<(event: ConversationEvent) => void>>();

  subscribe(pokeUserId: string, listener: (event: ConversationEvent) => void): () => void {
    const listeners = this.listeners.get(pokeUserId) ?? new Set();
    listeners.add(listener);
    this.listeners.set(pokeUserId, listeners);
    return () => {
      listeners.delete(listener);
      if (listeners.size === 0) {
        this.listeners.delete(pokeUserId);
      }
    };
  }

  publish(event: ConversationEvent): void {
    const listeners = this.listeners.get(event.pokeUserId);
    if (!listeners) return;
    for (const listener of listeners) {
      listener(event);
    }
  }
}
