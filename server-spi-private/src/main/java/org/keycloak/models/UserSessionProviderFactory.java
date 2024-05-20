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

package org.keycloak.models;

import org.keycloak.provider.ProviderFactory;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface UserSessionProviderFactory<T extends UserSessionProvider> extends ProviderFactory<T> {

    // This is supposed to prefill all userSessions and clientSessions from userSessionPersister to the userSession infinispan/memory storage
    // This method is no longer used as we don't have offline sessions preloading anymore
    @Deprecated
    default void loadPersistentSessions(KeycloakSessionFactory sessionFactory, final int maxErrors, final int sessionsPerSegment) {}

}
