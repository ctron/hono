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
package org.eclipse.hono.tests.registry;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.management.credentials.CommonSecret;
import org.eclipse.hono.service.management.credentials.GenericSecret;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.junit.Assert.assertNotNull;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Credentials HTTP endpoint and validating the corresponding responses.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsHttpIT {

    private static final String HTTP_HEADER_ETAG = HttpHeaders.ETAG.toString();

    private static final String TENANT = DEFAULT_TENANT;
    private static final String TEST_AUTH_ID = "sensor20";
    private static final Vertx vertx = Vertx.vertx();

    private static final String ORIG_BCRYPT_PWD;
    private static DeviceRegistryHttpClient registry;

    static {
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS);
        ORIG_BCRYPT_PWD = encoder.encode("thePassword");
    }

    /**
     * Time out each test after 5 secs.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private String deviceId;
    private String authId;
    private PasswordSecret hashedPasswordSecret;
    private PskSecret pskCredentials;

    /**
     * Creates the HTTP client for accessing the registry.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);

    }

    /**
     * Sets up the fixture.
     * 
     * @param ctx The test context.
     */
    @Before
    public void setUp(final TestContext ctx) {
        deviceId = UUID.randomUUID().toString();
        authId = getRandomAuthId(TEST_AUTH_ID);
        hashedPasswordSecret = AbstractCredentialsServiceTest.createPasswordSecret(authId, ORIG_BCRYPT_PWD);
        pskCredentials = newPskCredentials(authId);
        final Async creation = ctx.async();
        registry.registerDevice(DEFAULT_TENANT, deviceId).setHandler(attempt -> creation.complete());
        creation.await();
    }

    /**
     * Removes the credentials that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void removeCredentials(final TestContext ctx) {
        final Async deletion = ctx.async();
        registry
                .removeAllCredentials(TENANT, deviceId, HttpURLConnection.HTTP_NO_CONTENT)
                .setHandler(attempt -> deletion.complete());
        deletion.await();
    }

    /**
     * Shuts down the client.
     * 
     * @param context The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an add credentials request containing valid credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceeds(final TestContext context)  {

        registry
                .updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that when a device is created, an associated entry is created in the credential Service.
     * TODO : The credentials array should be empty.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddDeviceTriggerCredentialsCreation(final TestContext context) {

        registry.registerDevice(TENANT, deviceId).compose(ok -> registry.getCredentials(TENANT, deviceId)
                .setHandler(context.asyncAssertSuccess()));
    }

    /**
     * Verifies that the service accepts an add credentials request containing a clear text password.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Ignore("credentials ext concept")
    public void testAddCredentialsSucceedsForAdditionalProperties(final TestContext context) {

        final PasswordSecret secret = AbstractCredentialsServiceTest.createPasswordSecret(authId, "thePassword");
        // FIXME: secret.setProperty("client-id", "MQTT-client-2384236854");

        registry.addCredentials(TENANT, deviceId, Collections.singleton(secret))
                .compose(createAttempt -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(b -> {
                    final JsonObject obj = b.toJsonObject();
                    context.assertEquals("MQTT-client-2384236854", extractFirstCredential(obj).getString("client-id"));
                }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with a Content-Type other than
     * application/json.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForWrongContentType(final TestContext context) {

        registry
                .updateCredentials(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordSecret),
                        "application/x-www-form-urlencoded",
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verifies that the service rejects a request to add credentials of a type for which the device already has
     * existing credentials with a 409.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsRejectsDuplicateRegistration(final TestContext context) {

        registry
                .updateCredentials(
                        TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> {
                    final var etag = ar.get(HTTP_HEADER_ETAG);
                    System.out.println(ar);
                    assertNotNull("missing etag header", etag);
                    // now try to update credentials with the same version
                    return registry.updateCredentialsWithVersion(TENANT, deviceId,
                            Collections.singleton(hashedPasswordSecret), etag, HttpURLConnection.HTTP_PRECON_FAILED);
                })
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with hashed password
     * credentials that use a BCrypt hash with more than the configured max iterations.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Ignore
    public void testAddCredentialsFailsForBCryptWithTooManyIterations(final TestContext context)  {

        // GIVEN a hashed password using bcrypt with more than the configured max iterations
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS + 1);

        final PasswordSecret secret = new PasswordSecret();
        secret.setAuthId(authId);
        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        secret.setPasswordHash(encoder.encode("thePassword"));

        // WHEN adding the credentials
        registry.updateCredentials(
                TENANT,
                deviceId,
                Collections.singleton(secret),
                // THEN the request fails with 400
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with an empty body.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForEmptyBody(final TestContext context) {

        registry.updateCredentialsRaw(TENANT, deviceId, null, CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingType(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_TYPE);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_AUTH_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingAuthId(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_AUTH_ID);
    }

    private void testAddCredentialsWithMissingPayloadParts(final TestContext context, final String fieldMissing) {

        final JsonObject json = JsonObject.mapFrom(hashedPasswordSecret);
        json.remove(fieldMissing);

        registry
                .updateCredentialsRaw(TENANT, deviceId, json.toBuffer(),
                        CrudHttpClient.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final TestContext context) {

        final PasswordSecret altered = JsonObject
                .mapFrom(hashedPasswordSecret)
                .mapTo(PasswordSecret.class);
        altered.setComment("test");

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, Collections.singleton(altered),
                        HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ur -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(gr -> {
                context.assertEquals("test", gr.toJsonArray().getJsonObject(0).getString("comment"));
            }));
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Ignore // TODO clear passwords
    public void testUpdateCredentialsSucceedsForClearTextPassword(final TestContext context) {

        final PasswordSecret secret = AbstractCredentialsServiceTest.createPasswordSecret(authId, "newPassword");

        registry.addCredentials(TENANT, deviceId, Collections.<CommonSecret> singleton(hashedPasswordSecret))
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, secret))
                .compose(ur -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(gr -> {
                    final CredentialsObject o = extractFirstCredential(gr.toJsonObject())
                            .mapTo(CredentialsObject.class);
                    context.assertEquals(authId, o.getAuthId());
                    context.assertFalse(o.getCandidateSecrets(s -> CredentialsConstants.getPasswordHash(s))
                            .stream().anyMatch(hash -> ORIG_BCRYPT_PWD.equals(hash)));
                }));
    }

    /**
     * Verifies that the service rejects an update request for non-existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingCredentials(final TestContext context) {
        registry
                .updateCredentialsWithVersion(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordSecret),
                        "3",
                        HttpURLConnection.HTTP_PRECON_FAILED)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the device-id.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsForDeviceSucceeds(final TestContext context) {

        registry

                .updateCredentials(TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordSecret),
                        HttpURLConnection.HTTP_NO_CONTENT)

                .compose(ar -> {
                    return registry.removeAllCredentials(TENANT, deviceId, HttpURLConnection.HTTP_NO_CONTENT);
                })

                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a request to delete all credentials for a device fails if no credentials exist
     * for the device.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsForDeviceFailsForNonExistingCredentials(final TestContext context) {

        registry.removeAllCredentials(TENANT, "non-existing-device", HttpURLConnection.HTTP_NOT_FOUND)
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and
     * authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentials(final TestContext context) {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(b -> {
                    context.assertEquals(
                            new JsonArray()
                                    .add(JsonObject.mapFrom(hashedPasswordSecret)),
                            b.toJsonArray());
                }));

    }

    /**
     * Verify that multiple (2) correctly added credentials records of the same authId can be successfully looked up by
     * single requests using their type and authId again.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Ignore
    public void testGetAddedCredentialsMultipleTypesSingleRequests(final TestContext context) {

        final List<CommonSecret> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(hashedPasswordSecret);
        credentialsListToAdd.add(pskCredentials);

        registry
                .addCredentials(TENANT, deviceId, credentialsListToAdd)

                .compose(ar -> registry.getCredentials(TENANT, authId,
                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))

                .compose(body -> {
                    context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(
                            body.toJsonObject(),
                            JsonObject.mapFrom(hashedPasswordSecret)));
                    return registry.getCredentials(TENANT, authId,
                            CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
                })

                .setHandler(context.asyncAssertSuccess(body -> {
                    context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(
                            body.toJsonObject(),
                            JsonObject.mapFrom(pskCredentials)));
                }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of authentication identifier.
     * <p>
     * The returned JsonObject must consist of the total number of entries and contain all previously added credentials
     * in the provided JsonArray that is found under the key of the endpoint {@link CredentialsConstants#CREDENTIALS_ENDPOINT}.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    @Ignore //TODO change to set properly multiple credentials
    public void testGetAllCredentialsForDeviceSucceeds(final TestContext context) throws InterruptedException {

        final List<CommonSecret> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(newPskCredentials("auth"));
        credentialsListToAdd.add(newPskCredentials("other-auth"));

        registry.addCredentials(TENANT, deviceId, credentialsListToAdd)
            .compose(ar -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsListToAdd);
            }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of type.
     * <p>
     * The returned JsonObject must consist of the total number of entries and contain all previously added credentials
     * in the provided JsonArray that is found under the key of the endpoint {@link CredentialsConstants#CREDENTIALS_ENDPOINT}.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    @Ignore
    public void testGetCredentialsForDeviceRegardlessOfType(final TestContext context) throws InterruptedException {

        final String pskAuthId = getRandomAuthId(TEST_AUTH_ID);
        final List<CommonSecret> credentialsToAdd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final GenericSecret secret = new GenericSecret();
            secret.setAuthId(pskAuthId);
            secret.setType("type" + i);
            credentialsToAdd.add(secret);
        }

        registry.addCredentials(TENANT, deviceId, credentialsToAdd)
            .compose(ar -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsToAdd);
            }));
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final TestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(TENANT, authId, "wrong-type", HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final TestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordSecret),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(
                    TENANT,
                    "wrong-auth-id",
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
    }

    private static void assertResponseBodyContainsAllCredentials(final TestContext context, final JsonObject responseBody,
            final List<CommonSecret> credentialsList) {

        // the response must contain all of the payload of the add request, so test that now
        context.assertTrue(responseBody.containsKey(CredentialsConstants.FIELD_CREDENTIALS_TOTAL));
        final Integer totalCredentialsFound = responseBody.getInteger(CredentialsConstants.FIELD_CREDENTIALS_TOTAL);
        context.assertEquals(totalCredentialsFound, credentialsList.size());
        context.assertTrue(responseBody.containsKey(CredentialsConstants.CREDENTIALS_ENDPOINT));
        final JsonArray credentials = responseBody.getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT);
        context.assertNotNull(credentials);
        context.assertEquals(credentials.size(), totalCredentialsFound);
        // TODO: add full test if the lists are 'identical' (contain the same JsonObjects by using the
        //       contained helper method)
    }

    private static String getRandomAuthId(final String authIdPrefix) {
        return authIdPrefix + "." + UUID.randomUUID();
    }

    private static PskSecret newPskCredentials(final String authId) {

        final PskSecret secret = new PskSecret();
        secret.setAuthId(authId);
        secret.setKey("secret".getBytes(StandardCharsets.UTF_8));
        return secret;

    }

    private JsonObject extractFirstCredential(final JsonObject json) {
        return json.getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT).getJsonObject(0);
    }
}
