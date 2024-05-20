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

package org.keycloak.testsuite.model;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.AbstractTestRealmKeycloakTest;
import org.keycloak.testsuite.arquillian.annotation.ModelTest;

import java.util.concurrent.atomic.AtomicReference;
import org.keycloak.models.Constants;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class MultipleRealmsTest extends AbstractTestRealmKeycloakTest {

    private static final String REALM_ATTRIBUTE = "test-realm";

    public static void createObjects(KeycloakSession session, RealmModel realm) {
        final List<String> realmNameList = Collections.singletonList(realm.getName());

        ClientModel app1 = realm.addClient("app1");
        app1.setAttribute(REALM_ATTRIBUTE, realm.getName());
        realm.addClient("app2").setAttribute(REALM_ATTRIBUTE, realm.getName());

        session.users().addUser(realm, "user1").setAttribute(REALM_ATTRIBUTE, realmNameList);
        session.users().addUser(realm, "user2").setAttribute(REALM_ATTRIBUTE, realmNameList);

        realm.addRole("role1").setAttribute(REALM_ATTRIBUTE, realmNameList);
        realm.addRole("role2").setAttribute(REALM_ATTRIBUTE, realmNameList);

        app1.addRole("app1Role1").setAttribute(REALM_ATTRIBUTE, realmNameList);
        app1.addScopeMapping(realm.getRole("role1"));

        realm.addClient("cl1").setAttribute(REALM_ATTRIBUTE, realm.getName());
    }

    @Test
    @ModelTest
    public void testUsers(KeycloakSession session) {
        AtomicReference<UserModel> r1user1Atomic = new AtomicReference<>();

        String[] res = KeycloakModelUtils.runJobInTransactionWithResult(session.getKeycloakSessionFactory(), (KeycloakSession sessionTestUser1) -> {
            KeycloakSession currentSession = sessionTestUser1;

            RealmModel realm1 = currentSession.realms().createRealm(KeycloakModelUtils.generateId(), "realm1");
            RealmModel realm2 = currentSession.realms().createRealm(KeycloakModelUtils.generateId(), "realm2");
            String id1 = realm1.getId();
            String id2 = realm2.getId();

            realm1.setDefaultRole(currentSession.roles().addRealmRole(realm1, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm1.getName()));
            realm2.setDefaultRole(currentSession.roles().addRealmRole(realm2, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm2.getName()));

            createObjects(currentSession, realm1);
            createObjects(currentSession, realm2);

            UserModel r1user1 = currentSession.users().getUserByUsername(realm1, "user1");
            UserModel r2user1 = currentSession.users().getUserByUsername(realm2, "user1");

            r1user1Atomic.set(r1user1);

            Assert.assertEquals(r1user1.getUsername(), r2user1.getUsername());
            // The following check is not valid anymore since file store does have the same ID, and is redundant due to the previous line
            // Assert.assertNotEquals(r1user1.getId(), r2user1.getId());

            // Test password
            r1user1.credentialManager().updateCredential(UserCredentialModel.password("pass1"));
            r2user1.credentialManager().updateCredential(UserCredentialModel.password("pass2"));

            Assert.assertTrue(r1user1.credentialManager().isValid(UserCredentialModel.password("pass1")));
            Assert.assertFalse(r1user1.credentialManager().isValid(UserCredentialModel.password("pass2")));
            Assert.assertFalse(r2user1.credentialManager().isValid(UserCredentialModel.password("pass1")));
            Assert.assertTrue(r2user1.credentialManager().isValid(UserCredentialModel.password("pass2")));

            // Test searching
            Assert.assertEquals(2, currentSession.users().searchForUserStream(realm1, Map.of(UserModel.SEARCH, "user")).count());

            return new String[] { id1, id2 };
        });

        String id1 = res[0];
        String id2 = res[1];

        KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), (KeycloakSession sessionTestUser2) -> {
            KeycloakSession currentSession = sessionTestUser2;

            RealmModel realm1 = currentSession.realms().getRealm(id1);
            RealmModel realm2 = currentSession.realms().getRealm(id2);

            UserModel r1user1 = r1user1Atomic.get();

            currentSession.users().removeUser(realm1, r1user1);
            UserModel user2 = currentSession.users().getUserByUsername(realm1, "user2");
            currentSession.users().removeUser(realm1, user2);
            Assert.assertEquals(0, currentSession.users().searchForUserStream(realm1, Map.of(UserModel.SEARCH, "user")).count());
            Assert.assertEquals(2, currentSession.users().searchForUserStream(realm2, Map.of(UserModel.SEARCH, "user")).count());


            UserModel user1 = currentSession.users().getUserByUsername(realm1, "user1");
            UserModel user1a = currentSession.users().getUserByUsername(realm2, "user1");

            UserManager um = new UserManager(currentSession);
            if (user1 != null) {
                um.removeUser(realm1, user1);
            }
            if (user1a != null) {
                um.removeUser(realm2, user1a);
            }
        });

        KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), (KeycloakSession sessionTestUser3) -> {
            KeycloakSession currentSession = sessionTestUser3;
            currentSession.realms().removeRealm(id1);
            currentSession.realms().removeRealm(id2);
        });
    }

    @Test
    @ModelTest
    public void testGetById(KeycloakSession session) {
        String[] res = KeycloakModelUtils.runJobInTransactionWithResult(session.getKeycloakSessionFactory(), (KeycloakSession sessionById) -> {
            KeycloakSession currentSession = sessionById;

            RealmModel realm1 = currentSession.realms().createRealm(KeycloakModelUtils.generateId(), "realm1");
            RealmModel realm2 = currentSession.realms().createRealm(KeycloakModelUtils.generateId(), "realm2");
            String id1 = realm1.getId();
            String id2 = realm2.getId();

            realm1.setDefaultRole(currentSession.roles().addRealmRole(realm1, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm1.getName()));
            realm2.setDefaultRole(currentSession.roles().addRealmRole(realm2, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm2.getName()));

            createObjects(currentSession, realm1);
            createObjects(currentSession, realm2);

            Assert.assertEquals(realm1, currentSession.realms().getRealm(id1));
            Assert.assertEquals(realm1, currentSession.realms().getRealmByName("realm1"));
            Assert.assertEquals(realm2, currentSession.realms().getRealm(id2));
            Assert.assertEquals(realm2, currentSession.realms().getRealmByName("realm2"));

            ClientModel r1app1 = realm1.getClientByClientId("app1");

            Assert.assertNotNull(realm1.getClientByClientId("app2"));
            Assert.assertNotNull(realm2.getClientByClientId("app1"));
            Assert.assertNotNull(realm2.getClientByClientId("app2"));

            Assert.assertEquals(r1app1, realm1.getClientById(r1app1.getId()));
            assertThat(r1app1.getAttribute(REALM_ATTRIBUTE), is(realm1.getName()));

            ClientModel r2cl1 = realm2.getClientByClientId("cl1");
            Assert.assertEquals(r2cl1.getId(), realm2.getClientById(r2cl1.getId()).getId());
            assertThat(r2cl1.getAttribute(REALM_ATTRIBUTE), is(realm2.getName()));

            RoleModel r1App1Role = r1app1.getRole("app1Role1");
            Assert.assertEquals(r1App1Role, realm1.getRoleById(r1App1Role.getId()));
            assertAttrRealm(realm1, r1App1Role.getAttributeStream(REALM_ATTRIBUTE));

            RoleModel r2Role1 = realm2.getRole("role2");
            assertAttrRealm(realm2, r2Role1.getAttributeStream(REALM_ATTRIBUTE));

            UserModel user1 = currentSession.users().getUserByUsername(realm1, "user1");
            assertAttrRealm(realm1, user1.getAttributeStream(REALM_ATTRIBUTE));
            UserModel user1a = currentSession.users().getUserByUsername(realm2, "user1");
            assertAttrRealm(realm2, user1a.getAttributeStream(REALM_ATTRIBUTE));

            UserManager um = new UserManager(currentSession);
            if (user1 != null) {
                um.removeUser(realm1, user1);
            }
            if (user1a != null) {
                um.removeUser(realm2, user1a);
            }

            return new String[] { id1, id2 };
        });

        String id1 = res[0];
        String id2 = res[1];

        KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), (KeycloakSession sessionTestUser3) -> {
            KeycloakSession currentSession = sessionTestUser3;
            currentSession.realms().removeRealm(id1);
            currentSession.realms().removeRealm(id2);
        });
    }

    private void assertAttrRealm(RealmModel realm, Stream<String> attr) {
        assertThat(attr.collect(Collectors.toList()), containsInAnyOrder(realm.getName()));
    }

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }
}
