/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;

import org.eclipse.hono.service.deviceconnection.DeviceConnectionAmqpEndpoint;

import org.eclipse.hono.service.management.credentials.CredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementHttpEndpoint;

import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.registration.RegistrationAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantAmqpEndpoint;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Spring Boot configuration for the Device Registry application.
 *
 */
@Configuration
public class ApplicationConfig {

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * <p>
     * This method creates new Vert.x default options and invokes
     * {@link VertxProperties#configureVertx(VertxOptions)} on the object returned
     * by {@link #vertxProperties()}.
     * 
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        return Vertx.vertx(vertxProperties().configureVertx(new VertxOptions()));
    }

    /**
     * Exposes configuration properties for Vert.x.
     * 
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Exposes an OpenTracing {@code Tracer} as a Spring Bean.
     * <p>
     * The Tracer will be resolved by means of a Java service lookup.
     * If no tracer can be resolved this way, the {@code NoopTracer} is
     * returned.
     * 
     * @return The tracer.
     */
    @Bean
    public Tracer getTracer() {

        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    /**
     * Gets general properties for configuring the Device Registry Spring Boot application.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     * 
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpProperties() {
        final ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public RegistrationAmqpEndpoint registrationAmqpEndpoint() {
        return new RegistrationAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public CredentialsAmqpEndpoint credentialsAmqpEndpoint() {
        return new CredentialsAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public TenantAmqpEndpoint tenantAmqpEndpoint() {
        return new TenantAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public DeviceConnectionAmqpEndpoint deviceConnectionAmqpEndpoint() {
        return new DeviceConnectionAmqpEndpoint(vertx());
    }

    /**
     * Gets properties for configuring the Device Registry's REST endpoint.
     * 
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.rest")
    public ServiceConfigProperties restProperties() {
        final ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Device Registration</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public DeviceManagementHttpEndpoint registrationHttpEndpoint() {
        return new DeviceManagementHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Credentials</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public CredentialsManagementHttpEndpoint credentialsHttpEndpoint() {
        return new CredentialsManagementHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public TenantManagementHttpEndpoint tenantHttpEndpoint() {
        return new TenantManagementHttpEndpoint(vertx());
    }

    /**
     * Gets properties for configuring {@code FileBasedRegistrationService} which implements
     * the <em>Device Registration</em> API.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public FileBasedRegistrationConfigProperties serviceProperties() {
        return new FileBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedCredentialsService} which implements
     * the <em>Credentials</em> API.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public FileBasedCredentialsConfigProperties credentialsProperties() {
        return new FileBasedCredentialsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedTenantsService} which implements
     * the <em>Tenants</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.svc")
    public FileBasedTenantsConfigProperties tenantsProperties() {
        return new FileBasedTenantsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code InMemoryDeviceConnectionService} which implements
     * the <em>Device Connection</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.deviceConnection.svc")
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionsProperties() {
        return new MapBasedDeviceConnectionsConfigProperties();
    }

    /**
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));

    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), applicationConfigProperties());
    }

    /**
     * Exposes a password encoder to use for encoding clear text passwords
     * and for matching password hashes.
     * 
     * @return The encoder.
     */
    @Bean
    public HonoPasswordEncoder passwordEncoder() {
        return new SpringBasedHonoPasswordEncoder(credentialsProperties().getMaxBcryptIterations());
    }
}
