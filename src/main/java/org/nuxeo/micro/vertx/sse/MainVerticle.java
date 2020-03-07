package org.nuxeo.micro.vertx.sse;

import java.time.LocalDateTime;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = createRouter();
        createWebServer(startPromise, router);
        setTimer();
    }

    private Router createRouter() {
        Router router = Router.router(vertx);
        router.route("/timer/").handler(new TimeHandler()).failureHandler(it -> it.response().end("timer error"));
        router.route().handler(req -> {
            req.response().putHeader("content-type", "text/plain").end("Hello from Vert.x!");
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

    private void setTimer() {
        vertx.setPeriodic(1000, id -> {
            System.out.println("time");
            vertx.eventBus().publish("timer", LocalDateTime.now().toString());
        });
    }

}
