/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 *  and other contributors as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.keycloak.testsuite.broker;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.OAuthErrorException;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.models.Constants;
import org.keycloak.protocol.oidc.utils.OIDCResponseMode;
import org.keycloak.protocol.oidc.utils.OIDCResponseType;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.updaters.RealmAttributeUpdater;
import org.keycloak.testsuite.util.BrowserTabUtil;
import org.keycloak.testsuite.util.InfinispanTestTimeServiceRule;
import org.keycloak.testsuite.util.OAuthClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.keycloak.testsuite.AssertEvents.DEFAULT_REDIRECT_URI;
import static org.keycloak.testsuite.broker.BrokerTestTools.waitForPage;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KcOidcMultipleTabsBrokerTest  extends AbstractInitializedBaseBrokerTest {

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Rule
    public InfinispanTestTimeServiceRule ispnTestTimeService = new InfinispanTestTimeServiceRule(this);

    private String providerRealmId;
    private String consumerRealmId;

    @Override
    protected BrokerConfiguration getBrokerConfiguration() {
        return KcOidcBrokerConfiguration.INSTANCE;
    }

    // Similar to MultipleTabsLoginTest.multipleTabsParallelLogin but with IDP brokering test involved
    @Test
    public void testAuthenticationExpiredWithMoreBrowserTabs_clickIdpLoginInTab1AfterExpiration() {
        try (BrowserTabUtil tabUtil = BrowserTabUtil.getInstanceAndSetEnv(driver)) {
            oauth.clientId("broker-app");
            loginPage.open(bc.consumerRealmName());
            getLogger().infof("URL in tab 1: %s", driver.getCurrentUrl());

            // Open new tab 2
            tabUtil.newTab(oauth.getLoginFormUrl());
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(2));
            Assert.assertTrue(loginPage.isCurrent("consumer"));
            getLogger().infof("URL in tab2: %s", driver.getCurrentUrl());

            setTimeOffset(7200000);

            // Finish login in tab2
            loginPage.clickSocial(bc.getIDPAlias());
            Assert.assertEquals(loginPage.getError(), "Your login attempt timed out. Login will start from the beginning.");
            logInWithBroker(bc);

            waitForPage(driver, "update account information", false);
            updateAccountInformationPage.assertCurrent();
            Assert.assertTrue("We must be on consumer realm right now",
                    driver.getCurrentUrl().contains("/auth/realms/" + bc.consumerRealmName() + "/"));
            updateAccountInformationPage.updateAccountInformation(bc.getUserLogin(), bc.getUserEmail(), "Firstname", "Lastname");
            appPage.assertCurrent();

            // Go back to tab1 and click "login with IDP". Should be ideally logged-in automatically
            tabUtil.closeTab(1);
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(1));
            loginPage.clickSocial(bc.getIDPAlias());

            assertOnAppPageWithAlreadyLoggedInError();
        }

    }

    // Similar to MultipleTabsLoginTest.multipleTabsParallelLogin but with IDP brokering test involved
    @Test
    public void testAuthenticationExpiredWithMoreBrowserTabs_loginExpiredInBothConsumerAndProvider() {
        try (BrowserTabUtil tabUtil = BrowserTabUtil.getInstanceAndSetEnv(driver)) {
            // Open login page in tab1 and click "login with IDP"
            oauth.clientId("broker-app");
            loginPage.open(bc.consumerRealmName());
            loginPage.clickSocial(bc.getIDPAlias());

            // Open login page in tab 2
            tabUtil.newTab(oauth.getLoginFormUrl());
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(2));
            Assert.assertTrue(loginPage.isCurrent("consumer"));
            getLogger().infof("URL in tab2: %s", driver.getCurrentUrl());

            setTimeOffset(7200000);

            // Finish login in tab2
            loginPage.clickSocial(bc.getIDPAlias());
            Assert.assertEquals(loginPage.getError(), "Your login attempt timed out. Login will start from the beginning.");
            logInWithBroker(bc);

            waitForPage(driver, "update account information", false);
            updateAccountInformationPage.assertCurrent();
            Assert.assertTrue("We must be on consumer realm right now",
                    driver.getCurrentUrl().contains("/auth/realms/" + bc.consumerRealmName() + "/"));
            updateAccountInformationPage.updateAccountInformation(bc.getUserLogin(), bc.getUserEmail(), "Firstname", "Lastname");
            appPage.assertCurrent();
            events.clear();

            // Login in provider realm will redirect back to consumer with "authentication_expired" error.
            // The consumer has also expired authentication session, so that one will redirect straight to client due the "clientData" in IdentityBrokerState
            tabUtil.closeTab(1);
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(1));
            loginPage.login(bc.getUserLogin(), bc.getUserPassword());

            // Event for "already logged-in" in the provider realm
            events.expectLogin().error(Errors.ALREADY_LOGGED_IN)
                    .realm(getProviderRealmId())
                    .client("brokerapp")
                    .user((String) null)
                    .session((String) null)
                    .removeDetail(Details.CONSENT)
                    .removeDetail(Details.CODE_ID)
                    .detail(Details.REDIRECT_URI, Matchers.equalTo(OAuthClient.AUTH_SERVER_ROOT + "/realms/" + bc.consumerRealmName() + "/broker/" + bc.getIDPAlias() + "/endpoint"))
                    .detail(Details.REDIRECTED_TO_CLIENT, "true")
                    .detail(Details.RESPONSE_TYPE, OIDCResponseType.CODE)
                    .detail(Details.RESPONSE_MODE, OIDCResponseMode.QUERY.value())
                    .assertEvent();

            // Event for "already logged-in" in the consumer realm
            events.expect(EventType.IDENTITY_PROVIDER_LOGIN).error(Errors.ALREADY_LOGGED_IN)
                    .realm(getConsumerRealmId())
                    .client("broker-app")
                    .user((String) null)
                    .session((String) null)
                    .detail(Details.REDIRECT_URI, Matchers.equalTo(DEFAULT_REDIRECT_URI))
                    .detail(Details.REDIRECTED_TO_CLIENT, "true")
                    .detail(Details.RESPONSE_TYPE, OIDCResponseType.CODE)
                    .detail(Details.RESPONSE_MODE, OIDCResponseMode.QUERY.value())
                    .assertEvent();

            assertOnAppPageWithAlreadyLoggedInError();

        }
    }

    @Test
    public void testAuthenticationExpiredWithMoreBrowserTabs_loginExpiredInProvider() throws Exception {
        // Testing the scenario when authenticationSession expired only in "provider" realm and "consumer" is able to handle it  at IDP.
        // So need to increase authSession timeout on "consumer"
        try (BrowserTabUtil tabUtil = BrowserTabUtil.getInstanceAndSetEnv(driver);
             AutoCloseable realmUpdater = new RealmAttributeUpdater(adminClient.realm(bc.consumerRealmName()))
                     .setAccessCodeLifespanLogin(7200)
                     .update()
        ) {
            // Open login page in tab1 and click "login with IDP"
            oauth.clientId("broker-app");
            loginPage.open(bc.consumerRealmName());
            loginPage.clickSocial(bc.getIDPAlias());

            // Open login page in tab 2
            tabUtil.newTab(oauth.getLoginFormUrl());
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(2));
            Assert.assertTrue(loginPage.isCurrent("consumer"));
            getLogger().infof("URL in tab2: %s", driver.getCurrentUrl());

            setTimeOffset(3600);

            // Finish login in tab2
            logInWithBroker(bc);

            waitForPage(driver, "update account information", false);
            updateAccountInformationPage.assertCurrent();
            Assert.assertTrue("We must be on consumer realm right now",
                    driver.getCurrentUrl().contains("/auth/realms/" + bc.consumerRealmName() + "/"));
            updateAccountInformationPage.updateAccountInformation(bc.getUserLogin(), bc.getUserEmail(), "Firstname", "Lastname");
            appPage.assertCurrent();
            events.clear();

            // Login in provider realm will redirect back to consumer with "authentication_expired" error. That one will handle the "authentication_expired" error and redirect back to "provider"
            tabUtil.closeTab(1);
            assertThat(tabUtil.getCountOfTabs(), Matchers.equalTo(1));
            loginPage.login(bc.getUserLogin(), bc.getUserPassword());

            // Event for "already logged-in" in the provider realm
            events.expectLogin().error(Errors.ALREADY_LOGGED_IN)
                    .realm(getProviderRealmId())
                    .client("brokerapp")
                    .user((String) null)
                    .session((String) null)
                    .removeDetail(Details.CONSENT)
                    .removeDetail(Details.CODE_ID)
                    .detail(Details.REDIRECT_URI, Matchers.equalTo(OAuthClient.AUTH_SERVER_ROOT + "/realms/" + bc.consumerRealmName() + "/broker/" + bc.getIDPAlias() + "/endpoint"))
                    .detail(Details.REDIRECTED_TO_CLIENT, "true")
                    .detail(Details.RESPONSE_TYPE, OIDCResponseType.CODE)
                    .detail(Details.RESPONSE_MODE, OIDCResponseMode.QUERY.value())
                    .assertEvent();

            // SAML IDP on "consumer" will retry IDP login on the "provider"
            events.expect(EventType.IDENTITY_PROVIDER_LOGIN)
                    .realm(getConsumerRealmId())
                    .client("broker-app")
                    .user((String) null)
                    .detail(Details.IDENTITY_PROVIDER, bc.getIDPAlias())
                    .detail(Details.LOGIN_RETRY, "true")
                    .assertEvent();

            // We were redirected back to IDP where user is asked to re-authenticate (due prompt=login being sent to OIDC IDP in authz request)
            Assert.assertEquals("Please re-authenticate to continue", loginPage.getInfoMessage());
            Assert.assertTrue("We must be on provider realm right now",driver.getCurrentUrl().contains("/auth/realms/" + bc.providerRealmName() + "/"));
            loginPage.login(bc.getUserPassword());

            // Login finished on IDP (provider) as well as on "consumer" realm after being redirected there from "provider"
            events.expectLogin()
                    .realm(getProviderRealmId())
                    .client("brokerapp")
                    .user(AssertEvents.isUUID())
                    .detail(Details.REDIRECT_URI, Matchers.equalTo(OAuthClient.AUTH_SERVER_ROOT + "/realms/" + bc.consumerRealmName() + "/broker/" + bc.getIDPAlias() + "/endpoint"))
                    .assertEvent();

            Assert.assertEquals(EventType.CODE_TO_TOKEN.name(), events.poll().getType());
            Assert.assertEquals(EventType.USER_INFO_REQUEST.name(), events.poll().getType());

            events.expectLogin()
                    .realm(getConsumerRealmId())
                    .client("broker-app")
                    .user(AssertEvents.isUUID())
                    .detail(Details.IDENTITY_PROVIDER, bc.getIDPAlias())
                    .assertEvent();

            // Being redirected back to consumer and then back to client right away. Authentication session on "consumer" realm is still valid, so no error here.
            appPage.assertCurrent();
            OAuthClient.AuthorizationEndpointResponse authzResponse = new OAuthClient.AuthorizationEndpointResponse(oauth);
            org.keycloak.testsuite.Assert.assertNotNull(authzResponse.getCode());
            org.keycloak.testsuite.Assert.assertNull(authzResponse.getError());
        }
    }

    // Assert browser was redirected to the appPage with "error=temporarily_unavailable" and error_description corresponding to Constants.AUTHENTICATION_EXPIRED_MESSAGE
    private void assertOnAppPageWithAlreadyLoggedInError() {
        appPage.assertCurrent(); // Page "You are already logged in." should not be here
        OAuthClient.AuthorizationEndpointResponse authzResponse = new OAuthClient.AuthorizationEndpointResponse(oauth);
        org.keycloak.testsuite.Assert.assertEquals(OAuthErrorException.TEMPORARILY_UNAVAILABLE, authzResponse.getError());
        org.keycloak.testsuite.Assert.assertEquals(Constants.AUTHENTICATION_EXPIRED_MESSAGE, authzResponse.getErrorDescription());
    }

    private String getProviderRealmId() {
        if (providerRealmId != null) return providerRealmId;
        providerRealmId = adminClient.realm(bc.providerRealmName()).toRepresentation().getId();
        return providerRealmId;
    }

    private String getConsumerRealmId() {
        if (consumerRealmId != null) return consumerRealmId;
        consumerRealmId = adminClient.realm(bc.consumerRealmName()).toRepresentation().getId();
        return consumerRealmId;
    }
}
