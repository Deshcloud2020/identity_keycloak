/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.oauth;

import org.hamcrest.MatcherAssert;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.common.util.Retry;
import org.keycloak.common.util.Time;
import org.keycloak.events.Details;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.models.Constants;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.LogoutToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.pages.LoginPage;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.keycloak.testsuite.updaters.RealmAttributeUpdater;
import org.keycloak.testsuite.util.ClientManager;
import org.keycloak.testsuite.util.Matchers;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.TokenSignatureUtil;
import org.keycloak.testsuite.util.WaitUtils;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.keycloak.testsuite.admin.AbstractAdminTest.loadJson;

/**
 * Tests mostly for backchannel logout scenarios with refresh token (Legacy Logout endpoint not compliant with OIDC specification) and admin logout scenarios
 *
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class LogoutTest extends AbstractKeycloakTest {

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Page
    protected LoginPage loginPage;

    @Override
    public void beforeAbstractKeycloakTest() throws Exception {
        super.beforeAbstractKeycloakTest();
    }

    @Before
    public void clientConfiguration() {
        ClientManager.realm(adminClient.realm("test")).clientId("test-app").directAccessGrant(true);
        new RealmAttributeUpdater(adminClient.realm("test")).setNotBefore(0).update();
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realmRepresentation = loadJson(getClass().getResourceAsStream("/testrealm.json"), RealmRepresentation.class);
        RealmBuilder realm = RealmBuilder.edit(realmRepresentation).testEventListener();

        testRealms.add(realm.build());
    }

    @Test
    public void postLogout() throws Exception {
        oauth.doLogin("test-user@localhost", "password");

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

        oauth.clientSessionState("client-session");
        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
        String refreshTokenString = tokenResponse.getRefreshToken();

        try (CloseableHttpResponse response = oauth.doLogout(refreshTokenString, "password")) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.NO_CONTENT));

            assertNotNull(testingClient.testApp().getAdminLogoutAction());
        }
    }

    @Test
    public void postLogoutExpiredRefreshToken() throws Exception {
        oauth.doLogin("test-user@localhost", "password");

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

        oauth.clientSessionState("client-session");
        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
        String refreshTokenString = tokenResponse.getRefreshToken();

        adminClient.realm("test").update(RealmBuilder.create().notBefore(Time.currentTime() + 1).build());

        // Logout should succeed with expired refresh token, see KEYCLOAK-3302
        try (CloseableHttpResponse response = oauth.doLogout(refreshTokenString, "password")) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.NO_CONTENT));

            assertNotNull(testingClient.testApp().getAdminLogoutAction());
        }
    }

    @Test
    public void postLogoutWithRefreshTokenAfterUserSessionLogoutAndLoginAgain() throws Exception {
        // Login
        OAuthClient.AccessTokenResponse accessTokenResponse = loginAndForceNewLoginPage();
        String refreshToken1 = accessTokenResponse.getRefreshToken();

        oauth.doLogout(refreshToken1, "password");

        setTimeOffset(2);

        WaitUtils.waitForPageToLoad();
        loginPage.login("password");

        Assert.assertFalse(loginPage.isCurrent());

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse tokenResponse2 = oauth.doAccessTokenRequest(code, "password");

        // POST logout with token should fail
        try (CloseableHttpResponse response = oauth.doLogout(refreshToken1, "password")) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatusLine().getStatusCode());
        }

        String logoutUrl = oauth.getLogoutUrl()
                .idTokenHint(accessTokenResponse.getIdToken())
                .postLogoutRedirectUri(oauth.APP_AUTH_ROOT)
                .build();

        // GET logout with ID token should fail as well
        try (CloseableHttpClient c = HttpClientBuilder.create().disableRedirectHandling().build();
             CloseableHttpResponse response = c.execute(new HttpGet(logoutUrl))) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatusLine().getStatusCode());
        }

        // finally POST logout with VALID token should succeed
        try (CloseableHttpResponse response = oauth.doLogout(tokenResponse2.getRefreshToken(), "password")) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.NO_CONTENT));

            assertNotNull(testingClient.testApp().getAdminLogoutAction());
        }
    }


    @Test
    public void postLogoutFailWithCredentialsOfDifferentClient() throws Exception {
        oauth.doLogin("test-user@localhost", "password");

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

        oauth.clientSessionState("client-session");
        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
        String refreshTokenString = tokenResponse.getRefreshToken();

        oauth.clientId("test-app-scope");

        // Assert logout fails with 400 when trying to use different client credentials
        try (CloseableHttpResponse response = oauth.doLogout(refreshTokenString, "password")) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.BAD_REQUEST));
        }

        oauth.clientId("test-app");
    }

    @Test
    public void logoutUserByAdmin() {
        loginPage.open();
        loginPage.login("test-user@localhost", "password");
        String sessionId = events.expectLogin().assertEvent().getSessionId();

        UserRepresentation user = ApiUtil.findUserByUsername(adminClient.realm("test"), "test-user@localhost");
        Assert.assertEquals((Object) 0, user.getNotBefore());

        adminClient.realm("test").users().get(user.getId()).logout();

        Retry.execute(() -> {
            UserRepresentation u = adminClient.realm("test").users().get(user.getId()).toRepresentation();
            Assert.assertTrue(u.getNotBefore() > 0);

            loginPage.open();
            loginPage.assertCurrent();
        }, 10, 200);
    }

    private void backchannelLogoutRequest(String expectedRefreshAlg, String expectedAccessAlg, String expectedIdTokenAlg) throws Exception {
        oauth.doLogin("test-user@localhost", "password");

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

        oauth.clientSessionState("client-session");
        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
        String idTokenString = tokenResponse.getIdToken();

        JWSHeader header = new JWSInput(tokenResponse.getAccessToken()).getHeader();
        assertEquals(expectedAccessAlg, header.getAlgorithm().name());
        assertEquals("JWT", header.getType());
        assertNull(header.getContentType());

        header = new JWSInput(tokenResponse.getIdToken()).getHeader();
        assertEquals(expectedIdTokenAlg, header.getAlgorithm().name());
        assertEquals("JWT", header.getType());
        assertNull(header.getContentType());

        header = new JWSInput(tokenResponse.getRefreshToken()).getHeader();
        assertEquals(expectedRefreshAlg, header.getAlgorithm().name());
        assertEquals("JWT", header.getType());
        assertNull(header.getContentType());

        String logoutUrl = oauth.getLogoutUrl()
          .idTokenHint(idTokenString)
          .postLogoutRedirectUri(oauth.APP_AUTH_ROOT)
          .build();
        
        try (CloseableHttpClient c = HttpClientBuilder.create().disableRedirectHandling().build();
          CloseableHttpResponse response = c.execute(new HttpGet(logoutUrl))) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.FOUND));
            MatcherAssert.assertThat(response.getFirstHeader(HttpHeaders.LOCATION).getValue(), is(oauth.APP_AUTH_ROOT));
        }
    }

    @Test
    public void backchannelLogoutRequest_RealmRS384_ClientRS512() throws Exception {
        try {
            TokenSignatureUtil.changeRealmTokenSignatureProvider(adminClient, "RS384");
            TokenSignatureUtil.changeClientAccessTokenSignatureProvider(ApiUtil.findClientByClientId(adminClient.realm("test"), "test-app"), "RS512");
            backchannelLogoutRequest(Constants.INTERNAL_SIGNATURE_ALGORITHM, "RS512", "RS384");
        } finally {
            TokenSignatureUtil.changeRealmTokenSignatureProvider(adminClient, "RS256");
            TokenSignatureUtil.changeClientAccessTokenSignatureProvider(ApiUtil.findClientByClientId(adminClient.realm("test"), "test-app"), "RS256");
        }
    }

    @Test
    public void successfulKLogoutAfterEmptyBackChannelUrl() throws Exception {
        ClientsResource clients = adminClient.realm(oauth.getRealm()).clients();
        ClientRepresentation rep = clients.findByClientId(oauth.getClientId()).get(0);

        rep.getAttributes().put(OIDCConfigAttributes.BACKCHANNEL_LOGOUT_URL, "");

        clients.get(rep.getId()).update(rep);

        oauth.doLogin("test-user@localhost", "password");
        String sessionId = events.expectLogin().assertEvent().getSessionId();

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

        oauth.clientSessionState("client-session");

        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
        events.poll();
        String idTokenString = tokenResponse.getIdToken();
        String logoutUrl = oauth.getLogoutUrl()
                .idTokenHint(idTokenString)
                .postLogoutRedirectUri(oauth.APP_AUTH_ROOT)
                .build();

        try (CloseableHttpClient c = HttpClientBuilder.create().disableRedirectHandling().build();
                CloseableHttpResponse response = c.execute(new HttpGet(logoutUrl))) {
            MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.FOUND));
            MatcherAssert.assertThat(response.getFirstHeader(HttpHeaders.LOCATION).getValue(), is(oauth.APP_AUTH_ROOT));
        }

        // Assert logout event triggered for backchannel logout
        events.expectLogout(sessionId)
                .client(AssertEvents.DEFAULT_CLIENT_ID)
                .detail(Details.REDIRECT_URI, oauth.APP_AUTH_ROOT)
                .assertEvent();

        assertNotNull(testingClient.testApp().getAdminLogoutAction());
    }

    @Test
    public void backChannelPreferenceOverKLogout() throws Exception {
        ClientsResource clients = adminClient.realm(oauth.getRealm()).clients();
        ClientRepresentation rep = clients.findByClientId(oauth.getClientId()).get(0);

        rep.getAttributes().put(OIDCConfigAttributes.BACKCHANNEL_LOGOUT_URL, oauth.APP_ROOT + "/admin/backchannelLogout");

        ClientResource clientResource = clients.get(rep.getId());
        clientResource.update(rep);

        try {
            oauth.doLogin("test-user@localhost", "password");

            String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);

            oauth.clientSessionState("client-session");

            OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");
            String idTokenString = tokenResponse.getIdToken();
            String logoutUrl = oauth.getLogoutUrl()
                    .idTokenHint(idTokenString)
                    .postLogoutRedirectUri(oauth.APP_AUTH_ROOT)
                    .build();

            try (CloseableHttpClient c = HttpClientBuilder.create().disableRedirectHandling().build();
                    CloseableHttpResponse response = c.execute(new HttpGet(logoutUrl))) {
                MatcherAssert.assertThat(response, Matchers.statusCodeIsHC(Status.FOUND));
                MatcherAssert.assertThat(response.getFirstHeader(HttpHeaders.LOCATION).getValue(), is(oauth.APP_AUTH_ROOT));
            }

            String rawLogoutToken = testingClient.testApp().getBackChannelRawLogoutToken();
            JWSInput jwsInput = new JWSInput(rawLogoutToken);
            LogoutToken logoutToken = jwsInput.readJsonContent(LogoutToken.class);
            validateLogoutToken(logoutToken);
            JWSHeader logoutTokenHeader = jwsInput.getHeader();
            assertEquals("logout+jwt", logoutTokenHeader.getType());
        } finally {
            rep.getAttributes().put(OIDCConfigAttributes.BACKCHANNEL_LOGOUT_URL, "");
            clientResource.update(rep);
        }
    }

    /**
     * Validate the token matches the spec at <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html#LogoutToken">OpenID Connect Back-Channel Logout 1.0 incorporating errata set 1</a>
     */
    private void validateLogoutToken(LogoutToken backChannelLogoutToken) {
        assertNotNull("token must be present", backChannelLogoutToken);
        assertNotNull("iss must be present", backChannelLogoutToken.getIssuer());
        assertNotNull("aud must be present", backChannelLogoutToken.getAudience());
        assertNotNull("iat must be present", backChannelLogoutToken.getIat());
        assertNotNull("exp must be present", backChannelLogoutToken.getExp());
        assertNotNull("jti must be present", backChannelLogoutToken.getId());
        Map<String, Object> events = backChannelLogoutToken.getEvents();
        assertNotNull("events must be present", events);
        Object backchannelLogoutEvent = events.get("http://schemas.openid.net/event/backchannel-logout");
        assertNotNull("back-channel logout event must be present", backchannelLogoutEvent);
        assertTrue("back-channel logout event must have a member object", backchannelLogoutEvent instanceof Map);
        MatcherAssert.assertThat("map of back-channel logout event member object should be an empty object", (Map<?, ?>) backchannelLogoutEvent, org.hamcrest.Matchers.anEmptyMap());
    }

    private OAuthClient.AccessTokenResponse loginAndForceNewLoginPage() {
        oauth.doLogin("test-user@localhost", "password");

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        oauth.clientSessionState("client-session");

        OAuthClient.AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(code, "password");

        setTimeOffset(1);

        String loginFormUri = UriBuilder.fromUri(oauth.getLoginFormUrl())
                .queryParam(OIDCLoginProtocol.PROMPT_PARAM, OIDCLoginProtocol.PROMPT_VALUE_LOGIN)
                .build().toString();
        driver.navigate().to(loginFormUri);

        loginPage.assertCurrent();

        return tokenResponse;
    }
}
