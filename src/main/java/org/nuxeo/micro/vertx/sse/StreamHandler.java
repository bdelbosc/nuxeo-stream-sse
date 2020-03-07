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

import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class StreamHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        System.out.println("Start StreamHandler");
        HttpServerRequest request = context.request();
        HttpServerResponse response = context.response();

        response.setChunked(true);
        response.headers().add("Content-Type", "text/event-stream;charset=UTF-8");
        response.headers().add("Connection", "keep-alive");
        response.headers().add("Cache-Control", "no-cache");
        response.headers().add("Access-Control-Allow-Origin", "*");

        String stream = request.getParam("stream");
        if (stream == null) {
            context.fail(404);
        }

        MessageConsumer<Object> consumer = context.vertx().eventBus().consumer(stream, message -> {
            System.out.println("I have received a message on " + stream + ": " + message.body());
            response.write(message.body().toString() + "\n\n");
        });

        response.closeHandler(aVoid -> {
            System.out.println("End StreamHandler");
            consumer.unregister();
        });
    }
}
