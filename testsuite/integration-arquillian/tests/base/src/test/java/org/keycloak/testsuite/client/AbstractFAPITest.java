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
 *
 */
package org.keycloak.testsuite.client;

import static org.junit.Assert.assertEquals;
import static org.keycloak.testsuite.admin.AbstractAdminTest.loadJson;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.Constants;
import org.keycloak.representations.AuthorizationResponseToken;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.client.policies.AbstractClientPoliciesTest;
import org.keycloak.testsuite.client.resources.TestOIDCEndpointsApplicationResource;
import org.keycloak.testsuite.pages.AppPage;
import org.keycloak.testsuite.pages.ErrorPage;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.pages.OAuthGrantPage;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.ServerURLs;

public abstract class AbstractFAPITest extends AbstractClientPoliciesTest {

    protected final String TEST_USERNAME = "john";
    protected final String TEST_USERSECRET = "password";

    @Page
    protected ErrorPage errorPage;

    @Page
    protected LoginPage loginPage;

    @Page
    protected OAuthGrantPage grantPage;

    @Page
    protected AppPage appPage;

    @BeforeClass
    public static void verifySSL() {
        // FAPI requires SSL and does not makes sense to test it with disabled SSL
        Assume.assumeTrue("The FAPI test requires SSL to be enabled.", ServerURLs.AUTH_SERVER_SSL_REQUIRED);
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realm = loadJson(getClass().getResourceAsStream("/testrealm.json"), RealmRepresentation.class);

        List<UserRepresentation> users = realm.getUsers();

        LinkedList<CredentialRepresentation> credentials = new LinkedList<>();
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(TEST_USERSECRET);
        credentials.add(password);

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(TEST_USERNAME);
        user.setEmail("john@keycloak.org");
        user.setFirstName("Johny");
        user.setCredentials(credentials);
        user.setClientRoles(Collections.singletonMap(Constants.REALM_MANAGEMENT_CLIENT_ID, Arrays.asList(AdminRoles.CREATE_CLIENT, AdminRoles.MANAGE_CLIENTS)));
        users.add(user);

        realm.setUsers(users);

        testRealms.add(realm);
    }


    public static void assertScopes(String expectedScope, String receivedScope) {
        Collection<String> expectedScopes = Arrays.asList(expectedScope.split(" "));
        Collection<String> receivedScopes = Arrays.asList(receivedScope.split(" "));
        Assert.assertTrue("Not matched. expectedScope: " + expectedScope + ", receivedScope: " + receivedScope,
                expectedScopes.containsAll(receivedScopes) && receivedScopes.containsAll(expectedScopes));
    }

    protected String getParameterFromUrl(String paramName, boolean fragmentExpected) {
        return fragmentExpected ? oauth.getCurrentFragment().get(paramName) : oauth.getCurrentQuery().get(paramName);
    }

    protected String loginUserAndGetCode(String clientId, boolean fragmentResponseModeExpected) {
        oauth.clientId(clientId);
        oauth.doLogin(TEST_USERNAME, TEST_USERSECRET);

        grantPage.assertCurrent();
        grantPage.assertGrants(OAuthGrantPage.PROFILE_CONSENT_TEXT, OAuthGrantPage.EMAIL_CONSENT_TEXT, OAuthGrantPage.ROLES_CONSENT_TEXT);
        grantPage.accept();

        String code = getParameterFromUrl(OAuth2Constants.CODE, fragmentResponseModeExpected);
        Assert.assertNotNull(code);
        return code;
    }

    protected String loginUserAndGetCodeInJwtQueryResponseMode(String clientId) {
        oauth.clientId(clientId);
        oauth.doLogin(TEST_USERNAME, TEST_USERSECRET);

        grantPage.assertCurrent();
        grantPage.assertGrants(OAuthGrantPage.PROFILE_CONSENT_TEXT, OAuthGrantPage.EMAIL_CONSENT_TEXT, OAuthGrantPage.ROLES_CONSENT_TEXT);
        grantPage.accept();

        System.out.println("KKKKK response = " + oauth.getCurrentQuery().get("response"));
        AuthorizationResponseToken responseToken = oauth.verifyAuthorizationResponseToken(oauth.getCurrentQuery().get("response"));
        String code = (String)responseToken.getOtherClaims().get("code");
        Assert.assertNotNull(code);
        return code;
    }

    protected void assertSuccessfulTokenResponse(OAuthClient.AccessTokenResponse tokenResponse) {
        assertEquals(200, tokenResponse.getStatusCode());
        MatcherAssert.assertThat(tokenResponse.getIdToken(), Matchers.notNullValue());
        MatcherAssert.assertThat(tokenResponse.getAccessToken(), Matchers.notNullValue());

        // Scope parameter must be present per FAPI
        Assert.assertNotNull(tokenResponse.getScope());
        assertScopes("openid profile email", tokenResponse.getScope());

        // ID Token contains all the claims
        IDToken idToken = oauth.verifyIDToken(tokenResponse.getIdToken());
        Assert.assertNotNull(idToken.getId());
        Assert.assertEquals("foo", idToken.getIssuedFor());
        Assert.assertEquals("john", idToken.getPreferredUsername());
        Assert.assertEquals("john@keycloak.org", idToken.getEmail());
        Assert.assertEquals("Johny", idToken.getGivenName());
        Assert.assertEquals(idToken.getNonce(), "123456");
    }

    protected void logoutUserAndRevokeConsent(String clientId, String username) {
        UserResource user = ApiUtil.findUserByUsernameId(adminClient.realm(REALM_NAME), username);
        user.logout();
        List<Map<String, Object>> consents = user.getConsents();
        org.junit.Assert.assertEquals(1, consents.size());
        user.revokeConsent(clientId);
    }

    protected void assertRedirectedToClientWithError(String expectedError, boolean fragmentExpected, String expectedErrorDescription) {
        appPage.assertCurrent();
        assertEquals(expectedError, getParameterFromUrl(OAuth2Constants.ERROR, fragmentExpected));
        assertEquals(expectedErrorDescription, getParameterFromUrl(OAuth2Constants.ERROR_DESCRIPTION, fragmentExpected));
    }

    protected void assertBrowserWithError(String expectedError) {
        errorPage.assertCurrent();
        Assert.assertEquals(expectedError, errorPage.getError());
    }

    protected OAuthClient.AccessTokenResponse doAccessTokenRequestWithClientSignedJWT(String code, String signedJwt, String codeVerifier, Supplier<CloseableHttpClient> httpClientSupplier) {
        try {
            List<NameValuePair> parameters = new LinkedList<>();
            parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.AUTHORIZATION_CODE));
            parameters.add(new BasicNameValuePair(OAuth2Constants.CODE, code));
            if (codeVerifier != null) {
                parameters.add(new BasicNameValuePair(OAuth2Constants.CODE_VERIFIER, codeVerifier));
            }
            parameters.add(new BasicNameValuePair(OAuth2Constants.REDIRECT_URI, oauth.getRedirectUri()));
            parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
            parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

            CloseableHttpResponse response = sendRequest(oauth.getAccessTokenUrl(), parameters, httpClientSupplier);
            return new OAuthClient.AccessTokenResponse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected String createSignedRequestToken(String clientId, String algorithm) throws Exception {
        TestOIDCEndpointsApplicationResource oidcClientEndpointsResource = testingClient.testApp().oidcClientEndpoints();
        Map<String, String> generatedKeys = oidcClientEndpointsResource.getKeysAsBase64();
        KeyPair keyPair = getKeyPairFromGeneratedBase64(generatedKeys, algorithm);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        return createSignedRequestToken(clientId, privateKey, publicKey, algorithm);
    }

    protected CloseableHttpResponse sendRequest(String requestUrl, List<NameValuePair> parameters, Supplier<CloseableHttpClient> httpClientSupplier) throws Exception {
        CloseableHttpClient client = httpClientSupplier.get();
        try {
            HttpPost post = new HttpPost(requestUrl);
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(parameters, "UTF-8");
            post.setEntity(formEntity);
            return client.execute(post);
        } finally {
            oauth.closeClient(client);
        }
    }
}
