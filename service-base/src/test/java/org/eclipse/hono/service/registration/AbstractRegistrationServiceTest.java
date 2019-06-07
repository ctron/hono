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
package org.eclipse.hono.service.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Optional;

/**
 * Abstract class used as a base for verifying behavior of {@link RegistrationService} and the
 * {@link DeviceManagementService} in device registry implementations.
 *
 */
public abstract class AbstractRegistrationServiceTest {

    /**
     * The tenant used in tests.
     */
    protected static final String TENANT = Constants.DEFAULT_TENANT;
    /**
     * The device identifier used in tests.
     */
    protected static final String DEVICE = "4711";
    /**
     * The gateway identifier used in the tests.
     */
    protected static final String GW = "gw-1";

    /**
     * Gets registration service being tested.
     * @return The registration service
     */
    public abstract RegistrationService getRegistrationService();

    /**
     * Gets device management service being tested.
     * 
     * @return The device management service
     */
    public abstract DeviceManagementService getDeviceManagementService();

    /**
     * Verifies that the registry returns 404 when getting an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getDeviceManagementService()
                .readDevice(TENANT, DEVICE, ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getDeviceManagementService()
                .deleteDevice(TENANT, DEVICE, Optional.empty(), ctx.succeeding(response -> ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                            ctx.completeNow();
                        })));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDuplicateRegistrationFails(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(ok -> {
            final Future<OperationResult<Id>> addResult = Future.future();
            getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), addResult);
            return addResult;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> addResult = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), addResult);
        addResult.map(r -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus());
        }))
        .compose(ok -> {
                    final Future<OperationResult<Device>> getResult = Future.future();
                    getDeviceManagementService().readDevice(TENANT, DEVICE, getResult);
                    return getResult;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
            assertNotNull(s.getPayload());

                    getRegistrationService().assertRegistration(TENANT, DEVICE, ctx.succeeding(s2 -> {
                        assertEquals(HttpURLConnection.HTTP_OK, s2.getStatus());
                        assertNotNull(s2.getPayload());
                        ctx.completeNow();
                    }));

        })));
    }

    /**
     * Verifies that the registry returns a copy of the registered device information on each invocation of the get
     * operation..
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetReturnsCopyOfOriginalData(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> addResult = Future.future();
        final Future<OperationResult<Device>> getResult = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), addResult);
        addResult
                .compose(r -> {
                    ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus()));
                    getDeviceManagementService().readDevice(TENANT, DEVICE, getResult);
                    return getResult;
                })
                .compose(r -> {
                    ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_OK, r.getStatus()));
                    r.getPayload().setExtensions(new HashMap<>());
                    r.getPayload().getExtensions().put("new-prop", true);

                    final Future<OperationResult<Device>> secondGetResult = Future.future();
                    getDeviceManagementService().readDevice(TENANT, DEVICE, secondGetResult);
                    return secondGetResult;
                })
                .setHandler(ctx.succeeding(secondGetResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_OK, secondGetResult.getStatus());
                        assertNotNull(getResult.result().getPayload().getExtensions().get("new-prop"));
                        assertNotEquals(getResult.result().getPayload(), secondGetResult.getPayload());
                        assertNotNull(secondGetResult.getPayload());
                        assertNull(secondGetResult.getPayload().getExtensions());
                        ctx.completeNow();
                    });
                }));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForDeregisteredDevice(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint get = ctx.checkpoint(3);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.compose(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            get.flag();
            final Future<Result<Void>> deregisterResult = Future.future();
            getDeviceManagementService().deleteDevice(TENANT, DEVICE, Optional.empty(), deregisterResult);
            return deregisterResult;
        }).compose(response -> {
            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
            get.flag();
            final Future<OperationResult<Device>> getResult = Future.future();
            getDeviceManagementService().readDevice(TENANT, DEVICE, getResult);
            return getResult;
        }).setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
            get.flag();
        })));
    }

    /**
     * Verify that registering a device without a device ID successfully creates a device
     * assigned with a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedForMissingDeviceId(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), result);
        result.setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            final String deviceId = response.getPayload().getId();
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            assertNotNull(deviceId);
            ctx.completeNow();
        })));
    }

    /**
     * Verify that registering a device without a device ID successfully creates a device
     * assigned with a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedAndContainsResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            final String deviceId = response.getPayload().getId();
            final String resourceVersion = response.getResourceVersion().orElse(null);
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            assertEquals(DEVICE, deviceId);
            assertNotNull(resourceVersion);
            ctx.completeNow();
        })));
    }

    /**
     * Verify that updating a device fails when the request contain a non matching resourceVersion.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateFailsWithInvalidResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.of(resourceVersion + "abc"),
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that updating a device succeed when the request contain an empty resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSucceedWithMissingResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.empty(),
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that updating a device succeeds when the request contain the matching resourceVersion.
     * Also verify that a new resourceVersion is returned with the update result.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSucceedWithCorrectResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);
        final JsonObject version = new JsonObject();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);
            version.put("1", resourceVersion);

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.of(resourceVersion),
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    final String secondResourceVersion = response.getResourceVersion().orElse(null);

                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    assertNotEquals(secondResourceVersion, version.getString("1"));
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain an empty resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteSucceedWithMissingResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.empty(), update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain the matching resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteSucceedWithCorrectResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.of(resourceVersion), update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain the matching resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteFailsWithInvalidResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.of(resourceVersion+10), update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getStatus());
                    register.flag();
                })));
    }


    /**
     * Asserts that a device is registered.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A succeeded future if the device is registered.
     */
    protected final Future<OperationResult<Device>> assertReadDevice(final String tenantId, final String deviceId) {
        final Future<OperationResult<Device>> result = Future.future();
        getDeviceManagementService().readDevice(tenantId, deviceId, result);
        return result.map(r -> {
            if (r.getStatus() == HttpURLConnection.HTTP_OK) {
                return r;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }
}
