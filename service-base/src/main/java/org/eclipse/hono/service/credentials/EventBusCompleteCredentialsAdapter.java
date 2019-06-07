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
package org.eclipse.hono.service.credentials;

import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link CredentialsService}s.
 * <p>
 * This base class provides support for receiving <em>Get</em> request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 *
 * @param <T> The type of configuration class this service supports.
 */
@Deprecated
public abstract class EventBusCompleteCredentialsAdapter<T> extends EventBusCredentialsAdapter<T> {

    @Override
    protected abstract CompleteCredentialsService getService();

    @Override
    protected Future<EventBusMessage> processGetByDeviceIdRequest(final EventBusMessage request, final String tenantId,
            final String type, final String deviceId, final Span span) {

        log.debug("getting credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
        TracingHelper.TAG_CREDENTIALS_TYPE.set(span, type);
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        final Future<CredentialsResult<JsonObject>> result = Future.future();
        getService().getAll(tenantId, deviceId, span, result);
        return result.map(res -> {
            return request.getResponse(res.getStatus())
                    .setDeviceId(deviceId)
                    .setJsonPayload(res.getPayload())
                    .setCacheDirective(res.getCacheDirective());
        });

    }


}
