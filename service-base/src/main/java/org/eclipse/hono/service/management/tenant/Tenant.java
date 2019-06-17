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

package org.eclipse.hono.service.management.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.hono.util.TenantConstants;

/**
 * Device Information.
 */
@JsonInclude(value = Include.NON_NULL)
public class Tenant {

    private Boolean enabled;

    @JsonProperty("ext")
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> extensions;

    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> defaults;

    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    @JsonInclude(value = Include.NON_EMPTY)
    private List<Map<String, Object>> adapters;

    @JsonProperty("limits")
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> limits;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> trustedCa;

    /**
     * Set the enabled property.
     *
     * @param enabled the value to assign
     * @return a reference to this for fluent use.
     */
    public Tenant setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Set the extension field.
     *
     * @param extensions the value to assign
     * @return a reference to this for fluent use.
     */
    public Tenant setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    public void setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public List<Map<String, Object>> getAdapters() {
        return adapters;
    }

    /**
     * Set the adapters field.
     *
     * @param adapters the value to assign
     * @return a reference to this for fluent use.
     */
    public Tenant setAdapters(final List<Map<String, Object>> adapters) {
        this.adapters = adapters;
        return this;
    }

    public Map<String, Object> getLimits() {
        return limits;
    }

    /**
     * Set the resources limits field.
     *
     * @param limits the value to assign
     * @return a reference to this for fluent use.
     */
    public Tenant setLimits(final Map<String, Object> limits) {
        this.limits = limits;
        return this;
    }

    public Map<String, Object> getTrustedCa() {
        return trustedCa;
    }

    /**
     * Set the trusted-ca field.
     *
     * @param trustedCa the value to assign
     * @return a reference to this for fluent use.
     */
    public Tenant setTrustedCa(final Map<String, Object> trustedCa) {
        this.trustedCa = trustedCa;
        return this;
    }

    /**
     * Add a new extension entry to the Tenant.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allowed chained invocations.
     */
    public Tenant putExtension(final String key, final Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }
}
