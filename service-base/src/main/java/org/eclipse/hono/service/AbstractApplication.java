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

package org.eclipse.hono.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

/**
 * A base class for implementing Spring Boot applications.
 * <p>
 * This class requires that an instance of {@link ObjectFactory} is provided
 * ({@link #addServiceFactories(Set)} for each service to be exposed by this application.
 */
public class AbstractApplication extends AbstractBaseApplication {

    private final Set<ObjectFactory<? extends AbstractServiceBase<?>>> serviceFactories = new HashSet<>();

    @Override
    protected void preFlightCheck() throws IllegalStateException {
        if (serviceFactories.isEmpty()) {
            throw new IllegalStateException("no service factory has been configured");
        }
    }

    /**
     * Adds the factories to use for creating service instances to
     * deploy the Vert.x container during startup.
     * <p>
     * 
     * @param factories The service factories.
     * @throws NullPointerException if factories is {@code null}.
     */
    @Autowired
    public final void addServiceFactories(final Set<ObjectFactory<? extends AbstractServiceBase<?>>> factories) {
        Objects.requireNonNull(factories);
        serviceFactories.addAll(factories);
        log.debug("added {} service factories", factories.size());
    }

    /**
     * Invoked before the service instances are being deployed.
     * <p>
     * May be overridden to prepare the environment for the service instances, e.g. deploying additional (prerequisite)
     * verticles.
     * <p>
     * This default implementation simply returns a succeeded future.
     * 
     * @param maxInstances The number of service verticle instances to deploy.
     * @return A future indicating success. Application start-up fails if the returned future fails.
     */
    protected Future<?> deployRequiredVerticles(final int maxInstances) {
        return Future.succeededFuture();
    }

    private Future<?> deployServiceVerticles(final int maxInstances) {
        final DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setInstances(maxInstances);

        @SuppressWarnings("rawtypes")
        final List<Future> deploymentTracker = new ArrayList<>();

        for (final ObjectFactory<? extends AbstractServiceBase<?>> serviceFactory : serviceFactories) {

            final Future<String> deployTracker = Future.future();
            getVertx().deployVerticle(serviceFactory::getObject, deploymentOptions, deployTracker);
            deploymentTracker.add(deployTracker);
        }

        return CompositeFuture.all(deploymentTracker);
    }

    @Override
    protected Future<?> deployVerticles() {
        // call into super ...
        return super.deployVerticles()
                // ... then deploy the required verticles
                .compose(s -> deployRequiredVerticles(getConfig().getMaxInstances()))
                // ... then deploy service verticles
                .compose(s -> deployServiceVerticles(getConfig().getMaxInstances()));
    }

}
