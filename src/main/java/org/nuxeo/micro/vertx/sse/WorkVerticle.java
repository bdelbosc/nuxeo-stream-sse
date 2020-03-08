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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.nuxeo.lib.stream.codec.AvroMessageCodec;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogPartition;
import org.nuxeo.lib.stream.log.LogRecord;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.lib.stream.log.RebalanceException;
import org.nuxeo.lib.stream.log.RebalanceListener;
import org.nuxeo.lib.stream.log.kafka.KafkaLogManager;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class WorkVerticle extends AbstractVerticle implements RebalanceListener {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LogManager logManager = new KafkaLogManager("nuxeo-bulk-", getProducerProperties(), getConsumerProperties());
        System.out.println("Got a LogMan: " + logManager);
        Codec<Record> codec = new AvroMessageCodec<>(Record.class);
        System.out.println("Got codec: " + codec);
        // List<String> streams = (List<String>) config().getValue("streams");
        List<String> streams = Collections.singletonList("command");
        System.out.println("Streams: " + streams);
        String group = config().getString("consumer.group");
        System.out.println("Streams: " + streams + " group: " + group);
        LogTailer<Record> tailer = logManager.subscribe(group, streams, this, codec);
        System.out.println("Got a tailer: " + tailer);
        tailer.toStart();
        startPromise.complete();
        try {
            while (true) {
                try {
                    System.out.println("before read");
                    LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
                    if (record == null) {
                        System.out.println("Starving");
                        continue;
                    }
                    process(record);
                } catch (RebalanceException e) {
                    System.out.println("Rebalanced");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted");
        } catch (Exception e) {
            // Exception in an executor are catched make sure they are logged
            System.err.println("Loop error: " + e.getMessage());
            throw e;
        } finally {
            tailer.close();
        }
    }

    private void process(LogRecord<Record> record) {
        String channel = record.offset().partition().name();
        System.out.println("Got record on channel:" + channel);
        vertx.eventBus().publish(channel, record.message().toString());
    }

    private Properties getConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 60000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 400);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        return props;
    }

    private Properties getProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        return props;
    }

    private String getBootstrapServers() {
        return config().getString(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
    }

    @Override
    public void onPartitionsRevoked(Collection<LogPartition> partitions) {
        System.out.println("Revoked " + Arrays.toString(partitions.toArray()));
    }

    @Override
    public void onPartitionsAssigned(Collection<LogPartition> partitions) {
        System.out.println("Assigned " + Arrays.toString(partitions.toArray()));
    }
}
