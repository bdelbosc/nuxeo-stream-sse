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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;
import org.apache.commons.io.IOUtils;
import org.nuxeo.lib.stream.computation.Record;

/**
 * Decodes Avro message coming from Nuxeo, using a schema store.
 */
public class AvroDecoder {
    public static final byte[] AVRO_MESSAGE_V1_HEADER = new byte[] { (byte) 0xC3, (byte) 0x01 };

    private SchemaStore.Cache schemaStore;

    public AvroDecoder() {
        schemaStore = new SchemaStore.Cache();
    }

    public String decode(Record record) {
        if (schemaStore == null || !isAvroMessage(record.getData())) {
            throw new IllegalArgumentException("Not avro encoded");
        }
        long fp = getFingerPrint(record.getData());
        Schema schema = schemaStore.findByFingerprint(fp);
        if (schema == null) {
            throw new IllegalStateException(String.format("Not found schema: 0x%08X", fp));
        }
        GenericData genericData = new GenericData();
        BinaryMessageDecoder<GenericRecord> decoder = new BinaryMessageDecoder<>(genericData, schema);
        try {
            GenericRecord avroRecord = decoder.decode(record.getData(), null);
            return avroRecord.toString();
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("Error: %s decoding with schema: 0x%08X", e.getMessage(), fp));
        }
    }

    private long getFingerPrint(byte[] data) {
        byte[] fingerPrintBytes = Arrays.copyOfRange(data, 2, 10);
        return ByteBuffer.wrap(fingerPrintBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    protected boolean isAvroMessage(byte[] data) {
        return data.length >= 10 && data[0] == AVRO_MESSAGE_V1_HEADER[0] && data[1] == AVRO_MESSAGE_V1_HEADER[1];
    }

    public void addResourceSchema(String path) {
        InputStream resource = this.getClass().getResourceAsStream(path);
        if (resource == null) {
            System.err.println("Invalid schema resource: " + path);
            return;
        }
        System.err.println("Load schema: " + path);
        try (InputStream stream = resource) {
            String schema = IOUtils.toString(stream, "UTF-8");
            schemaStore.addSchema(new Schema.Parser().parse(schema));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load resource file: " + path, e);
        }
    }
}
