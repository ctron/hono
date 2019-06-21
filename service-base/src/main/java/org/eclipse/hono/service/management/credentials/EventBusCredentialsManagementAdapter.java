/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.hono.service.management.Util.newChildSpan;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.auth.BCryptHelper;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventBusMessage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Adapter to bind {@link CredentialsManagementService} to the vertx event bus.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class EventBusCredentialsManagementAdapter<T> extends EventBusService<T>
        implements Verticle {

    private static final String SPAN_NAME_GET_CREDENTIAL = "get Credential from management API";
    private static final String SPAN_NAME_UPDATE_CREDENTIAL = "update Credential from management API";

    private static final int DEFAULT_MAX_BCRYPT_ITERATIONS = 10;

    /**
     * The service to forward requests to.
     * 
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract CredentialsManagementService getService();

    @Override
    protected String getEventBusAddress() {
        return CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_MANAGEMENT_IN;
    }

    @Override
    protected Future<EventBusMessage> processRequest(final EventBusMessage request) {
        Objects.requireNonNull(request);

        final String operation = request.getOperation();

        switch (CredentialsConstants.CredentialsAction.from(operation)) {
            case get:
                return processGetRequest(request);
            case update:
                return processUpdateRequest(request);
            default:
                return processCustomCredentialsMessage(request);
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom operations that are not defined by
     * Hono's Device Registration API.
     * <p>
     * This default implementation simply returns a future that is failed with a {@link ClientErrorException} with an
     * error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomCredentialsMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final JsonObject payload = request.getJsonPayload();
        final SpanContext spanContext = request.getSpanContext();

        if (tenantId == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant ID"));
        } else if (payload == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing payload"));
        }
        try {
            final Future<List<CommonCredential>> secretsFuture = credentialsFromPayload(request);

            final Span span = newChildSpan(SPAN_NAME_UPDATE_CREDENTIAL, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());
            final Future<OperationResult<Void>> result = Future.future();

            return secretsFuture.compose(secrets -> {
                getService().set(tenantId, deviceId, resourceVersion, secrets, span, result);
                        return result.map(res -> {
                            return res.createResponse(request, id -> null).setDeviceId(deviceId);
                        });
                    }
            );
        } catch (final IllegalStateException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage(), e));
        }
    }

    /**
     * Decode a secret from a JSON object.
     * 
     * @param object The object to device from.
     * @return The decoded secret. Or {@code null} if the provided JSON object was {@code null}.
     * @throws IllegalStateException if the {@code type} field was not set.
     */
    protected CommonCredential decodeCredential(final JsonObject object) {

        if (object == null) {
            return null;
        }

        final String type = object.getString("type");
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("'type' field must be set");
        }

        final CommonCredential secret = decodeSecret(type, object);
        checkSecret(secret);
        return secret;
    }

    /**
     * Decode a secret, based on the provided type.
     * 
     * @param type The type of the secret. Will never be {@code null}.
     * @param object The JSON object to decode. Will never be {@code null}.
     * @return The decoded secret.
     */
    protected CommonCredential decodeSecret(final String type, final JsonObject object) {
        switch (type) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            return object.mapTo(PasswordCredential.class);
        case CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY:
            return object.mapTo(PskCredential.class);
        case CredentialsConstants.SECRETS_TYPE_X509_CERT:
            return object.mapTo(X509CertificateCredential.class);
        default:
            return object.mapTo(GenericCredential.class);
        }
    }

    /**
     * Decode a list of secrets from a JSON array.
     * <p>
     * This is a convenience method, decoding a list of secrets from a JSON array.
     * 
     * @param objects The JSON array.
     * @return The list of decoded secrets.
     * @throws NullPointerException in the case the {@code objects} parameter is {@code null}.
     */
    protected List<CommonCredential> decodeCredentials(final JsonArray objects) {
        return objects
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(this::decodeCredential)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Extract the credentials from an event bus request.
     * 
     * @param request The request to extract information from.
     * @return A future, returning the secrets.
     * @throws NullPointerException in the case the request is {@code null}.
     */
    protected Future<List<CommonCredential>> credentialsFromPayload(final EventBusMessage request) {
        try {
            return Future.succeededFuture(Optional.ofNullable(request.getJsonPayload())
                    .map(json -> {
                        return decodeCredentials(json.getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT));
                    })
                    .orElseGet(ArrayList::new));
        } catch (final IllegalArgumentException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }
    }

    private Future<EventBusMessage> processGetRequest(final EventBusMessage request) {
        final String tenantId = request.getTenant();
        final String deviceId = request.getDeviceId();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = newChildSpan(SPAN_NAME_GET_CREDENTIAL, spanContext, tracer, tenantId, deviceId, getClass().getSimpleName());
        final Future<OperationResult<List<CommonCredential>>> result = Future.future();

        getService().get(tenantId, deviceId, span, result);

        return result.map(res -> {
            return res.createResponse(request, secrets -> {
                final JsonObject ret = new JsonObject();
                final JsonArray secretArray = new JsonArray();
                for (final CommonCredential secret : secrets) {
                    secretArray.add(JsonObject.mapFrom(secret));
                }
                ret.put(CredentialsConstants.CREDENTIALS_ENDPOINT, secretArray);
                return ret;
            }).setDeviceId(deviceId);
        });
    }

    /**
     * Validate a secret.
     * 
     * @param credential The secret to validate.
     * @throws IllegalStateException if the secret is not valid.
     */
    protected void checkSecret(final CommonCredential credential) {
        if (credential instanceof PasswordCredential) {
            for (final PasswordSecret passwordSecret : ((PasswordCredential) credential).getSecrets()) {
                passwordSecret.checkValidity();
                checkHashedPassword(passwordSecret);
                switch (passwordSecret.getHashFunction()) {
                    case CredentialsConstants.HASH_FUNCTION_BCRYPT:
                        final String pwdHash = passwordSecret.getPasswordHash();
                        verifyBcryptPasswordHash(pwdHash);
                        break;
                    default:
                        // pass
                }
                // pass
            }
        }
    }

    /**
     * Verifies that a hash value is a valid BCrypt password hash.
     * <p>
     * The hash must be a version 2a hash and must not use more than the configured
     * maximum number of iterations as returned by {@link #getMaxBcryptIterations()}.
     *
     * @param pwdHash The hash to verify.
     * @throws IllegalStateException if the secret does not match the criteria.
     */
    protected void verifyBcryptPasswordHash(final String pwdHash) {

        Objects.requireNonNull(pwdHash);
        if (BCryptHelper.getIterations(pwdHash) > getMaxBcryptIterations()) {
            throw new IllegalStateException("password hash uses too many iterations, max is " + getMaxBcryptIterations());
        }
    }

    private void checkHashedPassword(final PasswordSecret secret) {
        if (secret.getHashFunction() == null) {
            throw new IllegalStateException("missing/invalid hash function");
        }

        if (secret.getPasswordHash() == null) {
            throw new IllegalStateException("missing/invalid password hash");
        }
    }

    /**
     * Gets the maximum number of iterations that should be allowed in password hashes
     * created using the <em>BCrypt</em> hash function.
     * <p>
     * This default implementation returns 10.
     * <p>
     * Subclasses should override this method in order to e.g. return a value determined
     * from a configuration property.
     *
     * @return The number of iterations.
     */
    protected int getMaxBcryptIterations() {
        return DEFAULT_MAX_BCRYPT_ITERATIONS;
    }

}
