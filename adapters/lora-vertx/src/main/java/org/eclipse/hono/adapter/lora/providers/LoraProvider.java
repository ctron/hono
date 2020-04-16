/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CredentialsObject;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

/**
 * A LoraWAN provider which can send and receive messages from and to LoRa devices.
 */
public interface LoraProvider {

    /**
     * The name of this LoRaWAN provider. Will be used e.g. as an AMQP 1.0 message application property indicating the
     * the source provider of a LoRa Message.
     *
     * @return The name of this LoRaWAN provider.
     */
    String getProviderName();

    /**
     * The url path prefix which is used for this provider. E.g. "/myloraprovider".
     *
     * @return The url path prefix with leading slash. E.g. "/myloraprovider".
     */
    String pathPrefix();

    /**
     * Extracts the type from the incoming message of the LoRa Provider.
     *
     * @param loraMessage from which the type should be extracted.
     * @return LoraMessageType the type of this message
     */
    default LoraMessageType extractMessageType(final JsonObject loraMessage) {
        return LoraMessageType.UPLINK;
    }

    /**
     * The content type this provider will accept.
     *
     * @return MIME Content Type. E.g. "application/json"
     */
    default String acceptedContentType() {
        return HttpUtils.CONTENT_TYPE_JSON;
    }

    /**
     * The HTTP method this provider will accept incoming data.
     *
     * @return MIME Content Type. E.g. "application/json"
     */
    default HttpMethod acceptedHttpMethod() {
        return HttpMethod.POST;
    }

    /**
     * Extracts the device id from an incoming message of the LoRa Provider.
     *
     * @param loraMessage from which the device id should be extracted.
     * @return Device ID of the concerned device
     * @throws LoraProviderMalformedPayloadException if device Id cannot be extracted.
     */
    String extractDeviceId(JsonObject loraMessage);

    /**
     * Extracts the payload from an incoming message of the LoRa Provider.
     *
     * @param loraMessage from which the payload should be extracted.
     * @return Payload
     * @throws LoraProviderMalformedPayloadException if payload cannot be extracted.
     */
    String extractPayload(JsonObject loraMessage);

    /**
     * Extracts the normalizable data from an incoming message of the LoRa Provider.
     *
     * @param loraMessage from which the payload should be extracted.
     * @return Normalized data map.
     */
    default Map<String, Object> extractNormalizedData(JsonObject loraMessage){
        return null;
    }

    /**
     * Extracts the payload from an incoming message of the LoRa Provider.
     *
     * @param loraMessage from which the payload should be extracted.
     * @return Data which could not be normalized as a JsonObject.
     */
    default JsonObject extractAdditionalData(JsonObject loraMessage){
        return null;
    }

    /**
     * Sends the given payload using this lora provider.
     *
     * @param gatewayDevice The gateway device object containing the device information
     * @param gatewayCredential The gateway credential object containing the device credential information
     * @param targetDeviceId The device id containing the device information to which the message should be sent
     * @param loraCommand The command message containing the payload which should be sent.
     * @return A Future containing the result of the operation.
     */
    default Future<Void> sendDownlinkCommand(final JsonObject gatewayDevice, final CredentialsObject gatewayCredential,
            final String targetDeviceId, final Command loraCommand) {
        return Future.failedFuture(new UnsupportedOperationException());
    }

    /**
     * Extract an optional downlink handler from the uplink message.
     * <p>
     * The returned handler must not allocate any resources, as it is up
     * to the caller if the handler will be used or not.
     *
     * @param loraMessage from which the handler should be extracted.
     * @return The download handler, extracted from the upload message.
     *         May be {@link Optional#empty()}, but must never be {@code null}.
     *         The instance is specific to this uplink message, and must not be
     *         kept beyond processing this single message.
     * @throws IllegalArgumentException if the URL is present, but not a valid URL.
     */
    default Optional<DownlinkHandler> extractDownlinkHandler(final JsonObject loraMessage) {
        return Optional.empty();
    }

    /**
     * A handler for processing downlink messages.
     */
    interface DownlinkHandler {

        /**
         * Send a downlink message.
         * <p>
         * The method may only be executed once for each instance.
         *
         * @param command The command to send, must not be {@code null}.
         * @return A future, tracking the outcome of the operation.
         */
        Future<?> sendDownlinkCommand(Command command);
    }
}
