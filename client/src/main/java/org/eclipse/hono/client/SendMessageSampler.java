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

package org.eclipse.hono.client;

import org.apache.qpid.proton.amqp.transport.DeliveryState;

/**
 * An interface for sampling <em>send message</em> operations.
 */
public interface SendMessageSampler {

    /**
     * A factory for creating samplers.
     */
    interface Factory {

        /**
         * Create a new sampler.
         *
         * @param messageType The message type to create a sampler for.
         * @return A new sampler.
         */
        SendMessageSampler create(String messageType);

        /**
         * Get a default, no-op implementation.
         *
         * @return A no-op implementation, never returns {@code null}.
         */
        static SendMessageSampler.Factory noop() {
            return Noop.FACTORY;
        }

    }

    /**
     * Get a default, no-op implementation.
     *
     * @return A no-op implementation, never returns {@code null}.
     */
    static SendMessageSampler noop() {
        return Noop.SAMPLER;
    }

    /**
     * A default no-op implementations.
     */
    class Noop {

        private static final Sample SAMPLE = new Sample() {

            @Override
            public void completed(final String outcome) {
            }

            @Override
            public void timeout() {
            }

        };

        private static final SendMessageSampler SAMPLER = new SendMessageSampler() {

            @Override
            public Sample start(final String tenantId) {
                return SAMPLE;
            }

            @Override
            public void queueFull(final String tenantId) {
            }

        };

        private static final Factory FACTORY = new Factory() {

            @Override
            public SendMessageSampler create(final String messageType) {
                return SAMPLER;
            }

        };

        private Noop() {
        }

    }

    /**
     * An active sample instance.
     */
    interface Sample {

        String OUTCOME_ABORTED = "aborted";

        /**
         * Call when the message was processed by the remote peer.
         *
         * @param outcome The outcome of the message. This is expected to be one of AMQP dispositions.
         */
        void completed(String outcome);

        /**
         * Call when the message was processed by the remote peer.
         * <p>
         * This method will use the <em>simple class name</em> of the delivery state parameter. If the
         * parameter is {@code null} the operating is considered <em>aborted</em> and the value {@link #OUTCOME_ABORTED}
         * will be used instead.
         *
         * @param remoteState The remote state.
         */
        default void completed(final DeliveryState remoteState) {
            if (remoteState == null) {
                completed(OUTCOME_ABORTED);
            } else {
                completed(remoteState.getClass().getSimpleName().toLowerCase());
            }
        }

        /**
         * Call when the operation timed out.
         */
        void timeout();

    }

    /**
     * Start operation.
     *
     * @param tenantId The tenant ID to sample for.
     * @return A sample instance.
     */
    Sample start(String tenantId);

    /**
     * Record a case of "queue full".
     *
     * @param tenantId The tenant ID to sample for.
     */
    void queueFull(String tenantId);

}
