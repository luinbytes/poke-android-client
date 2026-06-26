import type { ConversationEvent, Device } from "./types.js";

export interface PushGateway {
  send(devices: Device[], event: ConversationEvent): Promise<void>;
}

export class LoggingPushGateway implements PushGateway {
  async send(devices: Device[], event: ConversationEvent): Promise<void> {
    if (devices.length === 0) {
      return;
    }
    console.info("push queued", {
      eventId: event.eventId,
      pokeUserId: event.pokeUserId,
      deviceCount: devices.length
    });
  }
}
