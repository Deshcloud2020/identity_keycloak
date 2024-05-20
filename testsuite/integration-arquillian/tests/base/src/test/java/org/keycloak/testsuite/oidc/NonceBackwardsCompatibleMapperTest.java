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

package org.keycloak.testsuite.oidc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.events.Details;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.NonceBackwardsCompatibleMapper;
import org.keycloak.protocol.oidc.utils.OIDCResponseMode;
import org.keycloak.protocol.oidc.utils.OIDCResponseType;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AuthorizationResponseToken;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.RefreshToken;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.AbstractTestRealmKeycloakTest;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.updaters.ClientAttributeUpdater;
import org.keycloak.testsuite.util.OAuthClient;

/**
 *
 * @author rmartinc
 */
public class NonceBackwardsCompatibleMapperTest extends AbstractTestRealmKeycloakTest {

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }

    @Test
    public void testNonceWithoutMapper() throws JsonProcessingException {
        testNonce(false);
    }

    @Test
    public void testNonceWithMapper() throws JsonProcessingException {
        ClientResource testApp = ApiUtil.findClientByClientId(testRealm(), "test-app");
        String mapperId = createNonceMapper(testApp);
        try {
            testNonce(true);
        } finally {
            testApp.getProtocolMappers().delete(mapperId);
        }
    }

    @Test
    public void testImplicitFlowWithoutMapper() throws Exception {
        try (ClientAttributeUpdater client = ClientAttributeUpdater.forClient(adminClient, TEST_REALM_NAME, "test-app")
                .setImplicitFlowEnabled(true)
                .update()) {
            testNonceImplicit(false);
        }
    }

    @Test
    public void testImplicitFlowWithMapper() throws Exception {
        ClientResource testApp = ApiUtil.findClientByClientId(testRealm(), "test-app");
        String mapperId = createNonceMapper(testApp);
        try (ClientAttributeUpdater client = ClientAttributeUpdater.forClient(adminClient, TEST_REALM_NAME, "test-app")
                .setImplicitFlowEnabled(true)
                .update()) {
            testNonceImplicit(true);
        } finally {
            testApp.getProtocolMappers().delete(mapperId);
        }
    }

    private String createNonceMapper(ClientResource testApp) {
        ProtocolMapperModel nonceMapper = NonceBackwardsCompatibleMapper.create("nonce");
        ProtocolMapperRepresentation nonceMapperRep = ModelToRepresentation.toRepresentation(nonceMapper);
        try (Response res = testApp.getProtocolMappers().createMapper(nonceMapperRep)) {
            Assert.assertEquals(Response.Status.CREATED.getStatusCode(), res.getStatus());
            return ApiUtil.getCreatedId(res);
        }
    }

    private void checkNonce(String expectedNonce, String nonce, boolean expected) {
        if (expected) {
            Assert.assertEquals(expectedNonce, nonce);
        } else {
            Assert.assertNull(nonce);
        }
    }

    private void testIntrospection(String accessToken, String expectedNonce, boolean expected) throws JsonProcessingException {
        String tokenResponse = oauth.introspectAccessTokenWithClientCredential("test-app", "password", accessToken);
        JsonNode nonce = new ObjectMapper().readTree(tokenResponse).get(OIDCLoginProtocol.NONCE_PARAM);
        checkNonce(expectedNonce, nonce != null? nonce.asText() : null, expected);
    }

    private void testNonceImplicit(boolean mapper) throws JsonProcessingException {
        String nonce = KeycloakModelUtils.generateId();
        oauth.nonce(nonce);
        oauth.responseMode(OIDCResponseMode.JWT.value());
        oauth.responseType(OIDCResponseType.TOKEN + " " + OIDCResponseType.ID_TOKEN);
        OAuthClient.AuthorizationEndpointResponse response = oauth.doLogin("test-user@localhost", "password");

        Assert.assertTrue(response.isRedirected());
        AuthorizationResponseToken responseToken = oauth.verifyAuthorizationResponseToken(response.getResponse());

        String accessTokenString = (String) responseToken.getOtherClaims().get("access_token");
        AccessToken token = oauth.verifyToken(accessTokenString);
        checkNonce(nonce, token.getNonce(), mapper);
        String idTokenString = (String) responseToken.getOtherClaims().get("id_token");
        IDToken idToken = oauth.verifyToken(idTokenString, IDToken.class);
        checkNonce(nonce, idToken.getNonce(), true);

        testIntrospection(accessTokenString, nonce, mapper);
        testIntrospection(idTokenString, nonce, true);
    }

    private void testNonce(boolean mapper) throws JsonProcessingException {
        String nonce = KeycloakModelUtils.generateId();
        oauth.nonce(nonce);
        oauth.doLogin("test-user@localhost", "password");
        EventRepresentation loginEvent = events.expectLogin().assertEvent();

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse response = oauth.doAccessTokenRequest(code, "password");

        AccessToken token = oauth.verifyToken(response.getAccessToken());
        checkNonce(nonce, token.getNonce(), mapper);
        IDToken idToken = oauth.verifyToken(response.getIdToken(), IDToken.class);
        checkNonce(nonce, idToken.getNonce(), true);
        RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());
        checkNonce(nonce, refreshToken.getNonce(), mapper);

        EventRepresentation tokenEvent = events.expectCodeToToken(loginEvent.getDetails().get(Details.CODE_ID),
                loginEvent.getSessionId()).assertEvent();

        response = oauth.doRefreshTokenRequest(response.getRefreshToken(), "password");
        events.expectRefresh(tokenEvent.getDetails().get(Details.REFRESH_TOKEN_ID),
                loginEvent.getSessionId()).assertEvent();

        token = oauth.verifyToken(response.getAccessToken());
        checkNonce(nonce, token.getNonce(), mapper);
        idToken = oauth.verifyToken(response.getIdToken(), IDToken.class);
        checkNonce(nonce, idToken.getNonce(), mapper);
        refreshToken = oauth.parseRefreshToken(response.getRefreshToken());
        checkNonce(nonce, refreshToken.getNonce(), mapper);

        testIntrospection(response.getAccessToken(), nonce, mapper);
    }
}
