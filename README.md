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
# subscribe to multiple streams from the Bulk Service, timer is a special stream to get a heart beat
curl -XGET http://localhost:8888/subscribe/command+status+done+timer
stream: timer
message: {"now": "2020-03-09T15:37:36.809280Z"}

stream: command
message: {"id": "e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d", "action": "csvExport", "query": "SELECT * FROM Document WHERE ecm:parentId = 'b9ac4cbc-2be0-47e4-9d5f-bd515e6d3050' AND ecm:isTrashed = 0 AND (ecm:isVersion = 0 AND ecm:mixinType != 'HiddenInNavigation')", "username": "Administrator", "repository": "default", "bucketSize": 100, "batchSize": 50, "scroller": "elastic", "params": "{\"schemas\":[\"dublincore\",\"common\",\"uid\",\"file\"]}"}

stream: status
message: {"commandId": "e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d", "action": null, "username": null, "delta": true, "errorCount": 0, "errorMessage": null, "processed": null, "state": "SCROLLING_RUNNING", "submitTime": null, "scrollStartTime": 1583768260567, "scrollEndTime": null, "processingStartTime": null, "processingEndTime": null, "completedTime": null, "total": null, "processingDurationMillis": null, "result": null}

stream: status
message: {"commandId": "e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d", "action": null, "username": null, "delta": true, "errorCount": 0, "errorMessage": null, "processed": null, "state": "RUNNING", "submitTime": null, "scrollStartTime": null, "scrollEndTime": 1583768260634, "processingStartTime": null, "processingEndTime": null, "completedTime": null, "total": 999, "processingDurationMillis": null, "result": null}

stream: done
message: {"commandId": "e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d", "action": "csvExport", "username": "Administrator", "delta": false, "errorCount": 0, "errorMessage": null, "processed": 999, "state": "COMPLETED", "submitTime": 1583768260546, "scrollStartTime": 1583768260567, "scrollEndTime": 1583768260634, "processingStartTime": null, "processingEndTime": null, "completedTime": 1583768261517, "total": 999, "processingDurationMillis": null, "result": "{\"url\":\"nxbigblob/e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d\"}"}

stream: status
message: {"commandId": "e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d", "action": null, "username": null, "delta": true, "errorCount": 0, "errorMessage": null, "processed": 999, "state": null, "submitTime": null, "scrollStartTime": null, "scrollEndTime": null, "processingStartTime": null, "processingEndTime": null, "completedTime": null, "total": null, "processingDurationMillis": null, "result": "{\"url\":\"nxbigblob/e8bfb0a4-8c57-4835-9e6a-13cc2b5c075d\"}"}

stream: timer
message: {"now": "2020-03-09T15:37:41.805258Z"}

stream: timer
message: {"now": "2020-03-09T15:37:46.807009Z"}

...
```

There is a single Worker thread that read records using the Nuxeo Stream Lib,
records are forwarded to the internal pub-sub Vert.X event bus.

Client subscription is done by Handlers (could be thousands) receive records from the event bus
and propagates downstream using SSE. 


## TODO

- create a "lag" stream that reports lag and latency on Nuxeo Stream consumers
- configure and use log4j
- create a simple client application to introspect Nuxeo Stream activity
- gatling test to check limits
- use a fixed pool of consumer with a shared LogManager
- check required AWS ALB configuration
- handle auth


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
