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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link CommonSecret} and others.
 */
public class SecretsTest {

    /**
     * Test encoding of a simple password secret.
     */
    @Test
    public void testEncodePasswordSecret1() {

        final PasswordSecret secret = new PasswordSecret();
        secret.setAuthId("foo");

        secret.setNotAfter(Instant.EPOCH);
        secret.setNotAfter(Instant.EPOCH.plusMillis(1));

        secret.setComment("setec astronomy");

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("hashed-password", json.getString(CredentialsConstants.FIELD_TYPE));
        assertEquals("foo", json.getString(CredentialsConstants.FIELD_AUTH_ID));
        assertEquals("abc", json.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        assertEquals("setec astronomy", json.getString("comment"));
        assertEquals("hashed-password", json.getString(CredentialsConstants.FIELD_TYPE));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {
        final PskSecret secret = new PskSecret();

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("psk", json.getString(CredentialsConstants.FIELD_TYPE));

        final CommonSecret decodedSecret = Json.decodeValue(Json.encodeToBuffer(secret), CommonSecret.class);
        assertTrue(decodedSecret instanceof PskSecret);
    }

    /**
     * Test encoding a x509 secret.
     */
    @Test
    public void testEncodeX509Secret() {
        final X509CertificateSecret secret = new X509CertificateSecret();

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("x509-cert", json.getString(CredentialsConstants.FIELD_TYPE));
    }

    /**
     * Test encoding an array of secrets.
     */
    @Test
    public void testEncodeArray() {
        testEncodeMany(secrets -> secrets.toArray(CommonSecret[]::new));
    }

    /**
     * Test encoding an array of secrets.
     */
    @Test
    @Disabled("Encoding bug")
    public void testEncodeList() {
        testEncodeMany(secrets -> secrets);
    }

    /**
     * Test encoding a list of secrets.
     * 
     * @param provider The payload provider.
     */
    protected void testEncodeMany(final Function<List<CommonSecret>, Object> provider) {
        final List<CommonSecret> secrets = new ArrayList<>();

        final PskSecret secret = new PskSecret();
        secret.setAuthId("auth");
        secret.setKey("foo".getBytes(StandardCharsets.UTF_8));
        secrets.add(secret);

        final String json = Json.encode(provider.apply(secrets));

        final JsonArray array = new JsonArray(json);
        for (final Object o : array) {
            assertTrue(o instanceof JsonObject);
            assertNotNull(((JsonObject) o).getString("type"));
        }
    }
}
