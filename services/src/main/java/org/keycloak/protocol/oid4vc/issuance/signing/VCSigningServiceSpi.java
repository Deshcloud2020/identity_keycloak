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

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * Spi implementation of the creation of {@link  VerifiableCredentialsSigningService}
 *
 * @author <a href="https://github.com/wistefan">Stefan Wiedemann</a>
 */
public class VCSigningServiceSpi implements Spi {
    private static final String NAME = "vcSigningService";

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return VerifiableCredentialsSigningService.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return VCSigningServiceProviderFactory.class;
    }
}
