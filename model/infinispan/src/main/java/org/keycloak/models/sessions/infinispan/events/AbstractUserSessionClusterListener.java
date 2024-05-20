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

package org.keycloak.models.sessions.infinispan.events;

import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterListener;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.connections.infinispan.TopologyInfo;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.connections.infinispan.InfinispanUtil;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.Provider;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class AbstractUserSessionClusterListener<SE extends SessionClusterEvent, T extends Provider> implements ClusterListener {

    private static final Logger log = Logger.getLogger(AbstractUserSessionClusterListener.class);

    private final KeycloakSessionFactory sessionFactory;

    private final Class<T> providerClazz;

    public AbstractUserSessionClusterListener(KeycloakSessionFactory sessionFactory, Class<T> providerClazz) {
        this.sessionFactory = sessionFactory;
        this.providerClazz = providerClazz;
    }


    @Override
    public void eventReceived(ClusterEvent event) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, (KeycloakSession session) -> {
            T provider = session.getProvider(providerClazz);
            SE sessionEvent = (SE) event;

            boolean shouldResendEvent = shouldResendEvent(session, sessionEvent);

            if (log.isDebugEnabled()) {
                log.debugf("Received user session event '%s'. Should resend event: %b", sessionEvent.toString(), shouldResendEvent);
            }

            eventReceived(provider, sessionEvent);

            if (shouldResendEvent) {
                session.getProvider(ClusterProvider.class).notify(sessionEvent.getEventKey(), event, true, ClusterProvider.DCNotify.ALL_BUT_LOCAL_DC);
            }

        });
    }

    protected abstract void eventReceived(T provider, SE sessionEvent);


    private boolean shouldResendEvent(KeycloakSession session, SessionClusterEvent event) {
        if (!event.isResendingEvent()) {
            return false;
        }

        // Just the initiator will re-send the event after receiving it
        TopologyInfo topology = InfinispanUtil.getTopologyInfo(session);
        String myNode = topology.getMyNodeName();
        String mySite = topology.getMySiteName();
        return (event.getNodeId() != null && event.getNodeId().equals(myNode) && event.getSiteId() != null && event.getSiteId().equals(mySite));
    }

}
