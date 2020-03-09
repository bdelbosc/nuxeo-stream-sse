# Nuxeo-stream-sse

This is a proof of concept **NOT FOR PRODUCTION**.


## About

Nuxeo Stream is a server side stream processor that relies on Apache Kafka.
Client can submits asynchronous processing command and request the state using a REST API,
but it will be better to get continuous and realtime feedback instead
of having to periodically pull the state.

## Goals

1. Propagates Nuxeo Stream records to the client side.
2. Support a massive number of clients per front node and scale horizontally.


## Design Decisions

We need a one way communication from server to client.
Server Sent Event is supported by all browsers and is exactly designed for this.


To scale on the number of client it requires async NIO and a multi reactor pattern in order to handle a high number of concurrent requests.
Vert.x is a solid solution that should support thousands of connections per node with very few resources.


### Rejected alternatives 

- Use helidon and rxJava with an observer pattern:
    - pros: we could run Nuxeo Runtime/Core as micro service on the same instance  
    - cons: much more complex than vert.x event-bus

- Use Akka:
    - pros:
    - cons: actors is a complex pattern just for this specific need

## Usage

Clients subscribe to streams using a REST API and receive records from the stream in real time:

```bash
# subscribe to the stream "command" which is the Bulk Service Command Stream
curl -XGET http://localhost:8888/subscribe/command

Record{watermark=207576477653008384, wmDate=2020-03-08 16:54:03.422, flags=[DEFAULT], key='380c06ff-5d59-4899-98dc-383355e4bb96', data.length=314, data="....5.....H380c06ff-5d59-4899-98dc-383355e4bb96.csvExport..SELECT * FROM Document WHERE ecm:parentId = '2da04904-9675-475e-a2c0"}

Record{watermark=207576481285931008, wmDate=2020-03-08 16:54:31.139, flags=[DEFAULT], key='f12642c1-415e-44c4-ad5f-260a99419ffd', data.length=314, data="....5.....Hf12642c1-415e-44c4-ad5f-260a99419ffd.csvExport..SELECT * FROM Document WHERE ecm:parentId = '2da04904-9675-475e-a2c0"}

...
```

There is a single Worker thread that read records using the Nuxeo Stream Lib,
records are forwarded to the internal pub-sub Vert.X event bus.

Client subscription is done by Handlers (could be thousands) receive records from the event bus
and propagates downstream using SSE. 


## TODO

- use a fixed pool of consumer with a shared LogManager
- decode avro message and propagate JSON body to client (Bulk Status, Bulk Command)
- add a heart beat to make sure connection is active
- subscribe on demand to any stream
- create a simple client application to introspect Nuxeo Stream activity
- gatling test to check limits


# Help

* [Vert.x Documentation](https://vertx.io/docs/)
* [Server Sent Events](https://en.wikipedia.org/wiki/Server-sent_events)
* [Nuxeo Stream](https://doc.nuxeo.com/nxdoc/nuxeo-stream/)

# About Nuxeo

Nuxeo provides a modular, extensible Java-based
[open source software platform for enterprise content management](http://www.nuxeo.com/en/products/ep)
and packaged applications for
[document management](http://www.nuxeo.com/en/products/document-management),
[digital asset management](http://www.nuxeo.com/en/products/dam) and
[case management](http://www.nuxeo.com/en/products/case-management). Designed
by developers for developers, the Nuxeo platform offers a modern
architecture, a powerful plug-in model and extensive packaging
capabilities for building content applications.

More information on: <http://www.nuxeo.com/>
