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

package org.keycloak.events;

import org.jboss.logging.Logger;
import org.keycloak.common.ClientConnection;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;

import org.keycloak.models.utils.KeycloakModelUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class EventBuilder {

    private static final Logger log = Logger.getLogger(EventBuilder.class);

    private final KeycloakSession session;
    private EventStoreProvider store;
    private List<EventListenerProvider> listeners;
    private RealmModel realm;
    private Event event;
    private Boolean storeImmediately;
    private final boolean isEventsEnabled;

    public EventBuilder(RealmModel realm, KeycloakSession session, ClientConnection clientConnection) {
        this(realm, session);
        ipAddress(clientConnection.getRemoteAddr());
    }

    public EventBuilder(RealmModel realm, KeycloakSession session) {
        this.session = session;
        this.realm = realm;
        this.isEventsEnabled = realm.isEventsEnabled();

        event = new Event();

        this.store = this.isEventsEnabled ? getEventStoreProvider(session) : null;
        this.listeners = getEventListeners(session, realm);

        realm(realm);
    }

    private static EventStoreProvider getEventStoreProvider(KeycloakSession session) {
        EventStoreProvider store = session.getProvider(EventStoreProvider.class);
        if (store == null) {
            log.error("Events enabled, but no event store provider configured");
        }

        return store;
    }

    private static List<EventListenerProvider> getEventListeners(KeycloakSession session, RealmModel realm) {
        return realm.getEventsListenersStream().map(id -> {
            EventListenerProvider listener = session.getProvider(EventListenerProvider.class, id);
            if (listener != null) {
                return listener;
            } else {
                log.error("Event listener '" + id + "' registered, but provider not found");
                return null;
            }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }

    private EventBuilder(KeycloakSession session, EventStoreProvider store, List<EventListenerProvider> listeners, RealmModel realm, Event event) {
        this.listeners = listeners;
        this.realm = realm;
        this.event = event;
        this.session = session;
        this.store = store;
        this.isEventsEnabled = realm.isEventsEnabled();
    }

    public EventBuilder realm(RealmModel realm) {
        event.setRealmId(realm == null ? null : realm.getId());
        return this;
    }

    public EventBuilder realm(String realmId) {
        event.setRealmId(realmId);
        return this;
    }

    public EventBuilder client(ClientModel client) {
        event.setClientId(client == null ? null : client.getClientId());
        return this;
    }

    public EventBuilder client(String clientId) {
        event.setClientId(clientId);
        return this;
    }

    public EventBuilder user(UserModel user) {
        event.setUserId(user == null ? null : user.getId());
        return this;
    }

    public EventBuilder user(String userId) {
        event.setUserId(userId);
        return this;
    }

    public EventBuilder session(UserSessionModel session) {
        event.setSessionId(session == null ? null : session.getId());
        return this;
    }

    public EventBuilder session(String sessionId) {
        event.setSessionId(sessionId);
        return this;
    }

    public EventBuilder ipAddress(String ipAddress) {
        event.setIpAddress(ipAddress);
        return this;
    }

    public EventBuilder event(EventType e) {
        event.setType(e);
        return this;
    }

    public EventBuilder detail(String key, String value) {
        if (value == null || value.equals("")) {
            return this;
        }

        if (event.getDetails() == null) {
            event.setDetails(new HashMap<>());
        }
        event.getDetails().put(key, value);
        return this;
    }
    
    /**
     * Add event detail where strings from the input Collection are filtered not to contain <code>null</code> and then joined using <code>::</code> character. 
     * 
     * @param key of the detail
     * @param values, can be null
     * @return builder for chaining
     */
    public EventBuilder detail(String key, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        return detail(key, values.stream().filter(Objects::nonNull).collect(Collectors.joining("::")));
    }
    
    /**
     * Add event detail where strings from the input Stream are filtered not to contain <code>null</code> and then joined using <code>::</code> character. 
     * 
     * @param key of the detail
     * @param values, can be null
     * @return builder for chaining
     */
    public EventBuilder detail(String key, Stream<String> values) {
        if (values == null) {
            return this;
        }
        return detail(key, values.filter(Objects::nonNull).collect(Collectors.joining("::")));
    }

    /**
     * Sets the time when to store the event.
     * By default, events marked as success ({@link #success()}) are stored upon commit of the session's transaction
     * while the failures ({@link #error(java.lang.String)} are stored and propagated to the event listeners
     * immediately into the event store.
     * @param forcedValue If {@code true}, the event is stored in the event store immediately. If {@code false},
     *   the event is stored upon commit.
     * @return
     */
    public EventBuilder storeImmediately(boolean forcedValue) {
        this.storeImmediately = forcedValue;
        return this;
    }

    public EventBuilder removeDetail(String key) {
        if (event.getDetails() != null) {
            event.getDetails().remove(key);
        }
        return this;
    }

    public Event getEvent() {
        return event;
    }

    public void success() {
        send(this.storeImmediately == null ? false : this.storeImmediately);
    }

    public void error(String error) {
        if (Objects.isNull(event.getType())) {
            throw new IllegalStateException("Attempted to define event error without first setting the event type");
        }

        if (!event.getType().name().endsWith("_ERROR")) {
            event.setType(EventType.valueOf(event.getType().name() + "_ERROR"));
        }
        event.setError(error);
        send(this.storeImmediately == null ? true : this.storeImmediately);
    }

    public EventBuilder clone() {
        return new EventBuilder(session, store, listeners, realm, event.clone());
    }

    private void send(boolean sendImmediately) {
        event.setTime(Time.currentTimeMillis());
        event.setId(UUID.randomUUID().toString());

        Set<String> eventTypes = realm.getEnabledEventTypesStream().collect(Collectors.toSet());
        if (sendImmediately) {
            KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), session.getContext(), innerSession -> {
                EventStoreProvider store = this.isEventsEnabled ? getEventStoreProvider(innerSession) : null;
                List<EventListenerProvider> listeners = getEventListeners(innerSession, realm);

                sendNow(store, eventTypes, listeners);
            });
        } else {
            sendNow(this.store, eventTypes, this.listeners);
        }
    }

    private void sendNow(EventStoreProvider targetStore, Set<String> eventTypes, List<EventListenerProvider> targetListeners) {
        if (targetStore != null) {
            if (eventTypes.isEmpty() && event.getType().isSaveByDefault() || eventTypes.contains(event.getType().name())) {
                targetStore.onEvent(event);
            }
        }

        for (EventListenerProvider l : targetListeners) {
            try {
                l.onEvent(event);
            } catch (Throwable t) {
                log.error("Failed to send type to " + l, t);
            }
        }
    }

}
