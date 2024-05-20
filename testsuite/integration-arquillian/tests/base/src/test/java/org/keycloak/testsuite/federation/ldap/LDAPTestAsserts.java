/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.federation.ldap;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.representations.idm.SynchronizationResultRepresentation;
import org.keycloak.storage.user.SynchronizationResult;

/**
 * Common LDAP asserts
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class LDAPTestAsserts {

    public static UserModel assertUserImported(UserProvider userProvider, RealmModel realm, String username,
            String expectedFirstName, String expectedLastName, String expectedEmail, String expectedPostalCode) {
        return assertUserImported(userProvider, realm, username, expectedFirstName,
                expectedLastName, expectedEmail, expectedPostalCode, new MultivaluedHashMap<>());
    }

    public static UserModel assertUserImported(UserProvider userProvider, RealmModel realm, String username, String expectedFirstName,
            String expectedLastName, String expectedEmail, String expectedPostalCode, MultivaluedHashMap<String, String> otherAttrs) {
        UserModel user = userProvider.getUserByUsername(realm, username);
        assertLoaded(user, username, expectedFirstName, expectedLastName, expectedEmail, expectedPostalCode, otherAttrs);
        return user;
    }

    public static void assertLoaded(UserModel user, String username, String expectedFirstName,
            String expectedLastName, String expectedEmail, String expectedPostalCode) {
        assertLoaded(user, username, expectedFirstName, expectedLastName, expectedEmail, expectedPostalCode, new MultivaluedHashMap<>());
    }

    public static void assertLoaded(UserModel user, String username, String expectedFirstName, String expectedLastName,
            String expectedEmail, String expectedPostalCode, MultivaluedHashMap<String, String> otherAttrs) {
        Assert.assertNotNull(user);
        Assert.assertEquals(expectedFirstName, user.getFirstName());
        Assert.assertEquals(expectedLastName, user.getLastName());
        Assert.assertEquals(expectedEmail, user.getEmail());
        Assert.assertEquals(expectedPostalCode, user.getFirstAttribute("postal_code"));
        for (String name : otherAttrs.keySet()) {
            MatcherAssert.assertThat(otherAttrs.getList(name), Matchers.containsInAnyOrder(user.getAttributeStream(name).toArray(String[]::new)));
        }
    }


    public static void assertSyncEquals(SynchronizationResult syncResult, int expectedAdded, int expectedUpdated, int expectedRemoved, int expectedFailed) {
        Assert.assertEquals(expectedAdded, syncResult.getAdded());
        Assert.assertEquals(expectedUpdated, syncResult.getUpdated());
        Assert.assertEquals(expectedRemoved, syncResult.getRemoved());
        Assert.assertEquals(expectedFailed, syncResult.getFailed());
    }


    public static void assertSyncEquals(SynchronizationResultRepresentation syncResult, int expectedAdded, int expectedUpdated, int expectedRemoved, int expectedFailed) {
        Assert.assertEquals(expectedAdded, syncResult.getAdded());
        Assert.assertEquals(expectedUpdated, syncResult.getUpdated());
        Assert.assertEquals(expectedRemoved, syncResult.getRemoved());
        Assert.assertEquals(expectedFailed, syncResult.getFailed());
    }
}
