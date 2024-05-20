/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.integration.resteasy;

import io.quarkus.arc.Arc;

import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler;
import org.keycloak.models.KeycloakSession;
import org.keycloak.utils.KeycloakSessionUtil;

public final class TransactionalSessionHandler implements ServerRestHandler, org.keycloak.quarkus.runtime.transaction.TransactionalSessionHandler {

    @Override
    public void handle(ResteasyReactiveRequestContext requestContext) {
        requestContext.requireCDIRequestScope();
        KeycloakSession currentSession = Arc.container().instance(KeycloakSession.class).get();
        // this handler might be invoked multiple times when resolving sub-resources
        // make sure the transaction is began once when the session is first associated with the thread
        if (KeycloakSessionUtil.setKeycloakSession(currentSession) == null) {
            beginTransaction(currentSession);
        }
    }
}
