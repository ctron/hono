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

import java.net.HttpURLConnection;
import java.util.Objects;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsResult;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link CompleteCredentialsService}s.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration class this service supports.
 */
@Deprecated
public abstract class CompleteBaseCredentialsService<T> extends EventBusCompleteCredentialsAdapter<T>
    implements CompleteCredentialsService {

    private final HonoPasswordEncoder pwdEncoder;

    /**
     * Creates a new service instance for a password encoder.
     * 
     * @param pwdEncoder The encoder to use for hashing clear text passwords.
     * @throws NullPointerException if encoder is {@code null}.
     */
    protected CompleteBaseCredentialsService(final HonoPasswordEncoder pwdEncoder) {
        this.pwdEncoder = Objects.requireNonNull(pwdEncoder);
    }

    @Override
    protected CompleteCredentialsService getService() {
        return this;
    }

    @Override
    protected HonoPasswordEncoder getPasswordEncoder() {
        return this.pwdEncoder;
    }

    @Override
    public final void getAll(final String tenantId, final String deviceId,
                             final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        getAll(tenantId, deviceId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void add(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void update(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * Handles an unimplemented operation by failing the given handler with a {@link ClientErrorException} having a
     * <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
