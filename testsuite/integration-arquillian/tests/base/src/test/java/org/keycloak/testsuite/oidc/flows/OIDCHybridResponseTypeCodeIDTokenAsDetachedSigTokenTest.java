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

package org.keycloak.testsuite.oidc.flows;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.events.Details;
import org.keycloak.jose.jws.crypto.HashUtils;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.utils.OIDCResponseType;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.util.OAuthClient;

import java.util.Arrays;
import java.util.List;

/**
 * Tests with response_type=code id_token token as detached signature
 *
 * @author <a href="mailto:takashi.norimatsu.ws@hitachi.com">Takashi Norimatsu</a>
 */
public class OIDCHybridResponseTypeCodeIDTokenAsDetachedSigTokenTest extends AbstractOIDCResponseTypeTest {

    @Before
    public void clientConfiguration() {
        clientManagerBuilder().standardFlow(true).implicitFlow(true).updateAttribute(OIDCConfigAttributes.ID_TOKEN_AS_DETACHED_SIGNATURE, Boolean.TRUE.toString());

        oauth.clientId("test-app");
        oauth.responseType(OIDCResponseType.CODE + " " + OIDCResponseType.ID_TOKEN + " "  + OIDCResponseType.TOKEN);
    }


    @Override
    protected boolean isFragment() {
        return true;
    }


    protected List<IDToken> testAuthzResponseAndRetrieveIDTokens(OAuthClient.AuthorizationEndpointResponse authzResponse, EventRepresentation loginEvent) {
        Assert.assertEquals(OIDCResponseType.CODE + " " + OIDCResponseType.ID_TOKEN + " " + OIDCResponseType.TOKEN, loginEvent.getDetails().get(Details.RESPONSE_TYPE));

        // IDToken from the authorization response
        Assert.assertNotNull(authzResponse.getAccessToken());
        String idTokenStr = authzResponse.getIdToken();
        IDToken idToken = oauth.verifyIDToken(idTokenStr);
        // confirm ID token as detached signature does not include authenticated user's claims
        Assert.assertNull(idToken.getEmailVerified());
        Assert.assertNull(idToken.getName());
        Assert.assertNull(idToken.getPreferredUsername());
        Assert.assertNull(idToken.getGivenName());
        Assert.assertNull(idToken.getFamilyName());
        Assert.assertNull(idToken.getEmail());

        // Validate "at_hash"
        assertValidAccessTokenHash(idToken.getAccessTokenHash(), authzResponse.getAccessToken());

        // Validate "c_hash"
        assertValidCodeHash(idToken.getCodeHash(), authzResponse.getCode());

        // Financial API - Part 2: Read and Write API Security Profile
        // http://openid.net/specs/openid-financial-api-part-2.html#authorization-server
        // Validate "s_hash"
        Assert.assertNotNull(idToken.getStateHash());

        Assert.assertEquals(idToken.getStateHash(), HashUtils.accessTokenHash(getIdTokenSignatureAlgorithm(), authzResponse.getState()));

        // Validate if token_type is present
        Assert.assertNotNull(authzResponse.getTokenType());

        // Validate if expires_in is present
        Assert.assertNotNull(authzResponse.getExpiresIn());

        // IDToken exchanged for the code
        IDToken idToken2 = sendTokenRequestAndGetIDToken(loginEvent);
        // confirm ordinal ID token includes authenticated user's claims
        Assert.assertNotNull(idToken2.getEmailVerified());
        Assert.assertNotNull(idToken2.getName());
        Assert.assertNotNull(idToken2.getPreferredUsername());
        Assert.assertNotNull(idToken2.getGivenName());
        Assert.assertNotNull(idToken2.getFamilyName());
        Assert.assertNotNull(idToken2.getEmail());

        return Arrays.asList(idToken, idToken2);
    }

    @Test
    public void nonceNotUsedErrorExpected() {
        super.validateNonceNotUsedErrorExpected();
    }

    @Test
    public void errorStandardFlowNotAllowed() throws Exception {
        super.validateErrorStandardFlowNotAllowed();
    }

    @Test
    public void errorImplicitFlowNotAllowed() throws Exception {
        super.validateErrorImplicitFlowNotAllowed();
    }
}
