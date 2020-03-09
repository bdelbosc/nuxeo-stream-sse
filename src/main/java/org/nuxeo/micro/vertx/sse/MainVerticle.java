/*
 * (C) Copyright 2020 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bdelbosc
 */
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

    private Router createRouter() {
        Router router = Router.router(vertx);
        router.route("/subscribe/:stream")
              .handler(new SubscribeHandler())
              .failureHandler(it -> it.response().end("stream error"));
        router.route().handler(req -> req.response().putHeader("content-type", "text/plain").end("Hello!"));
        return router;
    }

    private JsonObject getConfig() {
        // TODO: use system env to pass config
        return getDefaultConfig();
    }

    private JsonObject getDefaultConfig() {
        return new JsonObject().put("bootstrap.servers", "localhost:9092")
                               .put("consumer.group", "" + "vertx")
                               .put("stream.prefix", "nuxeo-bulk-")
                               .put("streams", getDefaultStreams());
    }

    private List<String> getDefaultStreams() {
        return new ArrayList<>(List.of("command", "status", "done"));
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

    private void initWorkPool() {
        // Create a Work Verticle to read from Nuxeo Stream
        DeploymentOptions options = new DeploymentOptions().setWorker(true)
                                                           .setInstances(1)
                                                           .setWorkerPoolName("consumer-pool")
                                                           .setWorkerPoolSize(1)
                                                           .setConfig(getConfig());
        vertx.deployVerticle(WorkVerticle::new, options);
    }

}
