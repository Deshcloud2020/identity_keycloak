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
 *
 */

package org.keycloak.protocol.oidc;

import org.keycloak.Config;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class OIDCProviderConfig {

    private final boolean legacyLogoutRedirectUri;
    private final boolean suppressLogoutConfirmationScreen;

    public OIDCProviderConfig(Config.Scope config) {
        this.legacyLogoutRedirectUri = config.getBoolean(OIDCLoginProtocolFactory.CONFIG_LEGACY_LOGOUT_REDIRECT_URI, false);
        this.suppressLogoutConfirmationScreen = config.getBoolean(OIDCLoginProtocolFactory.SUPPRESS_LOGOUT_CONFIRMATION_SCREEN, false);
    }

    public boolean isLegacyLogoutRedirectUri() {
        return legacyLogoutRedirectUri;
    }

    public boolean suppressLogoutConfirmationScreen() {
        return suppressLogoutConfirmationScreen;
    }
}
