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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.time.Instant;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

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

        secret.setNotAfter(Instant.EPOCH);
        secret.setNotAfter(Instant.EPOCH.plusMillis(1));

        secret.setComment("setec astronomy");

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("abc", json.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        assertEquals("setec astronomy", json.getString("comment"));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {
        final PskSecret secret = new PskSecret();
        secret.setKey(new byte[] { 1, 2, 3 });

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertArrayEquals(new byte[] { 1, 2, 3 }, json.getBinary("key"));
    }

    /**
     * Test encoding a x509 secret.
     */
    @Test
    public void testEncodeX509Secret() {
        final X509CertificateSecret secret = new X509CertificateSecret();

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);
    }

}