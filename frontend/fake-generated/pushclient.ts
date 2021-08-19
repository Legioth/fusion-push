export interface EndpointGenerator<T> extends AsyncIterable<T> {
  close(): void;
}

const channels = new Map<string, Channel>();

class Channel {
  readonly name: string;
  private inited: Promise<WebSocket>
  private nextId = 0;
  private handlers = new Map<number, [(item: any) => void, (reason?: any) => void]>();

  constructor(name: string) {
    this.name = name;

    this.inited = new Promise((resolve, reject) => {
      const socket = new WebSocket(location.origin.replace(/^http/, 'ws') + "/" + name);
      socket.onerror = (event) => {
        reject(event);
      }
      socket.onopen = (event) => {
        resolve(socket);
      }
      socket.onmessage = (event) => {
        const message = JSON.parse(event.data);
        const id = message.id;
        const handler = this.handlers.get(id);
        if (!handler) {
          console.log("No handler for stream id " + id);
          return;
        }
        handler[0](message);
      };
    });
  }

  async *open(method: string, args: any[]): AsyncIterable<any> {
    const id = this.nextId++;
    const socket = await this.inited;

    socket.send(JSON.stringify({ id, method, args }));

    while(true) {
      const nextMessage = await new Promise<any>((resolve, reject) => {
        this.handlers.set(id, [resolve, reject]);
      });
      if (nextMessage.done) {
        return;
      } else {
        yield nextMessage;
      }
    }
  }
}

export function open<T>(endpointName: string, methodName: string, args: any[] = []): EndpointGenerator<T> {
  return {
    close() {
      throw new Error('Not implemented');
    },

    async *[Symbol.asyncIterator]() {
      if (!channels.has(endpointName)) {
        channels.set(endpointName, new Channel(endpointName));
      }
      const channel = channels.get(endpointName)!

      for await (const message of channel.open(methodName, args)) {
        yield message.item as T;
      }
    },
  };
}
