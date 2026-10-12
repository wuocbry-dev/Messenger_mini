// Types for the browser build of stompjs 2.3.x (`stompjs/lib/stomp.js`).
// The package root (`stompjs`) also pulls in Node-only TCP support, so the
// app imports the lib file directly.
declare module 'stompjs/lib/stomp.js' {
  export interface Frame {
    command: string;
    body: string;
    headers: Record<string, string>;
  }

  export interface Client {
    connect(
      headers: Record<string, string>,
      connectCallback: (frame?: Frame) => void,
      errorCallback?: (error: Frame | string) => void
    ): void;
    subscribe(destination: string, callback: (frame: Frame) => void): { id: string; unsubscribe: () => void };
    send(destination: string, headers?: Record<string, string>, body?: string): void;
    disconnect(callback?: () => void): void;
    debug: ((message: string) => void) | null;
    connected: boolean;
  }

  export const Stomp: {
    client(url: string, protocols?: string[]): Client;
    over(ws: WebSocket): Client;
  };
}
