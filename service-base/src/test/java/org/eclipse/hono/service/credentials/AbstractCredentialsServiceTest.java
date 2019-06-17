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

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonSecret;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract class used as a base for verifying behavior of {@link CredentialsService} and
 * {@link CredentialsManagementService} in device registry implementations.
 *
 */
public abstract class AbstractCredentialsServiceTest {

    /**
     * Gets credentials service being tested.
     * @return The credentials service
     */
    public abstract CredentialsService getCredentialsService();

    /**
     * Gets credentials service being tested.
     * 
     * @return The credentials service
     */
    public abstract CredentialsManagementService getCredentialsManagementService();

    /**
     * Gets the device management service.
     * <p>
     * Return this device management service which is needed in order to work in coordination with the credentials
     * service.
     * 
     * @return The device management service.
     */
    public abstract DeviceManagementService getDeviceManagementService();

    /**
     * Gets the cache directive that is supposed to be used for a given type of credentials.
     * <p>
     * This default implementation always returns {@code CacheDirective#noCacheDirective()}.
     *
     * @param credentialsType The type of credentials.
     * @return The expected cache directive.
     */
    protected CacheDirective getExpectedCacheDirective(final String credentialsType) {
        return CacheDirective.noCacheDirective();
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingCredentials(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
            ctx.completeNow();
        });

    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingDevice(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(),
                    ctx.succeeding(s -> {

                        assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                    assertNotNull(r.getPayload());
                                    assertTrue(r.getPayload().isEmpty());
                                },
                                r -> {
                                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                                },
                                ctx::completeNow);

            }));

        });

    }

    /**
     * Creates a password based secret.
     * 
     * @param authId The authentication to use.
     * @param password The password to use.
     * @return The fully populated secret.
     */
    protected static PasswordSecret createPasswordSecret(final String authId, final String password) {
        final PasswordSecret s = new PasswordSecret();
        s.setAuthId(authId);
        s.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        s.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        return s;
    }

    protected void assertGetMissing(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ExecutionBlock whenComplete) {

        assertGet(ctx, tenantId, deviceId, authId, type,
                r -> {
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                },
                r -> {
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                },
                whenComplete);
    }

    protected void assertGetEmpty(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ExecutionBlock whenComplete) {

        assertGet(ctx, tenantId, deviceId, authId, type,
                r -> {
                    assertEquals(HTTP_OK, r.getStatus());
                    assertTrue(r.getPayload().isEmpty());
                },
                r -> {
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                },
                whenComplete);
    }

    protected void assertGet(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ThrowingConsumer<OperationResult<List<CommonSecret>>> mangementValidation,
            final ThrowingConsumer<CredentialsResult<JsonObject>> adapterValidation,
            final ExecutionBlock whenComplete) {

        getCredentialsManagementService().get(tenantId, deviceId, ctx.succeeding(s3 -> {

            ctx.verify(() -> {

                // assert a few basics, optionals may be empty
                // but must not be null
                assertNotNull(s3.getCacheDirective());
                assertNotNull(s3.getResourceVersion());

                mangementValidation.accept(s3);

                getCredentialsService().get(tenantId, type,
                        authId,
                        ctx.succeeding(s4 -> ctx.verify(() -> {

                            adapterValidation.accept(s4);

                            whenComplete.apply();
                        })));

            });
        }));

    }

    /**
     * Test creating a new secret.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreatePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordSecret(authId, "bar");

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(secret),
                    ctx.succeeding(s2 -> {

                        ctx.verify(() -> {

                            assertEquals(HTTP_NO_CONTENT, s2.getStatus());

                            assertGet(ctx, tenantId, deviceId, authId,
                                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                    r -> {
                                        assertEquals(HTTP_OK, r.getStatus());
                                    },
                                    r -> {
                                        assertEquals(HTTP_OK, r.getStatus());
                                    },
                                    ctx::completeNow);

                        });

                    }));

        });

    }

    /**
     * Test updating a new secret.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdatePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordSecret(authId, "bar");

        final Future<?> phase1 = Future.future();

        // phase 1 - initially set credentials

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(secret),
                    ctx.succeeding(s2 -> {

                        ctx.verify(() -> {

                            assertEquals(HTTP_NO_CONTENT, s2.getStatus());

                            assertGet(ctx, tenantId, deviceId, authId,
                                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                    r -> {
                                        assertEquals(HTTP_OK, r.getStatus());
                                    },
                                    r -> {
                                        assertEquals(HTTP_OK, r.getStatus());
                                    },
                                    phase1::complete);

                        });

                    }));

        });

        // phase 2 - try to update

        phase1.setHandler(ctx.succeeding(v -> {

            final var newSecret = createPasswordSecret(authId, "baz");

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(newSecret),
                    ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HTTP_NO_CONTENT, s.getStatus());

                        assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                ctx::completeNow);
                    })));

        }));

    }

    /**
     * Test updating a new secret.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndDeletePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordSecret(authId, "bar");

        final Checkpoint checkpoint = ctx.checkpoint(7);

        // phase 1 - check missing

        final Future<?> phase1 = Future.future();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                phase1::complete);

        // phase 2 - create device

        final Future<?> phase2 = Future.future();

        phase1.setHandler(ctx.succeeding(s1 -> {

            checkpoint.flag();
            getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(),
                    ctx.succeeding(s2 -> {
                        checkpoint.flag();
                        phase2.complete();
                    }));

        }));

        // phase 3 - initially set credentials

        final Future<?> phase3 = Future.future();

        phase2.setHandler(ctx.succeeding(s1 -> {

            assertGetEmpty(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

                getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                        Collections.singletonList(secret),
                        ctx.succeeding(s2 -> {

                            checkpoint.flag();

                            ctx.verify(() -> {

                                assertEquals(HTTP_NO_CONTENT, s2.getStatus());

                                assertGet(ctx, tenantId, deviceId, authId,
                                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                        r -> {
                                            assertEquals(HTTP_OK, r.getStatus());
                                        },
                                        r -> {
                                            assertEquals(HTTP_OK, r.getStatus());
                                        },
                                        () -> {
                                            checkpoint.flag();
                                            phase3.complete();
                                        });

                            });

                        }));
            });

        }));

        // phase 4 - delete

        final Future<?> phase4 = Future.future();

        phase3.setHandler(ctx.succeeding(v -> {

            getCredentialsManagementService().remove(tenantId, deviceId, Optional.empty(),
                    ctx.succeeding(s -> ctx.verify(() -> {

                        checkpoint.flag();

                        assertEquals(HTTP_NO_CONTENT, s.getStatus());

                        assertGetEmpty(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                () -> {
                                    checkpoint.flag();
                                    phase4.complete();
                                });

                    })));

        }));

        // Phase 5

        final Future<?> phase5 = Future.future();

        phase4.setHandler(ctx.succeeding(v -> {
            getDeviceManagementService().deleteDevice(tenantId, deviceId, Optional.empty(),
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertGetMissing(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
                                    checkpoint.flag();
                                    phase5.complete();
                                });
                    })));
        }));

        // complete

        phase5.setHandler(ctx.succeeding(s -> ctx.completeNow()));

    }

    /**
     * Registers a set of credentials for a device.
     *
     * @param svc The service to register the credentials with.
     * @param tenantId The tenant that the device belongs to.
     * @param secrets The secrets to register.
     * @return A succeeded future if the credentials have been registered successfully.
     */
    protected static Future<OperationResult<Void>> setCredentials(
            final CredentialsManagementService svc,
            final String tenantId,
            final String deviceId,
            final List<CommonSecret> secrets) {

        final Future<OperationResult<Void>> result = Future.future();
        svc.set(tenantId, deviceId, Optional.empty(), secrets, result);
        return result.map(r -> {
            if (HttpURLConnection.HTTP_NO_CONTENT == r.getStatus()) {
                return r;
            } else {
                throw new ClientErrorException(r.getStatus());
            }
        });
    }

    /**
     * Verifies that credentials of a particular type are registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials exist.
     */
    protected static Future<Void> assertRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that credentials of a particular type are not registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials do not exist.
     */
    protected static Future<Void> assertNotRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_NOT_FOUND);
    }

    private static Future<Void> assertGet(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final int expectedStatusCode) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        svc.get(tenant, type, authId, result);
        return result.map(r -> {
            if (r.getStatus() == expectedStatusCode) {
                return null;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }

}
