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

package org.eclipse.hono.service.auth.impl;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.hono.service.AbstractBaseApplication;
import org.eclipse.hono.service.HealthCheckProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Verticle;

/**
 * A Spring Boot application exposing an AMQP based endpoint for retrieving a JSON Web Token for
 * a connection that has been authenticated using SASL.
 *
 */
@ComponentScan(basePackages = { "org.eclipse.hono.service.auth", "org.eclipse.hono.service.metric" })
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractBaseApplication {

    /**
     * All the verticles.
     */
    private List<Verticle> verticles;

    /**
     * All the health check providers.
     */
    private List<HealthCheckProvider> healthCheckProviders;

    @Autowired
    public void setVerticles(final List<Verticle> verticles) {
        this.verticles = verticles;
    }

    @Autowired
    public void setHealthCheckProviders(final List<HealthCheckProvider> healthCheckProviders) {
        this.healthCheckProviders = healthCheckProviders;
    }

    @Override
    protected final Future<?> deployRequiredVerticles(final int maxInstances) {

        return super.deployRequiredVerticles(maxInstances).compose(s -> {

            @SuppressWarnings("rawtypes")
            final List<Future> futures = new LinkedList<>();

            for (final Verticle verticle : this.verticles) {
                log.info("Deploying: {}", verticle);
                final Future<String> result = Future.future();
                getVertx().deployVerticle(verticle, result);
            }

            return CompositeFuture.all(futures);

        });

    }

    /**
     * Registers any additional health checks that the service implementation components provide.
     *
     * @return A succeeded future.
     */
    @Override
    protected Future<Void> postRegisterServiceVerticles() {
        this.healthCheckProviders.forEach(this::registerHealthchecks);
        return Future.succeededFuture();
    }

    /**
     * Starts the Authentication Server.
     * 
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
