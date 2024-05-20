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

package org.keycloak.storage.ldap;

import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.ldap.idm.store.ldap.LDAPIdentityStore;
import org.keycloak.storage.ldap.mappers.LDAPConfigDecorator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class LDAPIdentityStoreRegistry {

    private static final Logger logger = Logger.getLogger(LDAPIdentityStoreRegistry.class);

    private final Map<String, LDAPConfig> ldapStores = new ConcurrentHashMap<>();

    public LDAPIdentityStore getLdapStore(KeycloakSession session, ComponentModel ldapModel, Map<ComponentModel, LDAPConfigDecorator> configDecorators) {
        // Ldap config might have changed for the realm. In this case, we must re-initialize
        MultivaluedHashMap<String, String> configModel = ldapModel.getConfig();
        LDAPConfig ldapConfig = new LDAPConfig(configModel);
        for (Map.Entry<ComponentModel, LDAPConfigDecorator> entry : configDecorators.entrySet()) {
            ComponentModel mapperModel = entry.getKey();
            LDAPConfigDecorator decorator = entry.getValue();

            decorator.updateLDAPConfig(ldapConfig, mapperModel);
        }

        LDAPConfig cachedConfig = ldapStores.get(ldapModel.getId());
        if (cachedConfig == null || !ldapConfig.equals(cachedConfig)) {
            logLDAPConfig(session, ldapModel, ldapConfig);
            ldapStores.put(ldapModel.getId(), ldapConfig);
        }

        return createLdapIdentityStore(session, ldapConfig);
    }

    // Don't log LDAP password
    private void logLDAPConfig(KeycloakSession session, ComponentModel ldapModel, LDAPConfig ldapConfig) {
        logger.infof("Creating new LDAP Store for the LDAP storage provider: '%s', LDAP Configuration: %s", ldapModel.getName(), ldapConfig.toString());

        if (logger.isDebugEnabled()) {
            RealmModel realm = session.realms().getRealm(ldapModel.getParentId());
            realm.getComponentsStream(ldapModel.getId()).forEach(c ->
                    logger.debugf("Mapper for provider: %s, Mapper name: %s, Provider: %s, Mapper configuration: %s",
                            ldapModel.getName(), c.getName(), c.getProviderId(), c.getConfig().toString()));
        }
    }

    /**
     * Create LDAPIdentityStore to be cached in the local registry
     */
    public static LDAPIdentityStore createLdapIdentityStore(KeycloakSession session, LDAPConfig cfg) {
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.authentication", cfg.getConnectionPoolingAuthentication(), "none simple");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.initsize", cfg.getConnectionPoolingInitSize(), "1");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.maxsize", cfg.getConnectionPoolingMaxSize(), "1000");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.prefsize", cfg.getConnectionPoolingPrefSize(), "5");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.timeout", cfg.getConnectionPoolingTimeout(), "300000");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.protocol", cfg.getConnectionPoolingProtocol(), "plain ssl");
        checkSystemProperty("com.sun.jndi.ldap.connect.pool.debug", cfg.getConnectionPoolingDebug(), "off");

        return new LDAPIdentityStore(session, cfg);
    }

    private static void checkSystemProperty(String name, String cfgValue, String defaultValue) {
        String value = System.getProperty(name);
        if(cfgValue != null) {
            value = cfgValue;
        }
        if(value == null) {
            value = defaultValue;
        }
        System.setProperty(name, value);
    }
}
