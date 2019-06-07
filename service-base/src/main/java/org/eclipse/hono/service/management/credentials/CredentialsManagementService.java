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

import java.util.List;
import java.util.Optional;

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A service for keeping record of device credentials.
 * This interface presents all the available operations on the API.
 *
 * @see <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>
 */
public interface CredentialsManagementService {

    /**
     * Updates or create the set of credentials.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param credentials A list of credentials.
     *                  See <a href="https://www.eclipse.org/hono/api/Credentials-API/#credentials-format">Credentials Format</a> for details.
     * @param resourceVersion The identifier of the resource version to update.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials have been updated successfully.</li>
     *         <li><em>400 Bad Request</em> if the given credentials do not conform to the
     *         format defined by the Credentials API.</li>
     *         <li><em>404 Not Found</em> if no credentials of the given type and auth-id exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#update-credentials">
     *      Credentials API - Update Credentials</a>
     */
    void set(String tenantId, String deviceId, Optional<String> resourceVersion, List<CommonSecret> credentials,
            Handler<AsyncResult<OperationResult<Void>>> resultHandler);

    /**
     * Gets all credentials registered for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The device to get credentials for.
     * @param resultHandler The handler to invoke with the result of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if credentials of the given type and authentication identifier have been found. The
     *         <em>payload</em> will contain the credentials.</li>
     *         <li><em>404 Not Found</em> if no credentials matching the criteria exist.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void get(String tenantId, String deviceId, Handler<AsyncResult<OperationResult<List<CommonSecret>>>> resultHandler);

    /**
     * Removes all credentials for a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID under which the device is registered.
     * @param resultHandler The handler to invoke with the result of the operation.
     * @param resourceVersion The identifier of the resource version to update.
     *         <p>
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>204 No Content</em> if the credentials for the device have been removed.</li>
     *         <li><em>404 Not Found</em> if no credentials are registered for the device.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/credentials-api/#remove-credentials">
     *      Credentials API - Remove Credentials</a>
     */
    void remove(String tenantId, String deviceId, Optional<String> resourceVersion, Handler<AsyncResult<Result<Void>>> resultHandler);

}
