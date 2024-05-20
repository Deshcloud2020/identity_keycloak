/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.protocol.oid4vc.issuance.signing;

import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oid4vc.issuance.OffsetTimeProvider;
import org.keycloak.protocol.oid4vc.issuance.VCIssuerException;
import org.keycloak.protocol.oid4vc.model.Format;
import org.keycloak.provider.ConfigurationValidationHelper;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;
import java.util.Optional;


/**
 * Provider Factory to create {@link  JwtSigningService}s
 *
 * @author <a href="https://github.com/wistefan">Stefan Wiedemann</a>
 */
public class JwtSigningServiceProviderFactory implements VCSigningServiceProviderFactory {

    public static final Format SUPPORTED_FORMAT = Format.JWT_VC;
    private static final String HELP_TEXT = "Issues JWT-VCs following the specification of https://identity.foundation/jwt-vc-presentation-profile/.";

    @Override
    public VerifiableCredentialsSigningService create(KeycloakSession session, ComponentModel model) {

        String keyId = model.get(SigningProperties.KEY_ID.getKey());
        String algorithmType = model.get(SigningProperties.ALGORITHM_TYPE.getKey());
        String tokenType = model.get(SigningProperties.TOKEN_TYPE.getKey());
        String issuerDid = Optional.ofNullable(
                        session
                                .getContext()
                                .getRealm()
                                .getAttribute(ISSUER_DID_REALM_ATTRIBUTE_KEY))
                .orElseThrow(() -> new VCIssuerException("No issuerDid configured."));

        return new JwtSigningService(session, keyId, algorithmType, tokenType, issuerDid, new OffsetTimeProvider());
    }

    @Override
    public String getHelpText() {
        return HELP_TEXT;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return VCSigningServiceProviderFactory.configurationBuilder()
                .property(SigningProperties.ALGORITHM_TYPE.asConfigProperty())
                .property(SigningProperties.TOKEN_TYPE.asConfigProperty())
                .build();
    }

    @Override
    public String getId() {
        return SUPPORTED_FORMAT.toString();
    }

    @Override
    public void validateSpecificConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        ConfigurationValidationHelper.check(model)
                .checkRequired(SigningProperties.TOKEN_TYPE.asConfigProperty())
                .checkRequired(SigningProperties.ALGORITHM_TYPE.asConfigProperty());
    }

    @Override
    public Format supportedFormat() {
        return SUPPORTED_FORMAT;
    }
}