import type { ClientEnvelope, ServerEnvelope } from "../protocol/messages";

export type MessageHandler = (message: ServerEnvelope) => void;
export interface ConnectionHandlers {
  onOpen?: () => void;
  onClose?: () => void;
  onError?: () => void;
}

export class GameSocket {
  private socket: WebSocket | null = null;

  connect(url: string, onMessage: MessageHandler, handlers: ConnectionHandlers = {}) {
    this.socket = new WebSocket(url);
    this.socket.addEventListener("open", () => handlers.onOpen?.());
    this.socket.addEventListener("close", () => handlers.onClose?.());
    this.socket.addEventListener("error", () => handlers.onError?.());
    this.socket.addEventListener("message", (event) => {
      const parsed = JSON.parse(event.data) as ServerEnvelope;
      onMessage(parsed);
    });
    return this.socket;
  }

  send(message: ClientEnvelope) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("WebSocket is not connected.");
    }
    this.socket.send(JSON.stringify(message));
  }

  close() {
    this.socket?.close();
    this.socket = null;
  }
}
