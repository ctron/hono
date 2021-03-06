/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.CredentialsObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An authentication provider that verifies username/password credentials using
 * Hono's <em>Credentials</em> API.
 */
public final class UsernamePasswordAuthProvider extends CredentialsApiAuthProvider<UsernamePasswordCredentials> {

    private final ServiceConfigProperties config;
    private final HonoPasswordEncoder pwdEncoder;

    /**
     * Creates a new provider for a given configuration.
     *
     * @param credentialsClientFactory The factory to use for creating a Credentials service client.
     * @param config The configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Autowired
    public UsernamePasswordAuthProvider(final CredentialsClientFactory credentialsClientFactory, final ServiceConfigProperties config, final Tracer tracer) {
        this(credentialsClientFactory, new SpringBasedHonoPasswordEncoder(), config, tracer);
    }

    /**
     * Creates a new provider for a given configuration.
     *
     * @param credentialsClientFactory The factory to use for creating a Credentials service client.
     * @param pwdEncoder The object to use for validating hashed passwords.
     * @param config The configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Autowired
    public UsernamePasswordAuthProvider(
            final CredentialsClientFactory credentialsClientFactory,
            final HonoPasswordEncoder pwdEncoder,
            final ServiceConfigProperties config,
            final Tracer tracer) {

        super(credentialsClientFactory, tracer);
        this.config = Objects.requireNonNull(config);
        this.pwdEncoder = Objects.requireNonNull(pwdEncoder);
    }

    /**
     * Creates a {@link UsernamePasswordCredentials} instance from auth info provided by a
     * device.
     * <p>
     * The JSON object passed in is required to contain a <em>username</em> and a
     * <em>password</em> property.
     *
     * @param authInfo The credentials provided by the device.
     * @return The {@link UsernamePasswordCredentials} instance created from the auth info or
     *         {@code null} if the auth info does not contain the required information.
     * @throws NullPointerException if the auth info is {@code null}.
     */
    @Override
    protected UsernamePasswordCredentials getCredentials(final JsonObject authInfo) {

        try {
            final String username = authInfo.getString("username");
            final String password = authInfo.getString("password");
            if (username == null || password == null) {
                return null;
            } else if (password.isEmpty()) {
                return tryGetCredentialsEncodedInUsername(username);
            } else {
                return UsernamePasswordCredentials.create(username, password, config.isSingleTenant());
            }
        } catch (final ClassCastException e) {
            return null;
        }
    }

    private UsernamePasswordCredentials tryGetCredentialsEncodedInUsername(final String username) {

        try {
            final String decoded = new String(Base64.getDecoder().decode(username));
            final int colonIdx = decoded.indexOf(":");
            if (colonIdx > -1) {
                final String user = decoded.substring(0, colonIdx);
                final String pass = decoded.substring(colonIdx + 1);
                return UsernamePasswordCredentials.create(user, pass, config.isSingleTenant());
            } else {
                return null;
            }
        } catch (final IllegalArgumentException ex) {
            log.debug("error extracting username/password from username field", ex);
            return null;
        }
    }

    @Override
    protected Future<Device> doValidateCredentials(
            final UsernamePasswordCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {

        final Context currentContext = Vertx.currentContext();
        if (currentContext == null) {
            return Future.failedFuture(new IllegalStateException("not running on vert.x Context"));
        } else {
            final Promise<Device> result = Promise.promise();
            currentContext.executeBlocking(blockingCodeHandler -> {
                log.debug("validating password hash on vert.x worker thread [{}]", Thread.currentThread().getName());
                final boolean isValid = credentialsOnRecord.getCandidateSecrets().stream()
                        .anyMatch(candidateSecret -> pwdEncoder.matches(deviceCredentials.getPassword(), candidateSecret));
                if (isValid) {
                    blockingCodeHandler.complete(new Device(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
                } else {
                    blockingCodeHandler.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
                }
            }, false, result);
            return result.future();
        }
    }
}
