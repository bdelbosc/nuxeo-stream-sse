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
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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
import io.vertx.core.json.JsonArray;

public class WorkVerticle extends AbstractVerticle implements RebalanceListener {

    private LogManager logManager;

    private LogTailer<Record> tailer;

    private AvroDecoder decoder;


    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logManager = new KafkaLogManager(config().getString("stream.prefix"), getProducerProperties(),
                getConsumerProperties());
        initTailer();
        startPromise.complete();
        System.err.println("Loop Start");
        try {
            initAvro();
            consumerLoop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Loop Interrupted");
        } catch (Exception e) {
            // Exception in an executor are catched make sure they are logged
            System.err.println("Loop Error: " + e.getMessage());
            throw e;
        } finally {
            System.out.println("Cleaning");
            tailer.close();
            logManager.close();
        }

    }


    private void initAvro() {
        decoder = new AvroDecoder();
        decoder.addResourceSchema("/avro/BulkBucket-0xCC59A5FF2725F7AF.avsc");
        decoder.addResourceSchema("/avro/DataBucket-0xB62494C74E419198.avsc");
        decoder.addResourceSchema("/avro/BulkStatus-0x182AC639648607C7.avsc");
        decoder.addResourceSchema("/avro/BulkCommand-0xEEF6E0C0FA358880.avsc");
    }

    private void initTailer() {
        System.out.println("Created: " + logManager);
        Codec<Record> codec = new AvroMessageCodec<>(Record.class);
        List<String> streams = getStreams();
        String group = config().getString("consumer.group");
        System.out.println("Streams: " + streams + " group: " + group);
        tailer = logManager.subscribe(group, streams, this, codec);
        System.out.println("Created: " + tailer);
    }

    private List<String> getStreams() {
        JsonArray jsonStreams = (JsonArray) config().getValue("streams");
        return jsonStreams.stream().map(v -> (String) v).collect(Collectors.toList());
    }

    private void consumerLoop() throws InterruptedException {
        while (true) {
            try {
                LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
                if (record == null) {
                    System.out.println("Starving");
                    continue;
                }
                processRecord(record);
            } catch (RebalanceException e) {
                System.out.println("Rebalanced");
            }
        }
    }

    private void processRecord(LogRecord<Record> record) {
        String stream = record.offset().partition().name();
        System.out.println("New record on stream: " + stream);
        String message = decoder.decode(record.message());
        vertx.eventBus().publish(stream, message);
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
        tailer.toEnd();
    }
}
