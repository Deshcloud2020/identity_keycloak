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
package org.keycloak.testsuite.x509;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel.IdentityMapperType;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel.MappingSourceType;
import org.keycloak.testsuite.util.PhantomJSBrowser;
import org.openqa.selenium.WebDriver;

/**
 *
 * @author rmartinc
 */
public class X509SingleCertificateBrowserCRLTest extends AbstractX509AuthenticationTest {

    @ClassRule
    public static CRLRule crlRule = new CRLRule();

    @Drone
    @PhantomJSBrowser
    private WebDriver phantomJS;

    @Before
    public void replaceTheDefaultDriver() {
        replaceDefaultWebDriver(phantomJS);
    }

    @BeforeClass
    public static void onBeforeTestClass() {
        // configure single certificate without CA cert
        configurePhantomJS(null, "/client-ca.crt", "/client-ca.key", "password");
    }

    @Test
    public void loginSuccessWithSingleCertificateEmptyRevocationListFromHttp() throws Exception {
        X509AuthenticatorConfigModel config =
                new X509AuthenticatorConfigModel()
                        .setCRLEnabled(true)
                        .setCRLRelativePath(CRLRule.CRL_RESPONDER_ORIGIN + "/" + EMPTY_CRL_PATH)
                        .setConfirmationPageAllowed(true)
                        .setMappingSourceType(MappingSourceType.SUBJECTDN_EMAIL)
                        .setUserIdentityMapperType(IdentityMapperType.USERNAME_EMAIL);
        x509BrowserLogin(config, userId, "test-user@localhost", "test-user@localhost");
    }
}
