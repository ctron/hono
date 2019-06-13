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
package org.eclipse.hono.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Constants &amp; utility methods used throughout the Device Management API.
 */
public final class DeviceManagementConstants extends RequestResponseApiConstants {

    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>assert device registration</em> operation.
     */
    public static final String ACTION_CREATE = "create";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>get registration information</em> operation.
     */
    public static final String ACTION_GET = "get";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>deregister device</em> operation.
     */
    public static final String ACTION_DELETE = "delete";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>update device registration</em> operation.
     */
    public static final String ACTION_UPDATE = "update";

    /**
     * The name of the Device Registration HTTP API endpoint.
     */
    public static final String DEVICES_HTTP_ENDPOINT = "devices";

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_DEVICE_IN = "device.mgmt.in";

    private static final Set<String> ACTIONS = new HashSet<>(
            Arrays.asList(ACTION_CREATE, ACTION_GET, ACTION_DELETE, ACTION_UPDATE));

    private DeviceManagementConstants() {
        // prevent instantiation
    }

    /**
     * Checks if a given string represents a valid action.
     *
     * @param action The string to check.
     * @return {@code true} if the given string is a supported action.
     */
    public static boolean isValidAction(final String action) {
        if (action == null) {
            return false;
        } else {
            return ACTIONS.contains(action);
        }
    }
}
