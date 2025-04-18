# cordova-plugin-discover

## Installation

```shell
npm install @webnativellc/cordova-plugin-discover
```

Create a file called `discover.ts`:
```typescript
export interface Service {
  path: string
  hostname: string
  id: string
  address: string
  port: number
  name: string
  secure: boolean
}

export const IonicDiscover = (window as any).IonicDiscover;
```

Start checking for broadcasts:
```typescript
import { IonicDiscover, Service } from '../discovery';

...

IonicDiscover.start();
setInterval(async () => { 
    const data = await IonicDiscover.getServices();
    console.log(data?.services);
    }, 2000);
```

The above code will console log a `Service[]`

## Server Side

You can broadcast via UDP service information for the plugin with:
```typescript
        const p: Publisher = new Publisher('devapp', 'my-app-name', 8100);
        p.start();
```

Create a file called `discover.ts` and paste:
```typescript
import * as os from 'os';
import * as dgram from 'dgram';
import * as events from 'events';

import { Netmask } from 'netmask';

const PREFIX = 'ION_DP';
const PORT = 41234;

export interface Interface {
  address: string;
  broadcast: string;
}

export interface IPublisher {
  emit(event: 'error', err: Error): boolean;
  on(event: 'error', listener: (err: Error) => void): this;
}

export class Publisher extends events.EventEmitter implements IPublisher {
  id: string;
  path = '/';
  running = false;
  interval = 2000;

  timer?: any;
  client?: dgram.Socket;
  interfaces?: Interface[];

  constructor(public namespace: string, public name: string, public port: number) {
    super();

    if (name.indexOf(':') >= 0) {
      console.warn('name should not contain ":"');
      name = name.replace(':', ' ');
    }

    this.id = String(Math.round(Math.random() * 1000000));
  }

  start(): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      if (this.running) {
        return resolve();
      }

      this.running = true;

      if (!this.interfaces) {
        this.interfaces = this.getInterfaces();
      }

      const client = (this.client = dgram.createSocket('udp4'));

      client.on('error', (err) => {
        this.emit('error', err);
      });

      client.on('listening', () => {
        client.setBroadcast(true);
        this.timer = setInterval(this.sayHello.bind(this), this.interval);
        this.sayHello();
        resolve();
      });

      client.bind();
    });
  }

  stop() {
    if (!this.running) {
      return;
    }

    this.running = false;

    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }

    if (this.client) {
      this.client.close();
      this.client = undefined;
    }
  }

  buildMessage(ip: string): string {
    const now = Date.now();
    const message = {
      t: now,
      id: this.id,
      nspace: this.namespace,
      name: this.name,
      host: os.hostname(),
      ip: ip,
      port: this.port,
      path: this.path,
      secure: this.secure
    };
    return PREFIX + JSON.stringify(message);
  }

  getInterfaces(): Interface[] {
    return prepareInterfaces(os.networkInterfaces());
  }

  private sayHello() {
    if (!this.interfaces) {
      throw new Error('No network interfaces set--was the service started?');
    }

    try {
      for (const iface of this.interfaces) {
        const message = new Buffer(this.buildMessage(iface.address));

        this.client!.send(message, 0, message.length, PORT, iface.broadcast, (err) => {
          if (err) {
            this.emit('error', err);
          }
        });
      }
    } catch (e) {
      this.emit('error', e);
    }
  }
}

export function prepareInterfaces(interfaces: any): Interface[] {
  const set = new Set<string>();
  return Object.keys(interfaces)
    .map((key) => interfaces[key] as any[])
    .reduce((prev, current) => prev.concat(current))
    .filter((iface) => iface.family === 'IPv4')
    .map((iface) => {
      return {
        address: iface.address,
        broadcast: computeBroadcastAddress(iface.address, iface.netmask),
      };
    })
    .filter((iface) => {
      if (!set.has(iface.broadcast)) {
        set.add(iface.broadcast);
        return true;
      }
      return false;
    });
}

export function newSilentPublisher(namespace: string, name: string, port: number): Publisher {
  name = `${name}@${port}`;
  const service = new Publisher(namespace, name, port);
  service.on('error', (error) => {
    console.log(error);
  });
  service.start().catch((error) => {
    console.log(error);
  });
  return service;
}

export function computeBroadcastAddress(address: string, netmask: string): string {
  const ip = address + '/' + netmask;
  const block = new Netmask(ip);
  return block.broadcast;
}

```

