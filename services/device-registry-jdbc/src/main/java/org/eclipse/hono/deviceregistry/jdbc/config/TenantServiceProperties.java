/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Tenant service properties.
 */
public class TenantServiceProperties {

    private static final Duration DEFAULT_TENANT_TTL = Duration.ofMinutes(1);

    private Duration tenantTtl = DEFAULT_TENANT_TTL;

    public Duration getTenantTtl() {
        return this.tenantTtl;
    }

    /**
     * Set the tenant TTL.
     *
     * @param tenantTtl The tenant TTL.
     */
    public void setTenantTtl(final Duration tenantTtl) {
        Objects.requireNonNull(tenantTtl);

        if (tenantTtl.toSeconds() <= 0) {
            throw new IllegalArgumentException("'tenantTtl' must be a positive duration of at least one second");
        }
        this.tenantTtl = tenantTtl;
    }

}
