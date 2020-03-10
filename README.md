# Nuxeo-stream-sse

This is a proof of concept **NOT FOR PRODUCTION**.


## About

Nuxeo Stream and the [Bulk Service](https://doc.nuxeo.com/nxdoc/bulk-action-framework/#execution-flow) is a Stream processor framework that relies on Apache Kafka where a command can be submitted by a REST endpoint,
the progression or status of each command can also be requested using another REST endpoint.

Using periodic polling to get the status will saturate the limited tomcat pool and impacts all other operations on the platform, 

To support a massive number of clients or deliver a large number of status we need to scale on the REST endpoint.

We want a dedicated endpoint to send server feedback directly taken from Kafka in real-time without impacting the Nuxeo nodes' performance.

## Goals

1. Clients can subscribe to multiple Nuxeo Streams and receive record in real-time
2. Support a massive number of clients per front node and scale horizontally.


## Design Decisions

We need one-way communication from server to the client.
[Server Sent Event](https://en.wikipedia.org/wiki/Server-sent_events) is supported by all browsers and is exactly designed for this.

Scaling on the number of clients means supporting lots of concurrencies using a small number of threads.
An event-driven and non-blocking architecture like [Vert.x](https://vertx.io/) is a solid solution that can support thousands of connections per node with very few resources.

### Rejected Alternatives 

- Use [Helidon](https://helidon.io/) and [rxJava](https://github.com/ReactiveX/RxJava) with an observer pattern:
    - pros: we could run limited Nuxeo Platform as micro-service on the same instance
    - cons: much more complex and heavier than vert.x event-bus

- Use Akka:
    - pros:
    - cons: actor pattern is too complex just for this specific need

## Implementation

There is a single thread that reads records using the Nuxeo Stream Lib on multiple Streams (Kafka Topics).
This is a [Vert.x Worker Verticle](https://vertx.io/docs/vertx-core/java/#worker_verticles).
 
The records are decoded from Avro and send as JSON in the internal Vert.x [pub-sub event bus](https://vertx.io/docs/vertx-core/java/#_the_event_bus_api).

The client uses a REST endpoint to subscribe to a list of streams, the connection is managed by request handler.
Each handler subscribes to the Vert.x event bus and propagate the record downstream using SSE.
Having thousands of handlers should easy peasy.

## Usage

Start a Nuxeo with Kafka enabled, then run the poc:
```bash
# build
mvn -nsu clean install
# run
java -jar target/nuxeo-stream-sse-1.0.0-SNAPSHOT-fat.jar
...
```

Use curl to simulate a client subscription. For instance, subscribe to different streams of the Bulk Service,
note that `timer` stream is a fake stream used to create a heart-beat: 

```bash
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


## TODO

- add metadata to message: offset, stream, event-time, flag
  ```
    metadata: {"stream": name, "offset": "1234" ...}
    message: { ... }
  ```
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
