/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
 *
 */

package org.keycloak.services.clienttype.impl;

import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.representations.idm.ClientTypeRepresentation;
import org.keycloak.client.clienttype.ClientType;
import org.keycloak.client.clienttype.ClientTypeException;
import org.keycloak.client.clienttype.ClientTypeProvider;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class DefaultClientTypeProvider implements ClientTypeProvider {

    private static final Logger logger = Logger.getLogger(DefaultClientTypeProvider.class);

    @Override
    public ClientType getClientType(ClientTypeRepresentation clientTypeRep) {
        return new DefaultClientType(clientTypeRep);
    }

    @Override
    public ClientTypeRepresentation checkClientTypeConfig(ClientTypeRepresentation clientType)  throws ClientTypeException {
        Map<String, ClientTypeRepresentation.PropertyConfig> config = clientType.getConfig();
        for (Map.Entry<String, ClientTypeRepresentation.PropertyConfig> entry : config.entrySet()) {
            String propertyName = entry.getKey();
            ClientTypeRepresentation.PropertyConfig propConfig = entry.getValue();

            if (propConfig.getApplicable() == null) {
                logger.errorf("Property '%s' does not have 'applicable' configured for client type '%s'", propertyName, clientType.getName());
                throw new ClientTypeException("Invalid configuration of 'applicable' property on client type");
            }

            // Not supported to set read-only or default-value for properties, which are not applicable for the particular client
            if (!propConfig.getApplicable() && (propConfig.getReadOnly() != null || propConfig.getDefaultValue() != null)) {
                logger.errorf("Property '%s' is not applicable and so should not have read-only or default-value set for client type '%s'", propertyName, clientType.getName());
                throw new ClientTypeException("Invalid configuration of property on client type");
            }
        }

        // TODO:client-types retype configuration
        return clientType;
    }
}