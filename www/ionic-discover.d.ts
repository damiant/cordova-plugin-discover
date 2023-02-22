export interface IonicDiscover {

    start(): promise<void>;

    stop(): promise<void>;

    getServices(): promise<ServiceList>;
}

export interface Service {
    path: string
    hostname: string
    id: string
    address: string
    port: number
    name: string
  }
  
  export interface ServiceList {
      services: service[];
  }