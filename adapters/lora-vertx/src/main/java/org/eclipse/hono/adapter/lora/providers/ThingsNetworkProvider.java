/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.adapter.lora.providers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.client.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * A LoRaWAN provider with API for Things Network.
 */
@Component
public class ThingsNetworkProvider implements LoraProvider {

    private static final String FIELD_TTN_DEVICE_EUI = "hardware_serial";
    private static final String FIELD_TTN_DOWNLINK_URL = "downlink_url";
    private static final String FIELD_TTN_PAYLOAD_RAW = "payload_raw";
    private static final String FIELD_TTN_FPORT = "port";

    private Vertx vertx;
    private WebClient web;

    @Autowired
    public void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Start up component.
     */
    @PostConstruct
    public void start() {
        this.web = WebClient.create(vertx);
    }

    /**
     * Shut down component.
     */
    @PreDestroy
    public void stop() {
        if ( this.web != null ) {
            this.web.close();
        }
    }

    @Override
    public String getProviderName() {
        return "ttn";
    }

    @Override
    public String pathPrefix() {
        return "/ttn";
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_TTN_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_TTN_PAYLOAD_RAW);
    }

    @Override
    public Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        final Map<String, Object> returnMap = new HashMap<>();

        final Integer fport = loraMessage.getInteger(FIELD_TTN_FPORT);
        if (fport != null) {
            returnMap.put(LoraConstants.APP_PROPERTY_FUNCTION_PORT, fport);
        }

        return returnMap;
    }

    @Override
    public Optional<DownlinkHandler> extractDownlinkHandler(final JsonObject loraMessage) {

        final String url = loraMessage.getString(FIELD_TTN_DOWNLINK_URL);

        if ( url == null || url.isBlank()) {
            return Optional.empty();
        }

        final String deviceId = extractDeviceId(loraMessage);

        return Optional.of(new DownlinkHandler() {
            @Override
            public Future<?> sendDownlinkCommand(final Command command) {

                final JsonObject loraPayload = command.getPayload().toJsonObject();
                final String payloadBase64 = loraPayload.getString(LoraConstants.FIELD_LORA_DOWNLINK_PAYLOAD);
                final int port = loraPayload.getInteger(LoraConstants.FIELD_LORA_DEVICE_PORT, 0);

                final JsonObject body = new JsonObject();
                body.put("dev_id", deviceId);
                body.put("port", port);
                body.put("confirmed", false);
                body.put("payload_raw", payloadBase64);

                final Future<HttpResponse<Buffer>> result = Future.future();

                ThingsNetworkProvider.this.web
                    .post(url)
                    .sendJsonObject(body, result);

                return result;
            }
        });

    }

}
