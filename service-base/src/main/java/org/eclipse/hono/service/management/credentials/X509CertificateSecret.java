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

import org.eclipse.hono.util.CredentialsConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * X509 certificate secret.
 */
@JsonTypeName(CredentialsConstants.SECRETS_TYPE_X509_CERT)
public class X509CertificateSecret extends CommonSecret {
}
