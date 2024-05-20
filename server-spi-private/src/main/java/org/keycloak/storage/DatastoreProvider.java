/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.storage;

import org.keycloak.models.ClientProvider;
import org.keycloak.models.ClientScopeProvider;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserLoginFailureProvider;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.provider.Provider;
import org.keycloak.sessions.AuthenticationSessionProvider;


public interface DatastoreProvider extends Provider {
    AuthenticationSessionProvider authSessions();

    ClientScopeProvider clientScopes();

    ClientProvider clients();

    GroupProvider groups();

    UserLoginFailureProvider loginFailures();

    RealmProvider realms();

    RoleProvider roles();

    SingleUseObjectProvider singleUseObjects();
    
    UserProvider users();

    UserSessionProvider userSessions();

    ExportImportManager getExportImportManager();
}
