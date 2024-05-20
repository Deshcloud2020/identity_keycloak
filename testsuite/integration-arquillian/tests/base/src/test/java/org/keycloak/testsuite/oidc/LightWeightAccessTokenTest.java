/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.ClientScopeResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.Profile;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.OIDCLoginProtocolFactory;
import org.keycloak.protocol.oidc.mappers.AudienceProtocolMapper;
import org.keycloak.protocol.oidc.mappers.GroupMembershipMapper;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.protocol.oidc.mappers.HardcodedRole;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.RoleNameMapper;
import org.keycloak.protocol.oidc.mappers.SHA256PairwiseSubMapper;
import org.keycloak.protocol.oidc.mappers.UserSessionNoteMapper;
import org.keycloak.protocol.oidc.mappers.SessionStateMapper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.services.clientpolicy.condition.AnyClientConditionFactory;
import org.keycloak.services.clientpolicy.executor.UseLightweightAccessTokenExecutorFactory;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;
import org.keycloak.testsuite.client.policies.AbstractClientPoliciesTest;
import org.keycloak.testsuite.util.ClientManager;
import org.keycloak.testsuite.util.ClientPoliciesUtil;
import org.keycloak.testsuite.util.KeycloakModelUtils;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.ProtocolMapperUtil;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.keycloak.protocol.ProtocolMapperUtils.USER_SESSION_NOTE;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.ACR;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.ACR_SCOPE;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.ADDRESS;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.ALLOWED_WEB_ORIGINS;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.AUDIENCE_RESOLVE;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.CLIENT_ROLES;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.EMAIL;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.EMAIL_VERIFIED;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.FAMILY_NAME;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.FULL_NAME;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.GIVEN_NAME;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.PROFILE_CLAIM;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.REALM_ROLES;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.ROLES_SCOPE;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.USERNAME;
import static org.keycloak.protocol.oidc.OIDCLoginProtocolFactory.WEB_ORIGINS_SCOPE;
import static org.keycloak.protocol.oidc.mappers.AbstractPairwiseSubMapper.PROVIDER_ID_SUFFIX;
import static org.keycloak.protocol.oidc.mappers.AudienceProtocolMapper.INCLUDED_CLIENT_AUDIENCE;
import static org.keycloak.protocol.oidc.mappers.HardcodedClaim.CLAIM_VALUE;
import static org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN;
import static org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper.INCLUDE_IN_INTROSPECTION;
import static org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper.INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN;
import static org.keycloak.protocol.oidc.mappers.PairwiseSubMapperHelper.PAIRWISE_SUB_ALGORITHM_SALT;
import static org.keycloak.protocol.oidc.mappers.RoleNameMapper.NEW_ROLE_NAME;
import static org.keycloak.protocol.oidc.mappers.RoleNameMapper.ROLE_CONFIG;
import static org.keycloak.testsuite.admin.AbstractAdminTest.loadJson;
import static org.keycloak.testsuite.auth.page.AuthRealm.TEST;
import static org.keycloak.testsuite.util.ClientPoliciesUtil.createAnyClientConditionConfig;

@EnableFeature(value = Profile.Feature.TOKEN_EXCHANGE, skipRestart = true)
public class LightWeightAccessTokenTest extends AbstractClientPoliciesTest {
    private static final Logger logger = Logger.getLogger(LightWeightAccessTokenTest.class);
    private static String RESOURCE_SERVER_CLIENT_ID = "resource-server";
    private static String RESOURCE_SERVER_CLIENT_PASSWORD = "password";
    @Before
    public void clientConfiguration() {
        ClientManager.realm(adminClient.realm(REALM_NAME)).clientId(TEST_CLIENT).directAccessGrant(true).setServiceAccountsEnabled(true);
        ClientManager.realm(adminClient.realm(REALM_NAME)).clientId(RESOURCE_SERVER_CLIENT_ID).directAccessGrant(true);
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realm = loadJson(getClass().getResourceAsStream("/testrealm.json"), RealmRepresentation.class);
        UserRepresentation user = findUser(realm, TEST_USER_NAME);
        Map<String, List<String>> attributes = new HashMap<>(){{
            put("street", Arrays.asList("1 My Street"));
            put("locality", Arrays.asList("Cardiff"));
            put("region", Arrays.asList("Cardiff"));
            put("postal_code", Arrays.asList("CF104RA"));
        }};
        user.setAttributes(attributes);
        user.setGroups(Arrays.asList("/topGroup/level2group"));
        ClientRepresentation confApp = KeycloakModelUtils.createClient(realm, RESOURCE_SERVER_CLIENT_ID);
        confApp.setSecret(RESOURCE_SERVER_CLIENT_PASSWORD);
        confApp.setServiceAccountsEnabled(Boolean.TRUE);
        testRealms.add(realm);
    }

    private UserRepresentation findUser(RealmRepresentation testRealm, String userName) {
        for (UserRepresentation user : testRealm.getUsers()) {
            if (user.getUsername().equals(userName)) return user;
        }
        return null;
    }

    @Test
    public void accessTokenFalseIntrospectionTrueTest() throws IOException {
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, true);
        try {
            oauth.nonce("123456");
            oauth.scope("address");
            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = browserLogin(TEST_CLIENT_SECRET, TEST_USER_NAME, TEST_USER_PASSWORD).tokenResponse;
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, false, false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void accessTokenTrueIntrospectionFalseTest() throws IOException {
        ProtocolMappersResource protocolMappers = setProtocolMappers(true, false, true);
        try {
            oauth.nonce("123456");
            oauth.scope("address");
            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = browserLogin(TEST_CLIENT_SECRET, TEST_USER_NAME, TEST_USER_PASSWORD).tokenResponse;
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            // Most of the claims should not be included in introspectionResponse as introspectionMapper was disabled
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), true, false, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void accessTokenTrueIntrospectionTrueTest() throws IOException {
        ProtocolMappersResource protocolMappers = setProtocolMappers(true, true, true);
        try {
            oauth.nonce("123456");
            oauth.scope("address");
            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = browserLogin(TEST_CLIENT_SECRET, TEST_USER_NAME, TEST_USER_PASSWORD).tokenResponse;
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void offlineTokenTest() throws IOException {
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, true);
        try {
            oauth.nonce("123456");
            oauth.scope("openid address offline_access");

            oauth.clientId(TEST_CLIENT);
            TokenResponseContext ctx = browserLogin(TEST_CLIENT_SECRET, TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse response = ctx.tokenResponse;
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            logger.debug("idtoken:" + response.getIdToken());
            assertAccessToken(oauth.verifyToken(accessToken), true, false, false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            removeSession(ctx.userSessionId);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void clientCredentialTest() throws Exception {
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, false);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = oauth.doClientCredentialsGrantAccessTokenRequest(TEST_CLIENT_SECRET);
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), false, false, false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), false, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void exchangeTest() throws Exception {
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, true);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = browserLogin(TEST_CLIENT_SECRET, TEST_USER_NAME, TEST_USER_PASSWORD).tokenResponse;
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, false, false);
            response = oauth.doTokenExchange(TEST, accessToken, null, TEST_CLIENT, TEST_CLIENT_SECRET);
            String exchangedTokenString = response.getAccessToken();
            logger.debug("exchangedTokenString:" + exchangedTokenString);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, exchangedTokenString);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void testPolicyLightWeightFalseTest() throws Exception {
        setUseLightweightAccessTokenExecutor();
        ProtocolMappersResource protocolMappers = setProtocolMappers(true, true, false, false);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);

            OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            String accessToken = tokenResponse.getAccessToken();
            assertAccessToken(oauth.verifyToken(accessToken), true, false, true);
            logger.debug("lightweight access token:" + accessToken);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, false);
            logger.debug("tokenResponse:" + introspectResponse);

            oauth.clientId(TEST_CLIENT);
            deletePolicy(POLICY_NAME);
            oauth.doLogout(tokenResponse.getRefreshToken(), TEST_CLIENT_SECRET);

            authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void testPolicyLightWeightTrueTest() throws Exception {
        setUseLightweightAccessTokenExecutor();
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, true, false);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);

            OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            String accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, true);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + introspectResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, false);

        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void testAlwaysUseLightWeightFalseTest() throws Exception {
        alwaysUseLightWeightAccessToken(true);
        ProtocolMappersResource protocolMappers = setProtocolMappers(true, true, false, false);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);

            OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            String accessToken = tokenResponse.getAccessToken();
            assertAccessToken(oauth.verifyToken(accessToken), true, false, true);
            logger.debug("lightweight access token:" + accessToken);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, false);
            logger.debug("tokenResponse:" + introspectResponse);

            oauth.clientId(TEST_CLIENT);
            alwaysUseLightWeightAccessToken(false);
            oauth.doLogout(tokenResponse.getRefreshToken(), TEST_CLIENT_SECRET);

            authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, false);
        } finally {
            deleteProtocolMappers(protocolMappers);
        }
    }

    @Test
    public void testAlwaysUseLightWeightTrueTest() throws Exception {
        alwaysUseLightWeightAccessToken(true);
        ProtocolMappersResource protocolMappers = setProtocolMappers(false, true, true, false);
        try {
            oauth.nonce("123456");
            oauth.scope("address");

            oauth.clientId(TEST_CLIENT);

            OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            String accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true, true);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + introspectResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, false);

        } finally {
            deleteProtocolMappers(protocolMappers);
            alwaysUseLightWeightAccessToken(false);
        }
    }

    @Test
    public void testWithoutBasicClaim() throws Exception {
        alwaysUseLightWeightAccessToken(true);
        removeDefaultBasicClientScope();
        ProtocolMappersResource protocolMappers = setProtocolMappers(true, true, false, false);
        try {
            oauth.clientId(TEST_CLIENT);
            oauth.scope("address");

            OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            String accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true,  false,true);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + introspectResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, true);

            oauth.clientId(TEST_CLIENT);
            alwaysUseLightWeightAccessToken(false);
            oauth.doLogout(tokenResponse.getRefreshToken(), TEST_CLIENT_SECRET);


            authsEndpointResponse = oauth.doLogin(TEST_USER_NAME, TEST_USER_PASSWORD);
            tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), TEST_CLIENT_SECRET);
            accessToken = tokenResponse.getAccessToken();
            logger.debug("access token:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), true, true,  true);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            introspectResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + introspectResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(introspectResponse, AccessToken.class), true, true, true);

        } finally {
            deleteProtocolMappers(protocolMappers);
            addDefaultBasicClientScope();
        }
    }

    @Test
    public void clientCredentialWithoutBasicClaims() throws Exception {
        removeDefaultBasicClientScope();
        alwaysUseLightWeightAccessToken(true);
        try {
            oauth.nonce("123456");

            oauth.clientId(TEST_CLIENT);
            OAuthClient.AccessTokenResponse response = oauth.doClientCredentialsGrantAccessTokenRequest(TEST_CLIENT_SECRET);
            String accessToken = response.getAccessToken();
            logger.debug("accessToken:" + accessToken);
            assertAccessToken(oauth.verifyToken(accessToken), false,  false,false);

            oauth.clientId(RESOURCE_SERVER_CLIENT_ID);
            String tokenResponse = oauth.introspectAccessTokenWithClientCredential(RESOURCE_SERVER_CLIENT_ID, RESOURCE_SERVER_CLIENT_PASSWORD, accessToken);
            logger.debug("tokenResponse:" + tokenResponse);
            assertTokenIntrospectionResponse(JsonSerialization.readValue(tokenResponse, AccessToken.class), false, true, false);
        } finally {
            addDefaultBasicClientScope();
            alwaysUseLightWeightAccessToken(false);
        }
    }

    private void removeSession(final String sessionId) {
        testingClient.testing().removeExpired(REALM_NAME);
        try {
            testingClient.testing().removeUserSession(REALM_NAME, sessionId);
        } catch (NotFoundException nfe) {
            // Ignore
        }
    }

    private void assertMapperClaims(AccessToken token, boolean isAddMapperResponseFlag, boolean isAuthCodeFlow) {
        if (isAddMapperResponseFlag) {
            if (isAuthCodeFlow) {
                Assert.assertNotNull(token.getName());
                Assert.assertNotNull(token.getGivenName());
                Assert.assertNotNull(token.getFamilyName());
                Assert.assertNotNull(token.getAddress());
                Assert.assertNotNull(token.getEmail());
                Assert.assertNotNull(token.getOtherClaims().get("user-session-note"));
                Assert.assertNotNull(token.getOtherClaims().get("test-claim"));
                Assert.assertNotNull(token.getOtherClaims().get("group-name"));
                Assert.assertNotNull(token.getOtherClaims().get(IDToken.SESSION_STATE));
            }
            Assert.assertNotNull(token.getAudience());
            Assert.assertNotNull(token.getAcr());
            Assert.assertNotNull(token.getAllowedOrigins());
            Assert.assertNotNull(token.getRealmAccess());
            Assert.assertNotNull(token.getResourceAccess());
            Assert.assertNotNull(token.getEmailVerified());
            Assert.assertNotNull(token.getPreferredUsername());
        } else {
            if (isAuthCodeFlow) {
                Assert.assertNull(token.getName());
                Assert.assertNull(token.getGivenName());
                Assert.assertNull(token.getFamilyName());
                Assert.assertNull(token.getAddress());
                Assert.assertNull(token.getEmail());
                Assert.assertNull(token.getOtherClaims().get("user-session-note"));
                Assert.assertNull(token.getOtherClaims().get("test-claim"));
                Assert.assertNull(token.getOtherClaims().get("group-name"));
                Assert.assertNull(token.getOtherClaims().get(IDToken.SESSION_STATE));
            }
            Assert.assertNull(token.getAcr());
            Assert.assertNull(token.getAllowedOrigins());
            Assert.assertNull(token.getRealmAccess());
            Assert.assertTrue(token.getResourceAccess().isEmpty());
            Assert.assertNull(token.getEmailVerified());
            Assert.assertNull(token.getPreferredUsername());
        }
    }

    private void assertInitClaims(AccessToken token, boolean isAuthCodeFlow) {
        Assert.assertNotNull(token.getExp());
        Assert.assertNotNull(token.getIat());
        Assert.assertNotNull(token.getId());
        Assert.assertNotNull(token.getType());
        Assert.assertNotNull(token.getIssuedFor());
        Assert.assertNotNull(token.getScope());
        Assert.assertNotNull(token.getIssuer());
        if (isAuthCodeFlow) {
            Assert.assertNotNull(token.getSessionId());
        } else {
            Assert.assertNull(token.getSessionId());
        }
    }

    private void assertBasicClaims(AccessToken token, boolean isAuthCodeFlow, boolean missing) {
        if (missing) {
            Assert.assertNull(token.getAuth_time());
            Assert.assertNull(token.getSubject());
        } else {
            Assert.assertNotNull(token.getSubject());
            if (isAuthCodeFlow) {
                Assert.assertNotNull(token.getAuth_time());
            } else {
                Assert.assertNull(token.getAuth_time());
            }
        }
    }

    private void assertIntrospectClaims(AccessToken token) {
        Assert.assertNotNull(token.getOtherClaims().get("client_id"));
        Assert.assertNotNull(token.getOtherClaims().get("active"));
        Assert.assertNotNull(token.getOtherClaims().get("token_type"));
    }

    private void assertAccessToken(AccessToken token, boolean isAuthCodeFlow, boolean isAddToAccessToken, boolean missingBasicClaims) {
        Assert.assertNull(token.getNonce());
        assertMapperClaims(token, isAddToAccessToken, isAuthCodeFlow);
        assertInitClaims(token, isAuthCodeFlow);
        assertBasicClaims(token, isAuthCodeFlow, missingBasicClaims);
    }

    private void assertTokenIntrospectionResponse(AccessToken token, boolean isAuthCodeFlow, boolean isAddToIntrospect, boolean missingBasicClaims) {
        Assert.assertNull(token.getNonce());
        assertMapperClaims(token, isAddToIntrospect, isAuthCodeFlow);
        assertInitClaims(token, isAuthCodeFlow);
        assertIntrospectClaims(token);
        assertBasicClaims(token, isAuthCodeFlow, missingBasicClaims);
    }

    protected RealmResource testRealm() {
        return adminClient.realm(REALM_NAME);
    }

    public void addDefaultBasicClientScope() {
        testRealm().getDefaultDefaultClientScopes()
                .stream()
                .filter(scope-> scope.getName().equals(OIDCLoginProtocolFactory.BASIC_SCOPE))
                .findFirst()
                .ifPresent(scope-> {
                    ApiUtil.findClientResourceByClientId(adminClient.realm(REALM_NAME), TEST_CLIENT).addDefaultClientScope(scope.getId());
                });
    }

    public void removeDefaultBasicClientScope() {
        testRealm().getDefaultDefaultClientScopes()
                .stream()
                .filter(scope-> scope.getName().equals(OIDCLoginProtocolFactory.BASIC_SCOPE))
                .findFirst()
                .ifPresent(scope-> {
                    ApiUtil.findClientResourceByClientId(adminClient.realm(REALM_NAME), TEST_CLIENT).removeDefaultClientScope(scope.getId());
                });
    }

    private void setScopeProtocolMappers(boolean isIncludeAccessToken, boolean isIncludeIntrospection, boolean isIncludeLightweightAccessToken) {
        setScopeProtocolMapper(ACR_SCOPE, ACR, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(PROFILE_CLAIM, FULL_NAME, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(EMAIL, EMAIL, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(EMAIL, EMAIL_VERIFIED, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(PROFILE_CLAIM, GIVEN_NAME, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(PROFILE_CLAIM, FAMILY_NAME, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(PROFILE_CLAIM, USERNAME, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(WEB_ORIGINS_SCOPE, ALLOWED_WEB_ORIGINS, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(ROLES_SCOPE, REALM_ROLES, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(ROLES_SCOPE, CLIENT_ROLES, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(ROLES_SCOPE, AUDIENCE_RESOLVE, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        setScopeProtocolMapper(ADDRESS, ADDRESS, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
    }

    private void setScopeProtocolMapper(String scopeName, String mapperName, boolean isIncludeAccessToken, boolean isIncludeIntrospection, boolean isIncludeLightweightAccessToken) {
        ClientScopeResource scope = ApiUtil.findClientScopeByName(testRealm(), scopeName);
        ProtocolMapperRepresentation protocolMapper = ApiUtil.findProtocolMapperByName(scope, mapperName);
        Map<String, String> config = protocolMapper.getConfig();
        if (isIncludeAccessToken) {
            config.put(INCLUDE_IN_ACCESS_TOKEN, "true");
        } else {
            config.put(INCLUDE_IN_ACCESS_TOKEN, "false");
        }
        if (isIncludeIntrospection) {
            config.put(INCLUDE_IN_INTROSPECTION, "true");
        } else {
            config.put(INCLUDE_IN_INTROSPECTION, "false");
        }
        if (isIncludeLightweightAccessToken) {
            config.put(INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN, "true");
        } else {
            config.put(INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN, "false");
        }
        scope.getProtocolMappers().update(protocolMapper.getId(), protocolMapper);
    }

    private ProtocolMappersResource setProtocolMappers(boolean isIncludeAccessToken, boolean isIncludeIntrospection, boolean setPairWise) {
        setScopeProtocolMappers(isIncludeAccessToken, isIncludeIntrospection, false);
        List<ProtocolMapperRepresentation> protocolMapperList = new ArrayList<>();
        setExistingProtocolMappers(protocolMapperList, isIncludeAccessToken, isIncludeIntrospection, false, setPairWise);
        ProtocolMappersResource protocolMappers = ApiUtil.findClientResourceByClientId(adminClient.realm(REALM_NAME), TEST_CLIENT).getProtocolMappers();
        protocolMappers.createMapper(protocolMapperList);
        return protocolMappers;
    }

    private ProtocolMappersResource setProtocolMappers(boolean isIncludeAccessToken, boolean isIncludeIntrospection, boolean isIncludeLightweightAccessToken, boolean setPairWise) {
        setScopeProtocolMappers(isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken);
        List<ProtocolMapperRepresentation> protocolMapperList = new ArrayList<>();
        setExistingProtocolMappers(protocolMapperList, isIncludeAccessToken, isIncludeIntrospection, isIncludeLightweightAccessToken, setPairWise);
        ProtocolMappersResource protocolMappers = ApiUtil.findClientResourceByClientId(adminClient.realm(REALM_NAME), TEST_CLIENT).getProtocolMappers();
        protocolMappers.createMapper(protocolMapperList);
        return protocolMappers;
    }

    private void setExistingProtocolMappers(List<ProtocolMapperRepresentation> protocolMapperList, boolean isIncludeAccessToken, boolean isIncludeIntrospection, boolean isIncludeLightweightAccessToken, boolean setPairWise) {
        Map<String, String> config = new HashMap<>();
        if (isIncludeAccessToken) {
            config.put(INCLUDE_IN_ACCESS_TOKEN, "true");
        } else {
            config.put(INCLUDE_IN_ACCESS_TOKEN, "false");
        }
        if (isIncludeIntrospection) {
            config.put(INCLUDE_IN_INTROSPECTION, "true");
        } else {
            config.put(INCLUDE_IN_INTROSPECTION, "false");
        }
        if (isIncludeLightweightAccessToken) {
            config.put(INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN, "true");
        } else {
            config.put(INCLUDE_IN_LIGHTWEIGHT_ACCESS_TOKEN, "false");
        }

        ProtocolMapperRepresentation audienceProtocolMapper = createClaimMapper("audience", AudienceProtocolMapper.PROVIDER_ID, new HashMap<>(config) {{
            put(INCLUDED_CLIENT_AUDIENCE, "account-console");
        }});
        protocolMapperList.add(audienceProtocolMapper);
        ProtocolMapperRepresentation roleNameMapper = createClaimMapper("role-name", RoleNameMapper.PROVIDER_ID, new HashMap<>(config) {{
            put(ROLE_CONFIG, "user");
            put(NEW_ROLE_NAME, "new-role");
        }});
        protocolMapperList.add(roleNameMapper);
        ProtocolMapperRepresentation groupMembershipMapper = createClaimMapper("group-member", GroupMembershipMapper.PROVIDER_ID, new HashMap<>(config) {{
            put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "group-name");
        }});
        protocolMapperList.add(groupMembershipMapper);
        ProtocolMapperRepresentation hardcodedClaim = createClaimMapper("hardcoded-claim", HardcodedClaim.PROVIDER_ID, new HashMap<>(config) {{
            put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "test-claim");
            put(CLAIM_VALUE, "test-value");
        }});
        protocolMapperList.add(hardcodedClaim);
        ProtocolMapperRepresentation hardcodedRole = createClaimMapper("hardcoded-role", HardcodedRole.PROVIDER_ID, new HashMap<>(config) {{
            put(ROLE_CONFIG, "hardcoded-role");
        }});
        protocolMapperList.add(hardcodedRole);
        ProtocolMapperRepresentation userSessionNoteMapper = createClaimMapper("user-session-note", UserSessionNoteMapper.PROVIDER_ID, new HashMap<>(config) {{
            put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "user-session-note");
            put(USER_SESSION_NOTE, "AUTH_TIME");
        }});
        protocolMapperList.add(userSessionNoteMapper);
        if (setPairWise) {
            ProtocolMapperRepresentation pairwiseSubMapper = createClaimMapper("pairwise-sub-mapper", "oidc-" + SHA256PairwiseSubMapper.PROVIDER_ID + PROVIDER_ID_SUFFIX, new HashMap<>(config) {{
                put(PAIRWISE_SUB_ALGORITHM_SALT, "abc");
            }});
            protocolMapperList.add(pairwiseSubMapper);
        }
        ProtocolMapperRepresentation sessionStateMapper = createClaimMapper("session-state-mapper", SessionStateMapper.PROVIDER_ID, config);
        protocolMapperList.add(sessionStateMapper);
    }

    private static ProtocolMapperRepresentation createClaimMapper(String name, String providerId, Map<String, String> config) {
        ProtocolMapperModel mapper = new ProtocolMapperModel();
        mapper.setName(name);
        mapper.setProtocolMapper(providerId);
        mapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        mapper.setConfig(config);
        return ModelToRepresentation.toRepresentation(mapper);
    }

    private void deleteProtocolMappers(ProtocolMappersResource protocolMappers) {
        List<String> mapperNames = new ArrayList<>(Arrays.asList("reference", "audience", "role-name", "group-member", "hardcoded-claim", "hardcoded-role", "user-session-note", "pairwise-sub-mapper", "session-state-mapper"));
        List<ProtocolMapperRepresentation> mappers = new ArrayList<>();
        for (String mapperName : mapperNames) {
            mappers.add(ProtocolMapperUtil.getMapperByNameAndProtocol(protocolMappers, OIDCLoginProtocol.LOGIN_PROTOCOL, mapperName));
        }

        for (ProtocolMapperRepresentation mapper : mappers) {
            if (mapper != null) {
                protocolMappers.delete(mapper.getId());
            }
        }
    }

    private TokenResponseContext browserLogin(String clientSecret, String username, String password) {
        OAuthClient.AuthorizationEndpointResponse authsEndpointResponse = oauth.doLogin(username, password);
        String userSessionId = authsEndpointResponse.getSessionState();
        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(authsEndpointResponse.getCode(), clientSecret);
        return new TokenResponseContext(userSessionId, tokenResponse);
    }

    private class TokenResponseContext {

        private final String userSessionId;
        private final OAuthClient.AccessTokenResponse tokenResponse;

        public TokenResponseContext(String userSessionId, OAuthClient.AccessTokenResponse tokenResponse) {
            this.userSessionId = userSessionId;
            this.tokenResponse = tokenResponse;
        }
    }

    private void setUseLightweightAccessTokenExecutor() throws Exception {
        // register profiles
        String json = (new ClientPoliciesUtil.ClientProfilesBuilder()).addProfile(
                (new ClientPoliciesUtil.ClientProfileBuilder()).createProfile(PROFILE_NAME, "Use Lightweight Access Token")
                        .addExecutor(UseLightweightAccessTokenExecutorFactory.PROVIDER_ID, null)
                        .toRepresentation()
        ).toString();
        updateProfiles(json);

        // register policies
        json = (new ClientPoliciesUtil.ClientPoliciesBuilder()).addPolicy(
                (new ClientPoliciesUtil.ClientPolicyBuilder()).createPolicy(POLICY_NAME, "Use Lightweight Access Token Policy", Boolean.TRUE)
                        .addCondition(AnyClientConditionFactory.PROVIDER_ID,
                                createAnyClientConditionConfig())
                        .addProfile(PROFILE_NAME)
                        .toRepresentation()
        ).toString();
        updatePolicies(json);
    }

    private void alwaysUseLightWeightAccessToken(boolean enable){
        ClientManager.realm(adminClient.realm(REALM_NAME)).clientId(TEST_CLIENT).alwaysUseLightweightAccessToken(enable);
    }
}
