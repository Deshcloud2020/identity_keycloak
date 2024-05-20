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

package org.keycloak.protocol.oidc.grants.ciba.clientpolicy.context;

import jakarta.ws.rs.core.MultivaluedMap;

import org.keycloak.protocol.oidc.grants.ciba.channel.CIBAAuthenticationRequest;
import org.keycloak.protocol.oidc.grants.ciba.endpoints.request.BackchannelAuthenticationEndpointRequest;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyEvent;

/**
 * @author <a href="mailto:takashi.norimatsu.ws@hitachi.com">Takashi Norimatsu</a>
 */
public class BackchannelAuthenticationRequestContext implements ClientPolicyContext {

    private final BackchannelAuthenticationEndpointRequest request;
    private final CIBAAuthenticationRequest parsedRequest;
    private final MultivaluedMap<String, String> requestParameters;

    public BackchannelAuthenticationRequestContext(BackchannelAuthenticationEndpointRequest request,
            CIBAAuthenticationRequest parsedRequest,
            MultivaluedMap<String, String> requestParameters) {
        this.request = request;
        this.parsedRequest = parsedRequest;
        this.requestParameters = requestParameters;
    }

    @Override
    public ClientPolicyEvent getEvent() {
        return ClientPolicyEvent.BACKCHANNEL_AUTHENTICATION_REQUEST;
    }

    public BackchannelAuthenticationEndpointRequest getRequest() {
        return request;
    }

    public CIBAAuthenticationRequest getParsedRequest() {
        return parsedRequest;
    }

    public MultivaluedMap<String, String> getRequestParameters() {
        return requestParameters;
    }
}
