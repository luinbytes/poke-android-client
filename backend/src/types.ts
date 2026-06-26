export type EventType =
  | "message"
  | "action"
  | "status"
  | "log"
  | "progress"
  | "notification"
  | "incoming_request";

export type DeliveryState = "queued" | "delivered" | "failed";

export type ConversationEvent = {
  eventId: string;
  pokeUserId: string;
  deviceId?: string | null;
  eventType: EventType;
  payload: unknown;
  correlationId?: string | null;
  createdAt: string;
  deliveryState: DeliveryState;
};

export type Device = {
  deviceId: string;
  pokeUserId: string;
  fcmToken?: string | null;
  deviceName?: string | null;
  active: boolean;
  lastSeenAt: string;
};

export type HandlerKind = "incoming_request" | "log" | "progress" | "notification";
