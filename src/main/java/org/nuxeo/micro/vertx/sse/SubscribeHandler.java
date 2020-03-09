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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class SubscribeHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        List<String> streams = getStreams(request);
        if (streams == null || streams.isEmpty()) {
            context.fail(404);
            return;
        }
        System.out.println("Start Handler for streams: " + streams);

        HttpServerResponse response = context.response();
        addSseHeaders(response);

        List<MessageConsumer<Object>> consumers = new ArrayList<>(streams.size());
        streams.forEach(stream -> consumers.add(context.vertx().eventBus().consumer(stream, message -> {
            System.out.println("Forward message on " + stream + ": " + message.body());
            response.write("stream: " + stream  + "\n");
            response.write("message: " + message.body() + "\n\n");
        })));

        response.closeHandler(aVoid -> {
            System.out.println("Stop Handler on " + streams);
            consumers.forEach(MessageConsumer::unregister);
        });
    }

    private List<String> getStreams(HttpServerRequest request) {
        String streams = request.getParam("streams");
        return Arrays.stream(streams.split("\\+")).collect(Collectors.toList());
    }

    private void addSseHeaders(HttpServerResponse response) {
        response.setChunked(true);
        response.headers().add("Content-Type", "text/event-stream;charset=UTF-8");
        response.headers().add("Connection", "keep-alive");
        response.headers().add("Cache-Control", "no-cache");
        response.headers().add("Access-Control-Allow-Origin", "*");
    }
}
