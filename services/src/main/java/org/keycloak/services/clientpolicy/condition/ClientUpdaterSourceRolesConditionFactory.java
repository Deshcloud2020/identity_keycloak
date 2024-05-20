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
 */

package org.keycloak.services.clientpolicy.condition;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * @author <a href="mailto:takashi.norimatsu.ws@hitachi.com">Takashi Norimatsu</a>
 */
public class ClientUpdaterSourceRolesConditionFactory extends AbstractClientPolicyConditionProviderFactory {

    public static final String PROVIDER_ID = "client-updater-source-roles";

    public static final String ROLES = "roles";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        addCommonConfigProperties(configProperties);

        ProviderConfigProperty property;
        property = new ProviderConfigProperty(ROLES, PROVIDER_ID + ".label", PROVIDER_ID + ".tooltip", ProviderConfigProperty.MULTIVALUED_STRING_TYPE, "admin");
        configProperties.add(property);
    }

    @Override
    public ClientPolicyConditionProvider create(KeycloakSession session) {
        return new ClientUpdaterSourceRolesCondition(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "The condition checks the role of the entity who tries to create/update the client to determine whether the policy is applied.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
}
