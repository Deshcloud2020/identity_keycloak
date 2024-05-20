/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.services.clientpolicy.context;

import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyEvent;
import org.keycloak.services.clientregistration.ClientRegistrationContext;

public class DynamicClientUpdateContext extends AbstractDynamicClientCRUDContext {

    private final ClientModel clientToBeUpdated;
    private final ClientRepresentation proposedClientRepresentation;

    public DynamicClientUpdateContext(ClientRegistrationContext context, ClientModel proposedClientRepresentation, JsonWebToken token, RealmModel realm) {
        super(context.getSession(), token, realm);
        this.clientToBeUpdated = proposedClientRepresentation;
        this.proposedClientRepresentation = context.getClient();
    }

    @Override
    public ClientPolicyEvent getEvent() {
        return ClientPolicyEvent.UPDATE;
    }

    @Override
    public ClientRepresentation getProposedClientRepresentation() {
        return proposedClientRepresentation;
    }

    @Override
    public ClientModel getTargetClient() {
        return clientToBeUpdated;
    }
}
