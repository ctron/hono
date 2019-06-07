/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.credentials;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing device credentials.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API//">Credentials API</a>.
 * It receives HTTP requests representing operation invocations and sends them to the address {@link CredentialsConstants#CREDENTIALS_ENDPOINT} on the vertx
 * event bus for processing. The outcome is then returned to the client in the HTTP response.
 */
public final class CredentialsManagementHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public CredentialsManagementHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    protected String getEventBusAddress() {
        return CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN;
    }

    @Override
    public String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                getName(), PARAM_TENANT_ID, PARAM_DEVICE_ID);

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // get all credentials for a given device
        router.get(pathWithTenantAndDeviceId).handler(this::getCredentialsForDevice);

        // update all credentials for a given device
        router.put(pathWithTenantAndDeviceId).handler(bodyHandler);
        router.put(pathWithTenantAndDeviceId).handler(this::extractRequiredJsonArrayPayload);
        router.put(pathWithTenantAndDeviceId).handler(this::extractIfMatchVersionParam);
        router.put(pathWithTenantAndDeviceId).handler(this::updateCredentials);

        // remove all credentials for a device
        router.delete(pathWithTenantAndDeviceId).handler(this::extractIfMatchVersionParam);
        router.delete(pathWithTenantAndDeviceId).handler(this::removeCredentialsForDevice);

    }

    private void updateCredentials(final RoutingContext ctx) {

        final JsonArray credentials = (JsonArray) ctx.get(KEY_REQUEST_BODY);

        final String deviceId = getDeviceIdParam(ctx);
        final String resourceVersion = ctx.get(KEY_RESOURCE_VERSION);
        final String tenantId = getTenantParam(ctx);

        logger.debug("updating credentials [tenant: {}, device-id: {}]", tenantId, deviceId);

        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.CREDENTIALS_ENDPOINT, credentials);


        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.update.toString())
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setResourceVersion(resourceVersion)
                .setJsonPayload(payload)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx));
}

    private void removeCredentialsForDevice(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);
        final String resourceVersion = ctx.get(KEY_RESOURCE_VERSION);

        logger.debug("removeCredentialsForDevice: [tenant: {}, device-id: {}]", tenantId, deviceId);

        final JsonObject payload = new JsonObject();
        payload.put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        payload.put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SPECIFIER_WILDCARD);

        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.remove.toString())
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .setJsonPayload(payload)
                .setResourceVersion(resourceVersion)
                .toJson();

        sendAction(ctx, requestMsg, getDefaultResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_NO_CONTENT,
                (Handler<HttpServerResponse>) null));
    }

    private void getCredentialsForDevice(final RoutingContext ctx) {

        // mandatory params
        final String tenantId = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);

        logger.debug("getCredentialsForDevice [tenant: {}, device-id: {}]]", tenantId, deviceId);

        final JsonObject requestMsg = EventBusMessage.forOperation(CredentialsConstants.CredentialsAction.get.toString())
                .setTenant(tenantId)
                .setDeviceId(deviceId)
                .toJson();

        sendAction(ctx, requestMsg, getCredentialsResponseHandler(ctx,
                status -> status == HttpURLConnection.HTTP_OK,
                (Handler<HttpServerResponse>) null));
    }


    /**
     * Gets a response handler that implements the default behavior for responding to an HTTP request.
     * <p>
     * The default behavior is as follows:
     * <ol>
     * <li>Set the status code on the response.</li>
     * <li>If the status code represents an error condition (i.e. the code is &gt;= 400),
     * then the JSON object passed in to the returned handler is written to the response body.</li>
     * <li>Otherwise, if the given filter evaluates to {@code true} for the status code,
     * the JSON object is written to the response body and the given custom handler is
     * invoked (if not {@code null}).</li>
     * </ol>
     *
     * @param ctx The routing context of the request.
     * @param successfulOutcomeFilter A predicate that evaluates to {@code true} for the status code(s) representing a
     *                           successful outcome.
     * @param customHandler An (optional) handler for post processing the HTTP response, e.g. to set any additional HTTP
     *                        headers. The handler <em>must not</em> write to response body. May be {@code null}.
     * @return The created handler for processing responses.
     * @throws NullPointerException If routing context or filter is {@code null}.
     */
    protected BiConsumer<Integer, EventBusMessage> getCredentialsResponseHandler(
            final RoutingContext ctx,
            final Predicate<Integer> successfulOutcomeFilter,
            final Handler<HttpServerResponse> customHandler) {

        Objects.requireNonNull(successfulOutcomeFilter);
        final HttpServerResponse response = ctx.response();

        return (status, responseMessage) -> {
            response.setStatusCode(status);
            if (status >= 400) {
                HttpUtils.setResponseBody(response, responseMessage.getJsonPayload());
            } else if (successfulOutcomeFilter.test(status)) {
                final JsonArray secrets = responseMessage.getJsonPayload()
                        .getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT);
                HttpUtils.setResponseBody(response, secrets);
                if (customHandler != null) {
                    customHandler.handle(response);
                }
            }
            response.end();
        };
    }
}
