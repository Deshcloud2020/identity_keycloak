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

package org.keycloak.testsuite.organization.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.keycloak.models.OrganizationModel.BROKER_PUBLIC;
import static org.keycloak.models.OrganizationModel.ORGANIZATION_DOMAIN_ATTRIBUTE;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.OrganizationIdentityProviderResource;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.common.Profile.Feature;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;

@EnableFeature(Feature.ORGANIZATION)
public class OrganizationIdentityProviderTest extends AbstractOrganizationTest {

    @Test
    public void testUpdate() {
        OrganizationRepresentation organization = createOrganization();
        OrganizationIdentityProviderResource orgIdPResource = testRealm().organizations().get(organization.getId())
                .identityProviders().get(bc.getIDPAlias());
        IdentityProviderRepresentation expected = orgIdPResource.toRepresentation();

        // organization link set
        Assert.assertEquals(expected.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE), organization.getId());

        IdentityProviderResource idpResource = testRealm().identityProviders().get(expected.getAlias());
        IdentityProviderRepresentation actual = idpResource.toRepresentation();
        Assert.assertEquals(actual.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE), organization.getId());
        actual.getConfig().put(OrganizationModel.ORGANIZATION_ATTRIBUTE, "somethingelse");
        try {
            idpResource.update(actual);
            Assert.fail("Should fail because it maps to an invalid org");
        } catch (BadRequestException ignore) {
        }

        OrganizationRepresentation secondOrg = createOrganization("secondorg");
        actual.getConfig().put(OrganizationModel.ORGANIZATION_ATTRIBUTE, secondOrg.getId());
        idpResource.update(actual);
        actual = idpResource.toRepresentation();
        Assert.assertEquals(actual.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE), organization.getId());

        actual = idpResource.toRepresentation();
        // the link to the organization should not change
        Assert.assertEquals(actual.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE), organization.getId());
        actual.getConfig().remove(OrganizationModel.ORGANIZATION_ATTRIBUTE);
        idpResource.update(actual);
        actual = idpResource.toRepresentation();
        // the link to the organization should not change
        Assert.assertEquals(actual.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE), organization.getId());
    }

    @Test
    public void testDelete() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        IdentityProviderRepresentation idpTemplate = organization
                .identityProviders().get(bc.getIDPAlias()).toRepresentation();

        for (int i = 0; i < 5; i++) {
            idpTemplate.setAlias("idp-" + i);
            idpTemplate.setInternalId(null);
            testRealm().identityProviders().create(idpTemplate).close();
            organization.identityProviders().addIdentityProvider(idpTemplate.getAlias()).close();
        }

        Assert.assertEquals(6, organization.identityProviders().getIdentityProviders().size());

        for (int i = 0; i < 5; i++) {
            String alias = "idp-" + i;
            OrganizationIdentityProviderResource idpResource = organization.identityProviders().get(alias);

            try (Response response = idpResource.delete()) {
                assertThat(response.getStatus(), equalTo(Response.Status.NO_CONTENT.getStatusCode()));
            }

            try {
                idpResource.toRepresentation();
                Assert.fail("should be removed");
            } catch (NotFoundException expected) {
            }

            // not removed from the realm
            testRealm().identityProviders().get(alias).toRepresentation();
        }

        organization.identityProviders().get(bc.getIDPAlias()).delete().close();
        Assert.assertFalse(testRealm().identityProviders().findAll().isEmpty());
    }

    @Test
    public void testCreatingExistingIdentityProvider() {
        OrganizationResource organization = testRealm().organizations().get(createOrganization().getId());
        OrganizationIdentityProviderResource orgIdPResource = organization
                .identityProviders().get(bc.getIDPAlias());

        IdentityProviderRepresentation idpRepresentation = orgIdPResource.toRepresentation();

        String alias = idpRepresentation.getAlias();
        idpRepresentation.setAlias("another-idp");
        testRealm().identityProviders().create(idpRepresentation).close();

        try (Response response = organization.identityProviders().addIdentityProvider(alias)) {
            // already associated with the org
            assertThat(response.getStatus(), equalTo(Status.CONFLICT.getStatusCode()));
        }

        idpRepresentation.setAlias(alias);
        idpRepresentation.setInternalId(null);

        OrganizationResource secondOrg = testRealm().organizations().get(createOrganization("secondorg").getId());

        try (Response response = secondOrg.identityProviders().addIdentityProvider(alias)) {
            // associated with another org
            assertThat(response.getStatus(), equalTo(Status.BAD_REQUEST.getStatusCode()));
        }
    }

    @Test
    public void testRemovingOrgShouldRemoveIdP() {
        OrganizationRepresentation orgRep = createOrganization();
        OrganizationResource orgResource = testRealm().organizations().get(orgRep.getId());

        try (Response response = orgResource.delete()) {
            assertThat(response.getStatus(), equalTo(Response.Status.NO_CONTENT.getStatusCode()));
        }

        // broker not removed from realm
        IdentityProviderRepresentation idpRep = testRealm().identityProviders().get(bc.getIDPAlias()).toRepresentation();
        // broker no longer linked to the org
        Assert.assertNull(idpRep.getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE));
        Assert.assertNull(idpRep.getConfig().get(ORGANIZATION_DOMAIN_ATTRIBUTE));
        Assert.assertNull(idpRep.getConfig().get(BROKER_PUBLIC));
    }

    @Test
    public void testUpdateOrDeleteIdentityProviderNotAssignedToOrganization() {
        OrganizationRepresentation orgRep = createOrganization();
        OrganizationResource orgResource = testRealm().organizations().get(orgRep.getId());
        OrganizationIdentityProviderResource orgIdPResource = orgResource.identityProviders().get(bc.getIDPAlias());
        IdentityProviderRepresentation idpRepresentation = createRep("some-broker", "oidc");
        getCleanup().addCleanup(() -> testRealm().identityProviders().get(idpRepresentation.getAlias()).remove());
        //create IdP in realm not bound to Org
        testRealm().identityProviders().create(idpRepresentation).close();

        try (Response response = orgIdPResource.delete()) {
            assertThat(response.getStatus(), equalTo(Status.NO_CONTENT.getStatusCode()));
        }

        try (Response response = orgIdPResource.delete()) {
            assertThat(response.getStatus(), equalTo(Status.NOT_FOUND.getStatusCode()));
        }
    }

    @Test
    public void testAssignDomainNotBoundToOrganization() {
        OrganizationRepresentation orgRep = createOrganization();
        OrganizationResource orgResource = testRealm().organizations().get(orgRep.getId());
        OrganizationIdentityProviderResource orgIdPResource = orgResource.identityProviders().get(bc.getIDPAlias());
        IdentityProviderRepresentation idpRep = orgIdPResource.toRepresentation();
        idpRep.getConfig().put(ORGANIZATION_DOMAIN_ATTRIBUTE, "unknown.org");

        try {
            testRealm().identityProviders().get(idpRep.getAlias()).update(idpRep);
            Assert.fail("Domain set to broker is invalid");
        } catch (BadRequestException ignore) {

        }

        idpRep.setAlias("newbroker");
        idpRep.setInternalId(null);
        try (Response response = testRealm().identityProviders().create(idpRep)) {
            Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    public void testAddIdpFromDifferentRealm() {
        String orgId = createOrganization().getId();
        IdentityProviderRepresentation idpRepresentation = createRep("master-identity-provider", "oidc");
        adminClient.realm("master").identityProviders().create(idpRepresentation).close();

        getTestingClient().server(TEST_REALM_NAME).run(session -> {
            OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
            OrganizationModel organization = provider.getById(orgId);

            RealmModel realm = session.realms().getRealmByName("master");
            IdentityProviderModel idp = realm.getIdentityProviderByAlias("master-identity-provider");

            try {
                assertFalse(provider.addIdentityProvider(organization, idp));
            } finally {
                realm.removeIdentityProviderByAlias("master-identity-provider");
            }
        });
    }

    @Test
    public void testRemovedDomainUpdatedInIDP() {
        OrganizationRepresentation orgRep = createOrganization("testorg", "testorg.com", "testorg.net");
        OrganizationResource orgResource = testRealm().organizations().get(orgRep.getId());
        OrganizationIdentityProviderResource orgIdPResource = orgResource.identityProviders().get("testorg-identity-provider");
        IdentityProviderRepresentation idpRep = orgIdPResource.toRepresentation();

        // IDP should have been assigned to the first domain.
        assertThat(idpRep.getConfig().get(ORGANIZATION_DOMAIN_ATTRIBUTE), is(equalTo("testorg.com")));

        // let's update the organization, removing the domain linked to the IDP.
        orgRep.removeDomain(orgRep.getDomain("testorg.com"));
        try (Response response = orgResource.update(orgRep)) {
            assertThat(response.getStatus(), is(equalTo(Status.NO_CONTENT.getStatusCode())));
        }

        // fetch the idp config and check if the domain has been unlinked.
        idpRep = orgIdPResource.toRepresentation();
        assertThat(idpRep.getConfig().get(ORGANIZATION_DOMAIN_ATTRIBUTE), is(nullValue()));
    }

    private IdentityProviderRepresentation createRep(String alias, String providerId) {
        IdentityProviderRepresentation idp = new IdentityProviderRepresentation();

        idp.setAlias(alias);
        idp.setDisplayName(alias);
        idp.setProviderId(providerId);
        idp.setEnabled(true);
        return idp;
    }
}
