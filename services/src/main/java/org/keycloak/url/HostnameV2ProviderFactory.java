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

package org.keycloak.url;

import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.HostnameProviderFactory;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
public class HostnameV2ProviderFactory implements HostnameProviderFactory, EnvironmentDependentProviderFactory {
    private String hostname;
    private URI hostnameUrl;
    private URI adminUrl;
    private Boolean backchannelDynamic;

    // Simplified regexes for hostname validations; further validations are performed when instantiating URI object
    private static final String hostnameStringPattern = "[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*";
    private static final Pattern hostnamePattern = Pattern.compile("^" + hostnameStringPattern + "$");
    private static final Pattern hostnameUrlPattern = Pattern.compile("^(http|https)://" + hostnameStringPattern + "(:\\d+)?(/[\\w-]+)*/?$");

    @Override
    public void init(Config.Scope config) {
        // Strict mode is used just for enforcing that hostname is set
        Boolean strictMode = config.getBoolean("hostname-strict", false);

        String hostnameRaw = config.get("hostname");
        if (strictMode && hostnameRaw == null) {
            throw new IllegalArgumentException("hostname is not configured; either configure hostname, or set hostname-strict to false");
        } else if (hostnameRaw != null && !strictMode) {
            // We might not need this validation as it doesn't matter in this case if strict is true or false. It's just for consistency – hostname XOR !strict.
//            throw new IllegalArgumentException("hostname is configured, hostname-strict must be set to true");
        }

        // Set hostname, can be either a full URL, or just hostname
        if (hostnameRaw != null) {
            if (hostnamePattern.matcher(hostnameRaw).matches()) {
                hostname = hostnameRaw;
            }
            else {
                hostnameUrl = validateAndCreateUri(hostnameRaw, "Provided hostname is neither a plain hostname or a valid URL");
            }
        }

        Optional.ofNullable(config.get("hostname-admin")).ifPresent(h ->
                adminUrl = validateAndCreateUri(h, "Provided hostname-admin is not a valid URL"));

        // Dynamic backchannel requires hostname to be specified as full URL. Otherwise we might end up with some parts of the
        // backend request in frontend URLs. Therefore frontend (and admin) needs to be fully static.
        backchannelDynamic = config.getBoolean("hostname-backchannel-dynamic", false);
        if (hostname == null && hostnameUrl == null && backchannelDynamic) {
            throw new IllegalArgumentException("hostname-backchannel-dynamic must be set to false when no hostname is provided");
        }
        if (backchannelDynamic && hostnameUrl == null) {
            throw new IllegalArgumentException("hostname-backchannel-dynamic must be set to false if hostname is not provided as full URL");
        }
    }

    private URI validateAndCreateUri(String uri, String validationFailedMessage) {
        if (!hostnameUrlPattern.matcher(uri).matches()) {
            throw new IllegalArgumentException(validationFailedMessage);
        }
        try {
            return URI.create(uri);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(validationFailedMessage, e);
        }
    }

    @Override
    public HostnameProvider create(KeycloakSession session) {
        return new HostnameV2Provider(session, hostname, hostnameUrl, adminUrl, backchannelDynamic);
    }

    @Override
    public String getId() {
        return "v2";
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.HOSTNAME_V2);
    }
}
