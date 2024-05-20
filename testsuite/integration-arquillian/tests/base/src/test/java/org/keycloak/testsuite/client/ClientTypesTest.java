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
 *
 */

package org.keycloak.testsuite.client;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.junit.Test;
import org.keycloak.client.clienttype.ClientTypeManager;
import org.keycloak.common.util.ObjectUtil;
import org.keycloak.models.ClientModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientTypeRepresentation;
import org.keycloak.representations.idm.ClientTypesRepresentation;
import org.keycloak.representations.idm.ErrorRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.clienttype.impl.DefaultClientTypeProviderFactory;
import org.keycloak.testsuite.AbstractTestRealmKeycloakTest;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.arquillian.annotation.DisableFeature;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;
import org.keycloak.testsuite.arquillian.annotation.UncaughtServerErrorExpected;
import org.keycloak.testsuite.util.ClientBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.keycloak.common.Profile.Feature.CLIENT_TYPES;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@EnableFeature(value = CLIENT_TYPES)
public class ClientTypesTest extends AbstractTestRealmKeycloakTest {

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }

    @Test
    public void testFeatureWorksWhenEnabled() {
        checkIfFeatureWorks(true);
    }

    @Test
    @UncaughtServerErrorExpected
    @DisableFeature(value = CLIENT_TYPES, skipRestart = true)
    public void testFeatureDoesntWorkWhenDisabled() {
        checkIfFeatureWorks(false);
    }

    // Test create client with clientType filled. Check default properties are filled
    @Test
    public void testCreateClientWithClientType() {
        ClientRepresentation clientRep = createClientWithType("foo", ClientTypeManager.SERVICE_ACCOUNT);
        assertEquals("foo", clientRep.getClientId());
        assertEquals(ClientTypeManager.SERVICE_ACCOUNT, clientRep.getType());
        assertEquals(OIDCLoginProtocol.LOGIN_PROTOCOL, clientRep.getProtocol());
        Assert.assertFalse(clientRep.isStandardFlowEnabled());
        Assert.assertFalse(clientRep.isImplicitFlowEnabled());
        Assert.assertFalse(clientRep.isDirectAccessGrantsEnabled());
        Assert.assertTrue(clientRep.isServiceAccountsEnabled());
        Assert.assertFalse(clientRep.isPublicClient());
        Assert.assertFalse(clientRep.isBearerOnly());

        // Check type not included as client attribute
        Assert.assertFalse(clientRep.getAttributes().containsKey(ClientModel.TYPE));
    }

    @Test
    public void testThatCreateClientWithWrongClientTypeFails() {
        ClientRepresentation clientRep = ClientBuilder.create()
                .clientId("client-type-does-not-exist-request")
                .type("DNE")
                .build();

        Response response = testRealm().clients().create(clientRep);
        assertEquals(Response.Status.BAD_REQUEST, response.getStatusInfo());
    }

    @Test
    public void testUpdateClientWithClientType() {
        ClientRepresentation clientRep = createClientWithType("foo", ClientTypeManager.SERVICE_ACCOUNT);

        // Changing type should fail
        clientRep.setType(ClientTypeManager.STANDARD);
        try {
            testRealm().clients().get(clientRep.getId()).update(clientRep);
            Assert.fail("Not expected to update client");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Updating read-only attribute should fail
        clientRep.setType(ClientTypeManager.SERVICE_ACCOUNT);
        clientRep.setServiceAccountsEnabled(false);
        try {
            testRealm().clients().get(clientRep.getId()).update(clientRep);
            Assert.fail("Not expected to update client");
        } catch (BadRequestException bre) {
            assertErrorResponseContainsParams(bre.getResponse(), "serviceAccountsEnabled");
        }

        clientRep.setServiceAccountsEnabled(true);

        // Adding non-applicable attribute should not fail but not update client attribute
        clientRep.getAttributes().put(ClientModel.LOGO_URI, "https://foo");
        testRealm().clients().get(clientRep.getId()).update(clientRep);
        assertEquals(testRealm().clients().get(clientRep.getId()).toRepresentation().getAttributes().get(ClientModel.LOGO_URI), null);

        // Update of supported attribute should be successful
        clientRep.getAttributes().remove(ClientModel.LOGO_URI);
        clientRep.setRootUrl("https://foo");
        testRealm().clients().get(clientRep.getId()).update(clientRep);
    }

    @Test
    public void testCreateClientFailsWithMultipleInvalidClientTypeOverrides() {
        ClientRepresentation clientRep = ClientBuilder.create()
                .clientId("service-account-client-type-required-to-be-confidential-and-service-accounts-enabled")
                .type(ClientTypeManager.SERVICE_ACCOUNT)
                .serviceAccountsEnabled(false)
                .publicClient()
                .build();

        Response response = testRealm().clients().create(clientRep);
        assertErrorResponseContainsParams(response, "publicClient", "serviceAccountsEnabled");
    }

    private void assertErrorResponseContainsParams(Response response, String... items) {
        assertEquals(Response.Status.BAD_REQUEST, response.getStatusInfo());
        ErrorRepresentation errorRepresentation = response.readEntity(ErrorRepresentation.class);
        assertThat(
                List.of(errorRepresentation.getParams()),
                containsInAnyOrder(items));
    }

    @Test
    public void testClientTypesAdminRestAPI_globalTypes() {
        ClientTypesRepresentation clientTypes = testRealm().clientTypes().getClientTypes();

        assertEquals(0, clientTypes.getRealmClientTypes().size());

        List<String> globalClientTypeNames = clientTypes.getGlobalClientTypes().stream()
                .map(ClientTypeRepresentation::getName)
                .collect(Collectors.toList());
        Assert.assertNames(globalClientTypeNames, "sla", "service-account");

        ClientTypeRepresentation serviceAccountType = clientTypes.getGlobalClientTypes().stream()
                .filter(clientType -> "service-account".equals(clientType.getName()))
                .findFirst()
                .get();
        assertEquals("default", serviceAccountType.getProvider());

        ClientTypeRepresentation.PropertyConfig cfg = serviceAccountType.getConfig().get("standardFlowEnabled");
        assertPropertyConfig("standardFlowEnabled", cfg, false, null, null);

        cfg = serviceAccountType.getConfig().get("serviceAccountsEnabled");
        assertPropertyConfig("serviceAccountsEnabled", cfg, true, true, true);

        cfg = serviceAccountType.getConfig().get("tosUri");
        assertPropertyConfig("tosUri", cfg, false, null, null);
    }


    @Test
    public void testClientTypesAdminRestAPI_realmTypes() {
        ClientTypesRepresentation clientTypes = testRealm().clientTypes().getClientTypes();

        // Test invalid provider type should fail
        ClientTypeRepresentation clientType = new ClientTypeRepresentation();
        try {
            clientType.setName("sla1");
            clientType.setProvider("non-existent");
            clientType.setConfig(new HashMap<String, ClientTypeRepresentation.PropertyConfig>());
            clientTypes.setRealmClientTypes(Arrays.asList(clientType));
            testRealm().clientTypes().updateClientTypes(clientTypes);
            Assert.fail("Not expected to update client types");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Test attribute without applicable should fail
        try {
            clientType.setProvider(DefaultClientTypeProviderFactory.PROVIDER_ID);
            ClientTypeRepresentation.PropertyConfig cfg = new ClientTypeRepresentation.PropertyConfig();
            clientType.getConfig().put("standardFlowEnabled", cfg);
            testRealm().clientTypes().updateClientTypes(clientTypes);
            Assert.fail("Not expected to update client types");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Test non-applicable attribute with default-value should fail
        try {
            ClientTypeRepresentation.PropertyConfig cfg = clientType.getConfig().get("standardFlowEnabled");
            cfg.setApplicable(false);
            cfg.setReadOnly(true);
            cfg.setDefaultValue(true);
            testRealm().clientTypes().updateClientTypes(clientTypes);
            Assert.fail("Not expected to update client types");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Update should be successful
        ClientTypeRepresentation.PropertyConfig cfg = clientType.getConfig().get("standardFlowEnabled");
        cfg.setApplicable(true);
        testRealm().clientTypes().updateClientTypes(clientTypes);

        // Test duplicate name should fail
        ClientTypeRepresentation clientType2 = new ClientTypeRepresentation();
        try {
            clientTypes = testRealm().clientTypes().getClientTypes();
            clientType2 = new ClientTypeRepresentation();
            clientType2.setName("sla1");
            clientType2.setProvider(DefaultClientTypeProviderFactory.PROVIDER_ID);
            clientType2.setConfig(new HashMap<>());
            clientTypes.getRealmClientTypes().add(clientType2);
            testRealm().clientTypes().updateClientTypes(clientTypes);
            Assert.fail("Not expected to update client types");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Also test duplicated global name should fail
        try {
            clientType2.setName("service-account");
            testRealm().clientTypes().updateClientTypes(clientTypes);
            Assert.fail("Not expected to update client types");
        } catch (BadRequestException bre) {
            // Expected
        }

        // Different name should be fine
        clientType2.setName("different");
        testRealm().clientTypes().updateClientTypes(clientTypes);

        // Assert updated
        clientTypes = testRealm().clientTypes().getClientTypes();
        assertNames(clientTypes.getRealmClientTypes(), "sla1", "different");
        assertNames(clientTypes.getGlobalClientTypes(), "sla", "service-account");

        // Test updating global won't update anything. Nothing will be added to globalTypes
        clientType2.setName("moreDifferent");
        clientTypes.getGlobalClientTypes().add(clientType2);
        testRealm().clientTypes().updateClientTypes(clientTypes);

        clientTypes = testRealm().clientTypes().getClientTypes();
        assertNames(clientTypes.getRealmClientTypes(), "sla1", "different");
        assertNames(clientTypes.getGlobalClientTypes(), "sla", "service-account");
    }

    private void assertNames(List<ClientTypeRepresentation> clientTypes, String... expectedNames) {
        List<String> names = clientTypes.stream()
                .map(ClientTypeRepresentation::getName)
                .collect(Collectors.toList());
        Assert.assertNames(names, expectedNames);
    }


    private void assertPropertyConfig(String propertyName, ClientTypeRepresentation.PropertyConfig cfg, Boolean expectedApplicable, Boolean expectedReadOnly, Object expectedDefaultValue) {
        assertThat("'applicable' for property " + propertyName + " not equal", ObjectUtil.isEqualOrBothNull(expectedApplicable, cfg.getApplicable()));
        assertThat("'read-only' for property " + propertyName + " not equal", ObjectUtil.isEqualOrBothNull(expectedReadOnly, cfg.getReadOnly()));
        assertThat("'default-value' ;for property " + propertyName + " not equal", ObjectUtil.isEqualOrBothNull(expectedDefaultValue, cfg.getDefaultValue()));
    }

    private ClientRepresentation createClientWithType(String clientId, String clientType) {
        ClientRepresentation clientRep = ClientBuilder.create()
                .clientId(clientId)
                .type(clientType)
                .build();
        Response response = testRealm().clients().create(clientRep);
        String clientUUID = ApiUtil.getCreatedId(response);
        getCleanup().addClientUuid(clientUUID);

        return testRealm().clients().get(clientUUID).toRepresentation();
    }

    // Check if the feature really works
    private void checkIfFeatureWorks(boolean shouldWork) {
        try {
            ClientTypesRepresentation clientTypes = testRealm().clientTypes().getClientTypes();
            Assert.assertTrue(clientTypes.getRealmClientTypes().isEmpty());
            if (!shouldWork)
                fail("Feature is available, but at this moment should be disabled");

        } catch (Exception e) {
            if (shouldWork) {
                e.printStackTrace();
                fail("Feature is not available");
            }
        }
    }
}