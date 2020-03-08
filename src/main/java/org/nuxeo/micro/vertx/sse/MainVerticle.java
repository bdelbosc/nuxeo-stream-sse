package org.nuxeo.micro.vertx.sse;


import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = createRouter();
        createWebServer(startPromise, router);
        initWorkPool();
    }

    private void initWorkPool() {
        DeploymentOptions options = new DeploymentOptions().setWorker(true)
                                                           .setInstances(1)
                                                           .setWorkerPoolName("consumer-pool")
                                                           .setWorkerPoolSize(1)
                                                           .setConfig(getConfig());
        vertx.deployVerticle(WorkVerticle::new, options);
    }

    private JsonObject getConfig() {
        return new JsonObject().put("bootstrap.servers", "localhost:9092")
                               .put("consumer.group", "" + "vertx")
                               .put("streams", getStreams());
    }

    private List<String> getStreams() {
        return new ArrayList<>(List.of("command", "status", "done"));
    }

    private Router createRouter() {
        Router router = Router.router(vertx);
        router.route("/subscribe/:stream")
              .handler(new SubscribeHandler())
              .failureHandler(it -> it.response().end("stream error"));
        router.route().handler(req -> {
            req.response().putHeader("content-type", "text/plain").end("Hello!");
        });
        return router;
    }

    private void createWebServer(Promise<Void> startPromise, Router router) {
        vertx.createHttpServer().requestHandler(router).listen(8888, http -> {
            if (http.succeeded()) {
                startPromise.complete();
                System.out.println("HTTP server started on port 8888");
            } else {
                startPromise.fail(http.cause());
            }
        });
    }
}
