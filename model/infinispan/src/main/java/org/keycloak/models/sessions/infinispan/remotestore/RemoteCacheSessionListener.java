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

package org.keycloak.models.sessions.infinispan.remotestore;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.VersionedValue;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.client.hotrod.event.ClientEvent;
import org.infinispan.context.Flag;
import org.jboss.logging.Logger;
import org.keycloak.connections.infinispan.InfinispanUtil;
import org.keycloak.connections.infinispan.TopologyInfo;
import org.keycloak.executors.ExecutorsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.sessions.infinispan.SessionFunction;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.entities.SessionEntity;
import org.keycloak.models.sessions.infinispan.util.SessionTimeouts;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ClientListener
public class RemoteCacheSessionListener<K, V extends SessionEntity>  {

    protected static final Logger logger = Logger.getLogger(RemoteCacheSessionListener.class);

    private static final int MAXIMUM_REPLACE_RETRIES = 10;

    private Cache<K, SessionEntityWrapper<V>> cache;
    private RemoteCache<K, SessionEntityWrapper<V>> remoteCache;
    private TopologyInfo topologyInfo;
    private ClientListenerExecutorDecorator<K> executor;
    private SessionFunction<V> lifespanMsLoader;
    private SessionFunction<V> maxIdleTimeMsLoader;
    private KeycloakSessionFactory sessionFactory;


    protected RemoteCacheSessionListener() {
    }


    protected void init(KeycloakSession session, Cache<K, SessionEntityWrapper<V>> cache, RemoteCache<K, SessionEntityWrapper<V>> remoteCache,
                        SessionFunction<V> lifespanMsLoader, SessionFunction<V> maxIdleTimeMsLoader) {
        this.cache = cache;
        this.remoteCache = remoteCache;

        this.topologyInfo = InfinispanUtil.getTopologyInfo(session);

        this.lifespanMsLoader = lifespanMsLoader;
        this.maxIdleTimeMsLoader = maxIdleTimeMsLoader;
        this.sessionFactory = session.getKeycloakSessionFactory();

        ExecutorService executor = session.getProvider(ExecutorsProvider.class).getExecutor("client-listener-" + cache.getName());
        this.executor = new ClientListenerExecutorDecorator<>(executor);
    }


    @ClientCacheEntryCreated
    public void created(ClientCacheEntryCreatedEvent event) {
        K key = (K) event.getKey();

        if (shouldUpdateLocalCache(event.getType(), key, event.isCommandRetried())) {
            this.executor.submit(event, () -> {

                // Doesn't work due https://issues.jboss.org/browse/ISPN-9323. Needs to explicitly retrieve and create it
                //cache.get(key);

                createRemoteEntityInCache(key);

            });
        }
    }


    @ClientCacheEntryModified
    public void updated(ClientCacheEntryModifiedEvent event) {
        K key = (K) event.getKey();

        if (shouldUpdateLocalCache(event.getType(), key, event.isCommandRetried())) {

            this.executor.submit(event, () -> {

                replaceRemoteEntityInCache(key, event.getVersion());

            });
        }
    }


    protected void createRemoteEntityInCache(K key) {
        VersionedValue<SessionEntityWrapper<V>> remoteSessionVersioned = remoteCache.getWithMetadata(key);

        // Maybe can happen under some circumstances that remoteCache doesn't yet contain the value sent in the event (maybe just theoretically...)
        if (remoteSessionVersioned == null || remoteSessionVersioned.getValue() == null) {
            logger.debugf("Entity '%s' not present in remoteCache. Ignoring create", key);
            return;
        }


        V remoteSession = remoteSessionVersioned.getValue().getEntity();
        SessionEntityWrapper<V> newWrapper = new SessionEntityWrapper<>(remoteSession);

        logger.debugf("Read session entity wrapper from the remote cache: %s", remoteSession);

        KeycloakModelUtils.runJobInTransaction(sessionFactory, (session -> {

            RealmModel realm = session.realms().getRealm(newWrapper.getEntity().getRealmId());
            ClientModel client = newWrapper.getClientIfNeeded(realm);
            long lifespanMs = lifespanMsLoader.apply(realm, client, newWrapper.getEntity());
            long maxIdleTimeMs = maxIdleTimeMsLoader.apply(realm, client, newWrapper.getEntity());

            // It is possible the session may be expired by the time this has replicated, double check before inserting
            if (maxIdleTimeMs != SessionTimeouts.ENTRY_EXPIRED_FLAG && lifespanMs != SessionTimeouts.ENTRY_EXPIRED_FLAG) {
                logger.tracef("Calling putIfAbsent for entity '%s' in the cache '%s' . lifespan: %d ms, maxIdleTime: %d ms", key, remoteCache.getName(), lifespanMs, maxIdleTimeMs);
                // Using putIfAbsent. Theoretic possibility that entity was already put to cache by someone else
                cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE, Flag.SKIP_CACHE_LOAD, Flag.IGNORE_RETURN_VALUES)
                      .putIfAbsent(key, newWrapper, lifespanMs, TimeUnit.MILLISECONDS, maxIdleTimeMs, TimeUnit.MILLISECONDS);
            } else {
                logger.tracef("Not calling putIfAbsent for entity '%s' in the cache '%s' as entry is already expired", key, remoteCache.getName());
            }


        }));
    }


    protected void replaceRemoteEntityInCache(K key, long eventVersion) {
        // TODO can be optimized and remoteSession sent in the event itself?
        AtomicBoolean replaced = new AtomicBoolean(false);
        int replaceRetries = 0;
        int sleepInterval = 25;
        do {
            replaceRetries++;

            SessionEntityWrapper<V> localEntityWrapper = cache.get(key);
            VersionedValue<SessionEntityWrapper<V>> remoteSessionVersioned = remoteCache.getWithMetadata(key);

            // Probably already removed
            if (remoteSessionVersioned == null || remoteSessionVersioned.getValue() == null) {
                logger.debugf("Entity '%s' not present in remoteCache. Ignoring replace",
                        key);
                return;
            }

            if (remoteSessionVersioned.getVersion() < eventVersion) {
                try {
                    logger.debugf("Got replace remote entity event prematurely for entity '%s', will try again. Event version: %d, got: %d",
                            key, eventVersion, remoteSessionVersioned == null ? -1 : remoteSessionVersioned.getVersion());
                    Thread.sleep(new Random().nextInt(sleepInterval));  // using exponential backoff
                    continue;
                } catch (InterruptedException ex) {
                    continue;
                } finally {
                    sleepInterval = sleepInterval << 1;
                }
            }
            SessionEntity remoteSession = remoteSessionVersioned.getValue().getEntity();

            logger.debugf("Read session entity from the remote cache: %s . replaceRetries=%d", remoteSession, replaceRetries);

            SessionEntityWrapper<V> sessionWrapper = remoteSession.mergeRemoteEntityWithLocalEntity(localEntityWrapper);

            KeycloakModelUtils.runJobInTransaction(sessionFactory, (session -> {

                RealmModel realm = session.realms().getRealm(sessionWrapper.getEntity().getRealmId());
                ClientModel client = sessionWrapper.getClientIfNeeded(realm);
                long lifespanMs = lifespanMsLoader.apply(realm, client, sessionWrapper.getEntity());
                long maxIdleTimeMs = maxIdleTimeMsLoader.apply(realm, client, sessionWrapper.getEntity());

                // We received event from remoteCache, so we won't update it back
                replaced.set(cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE, Flag.SKIP_CACHE_LOAD, Flag.IGNORE_RETURN_VALUES)
                        .replace(key, localEntityWrapper, sessionWrapper, lifespanMs, TimeUnit.MILLISECONDS, maxIdleTimeMs, TimeUnit.MILLISECONDS));

            }));

            if (! replaced.get()) {
                logger.debugf("Did not succeed in merging sessions, will try again: %s", remoteSession);
            }
        } while (replaceRetries < MAXIMUM_REPLACE_RETRIES && ! replaced.get());
    }


    @ClientCacheEntryRemoved
    public void removed(ClientCacheEntryRemovedEvent event) {
        K key = (K) event.getKey();

        if (shouldUpdateLocalCache(event.getType(), key, event.isCommandRetried())) {

            this.executor.submit(event, () -> {

                // We received event from remoteCache, so we won't update it back
                cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE, Flag.SKIP_CACHE_LOAD, Flag.IGNORE_RETURN_VALUES)
                        .remove(key);

            });
        }
    }


    // For distributed caches, ensure that local modification is executed just on owner OR if event.isCommandRetried
    protected boolean shouldUpdateLocalCache(ClientEvent.Type type, K key, boolean commandRetried) {
        boolean result;

        // Case when cache is stopping or stopped already
        if (!cache.getStatus().allowInvocations()) {
            return false;
        }

        if (commandRetried) {
            result = true;
        } else {
            result = topologyInfo.amIOwner(cache, key);
        }

        logger.debugf("Received event from remote store. Event '%s', key '%s', skip '%b'", type, key, !result);

        return result;
    }

    public static <K, V extends SessionEntity> RemoteCacheSessionListener createListener(KeycloakSession session, Cache<K, SessionEntityWrapper<V>> cache, RemoteCache<K, SessionEntityWrapper<V>> remoteCache,
                                                                                         SessionFunction<V> lifespanMsLoader, SessionFunction<V> maxIdleTimeMsLoader) {
        /*boolean isCoordinator = InfinispanUtil.isCoordinator(cache);

        // Just cluster coordinator will fetch userSessions from remote cache.
        // In case that coordinator is failover during state fetch, there is slight risk that not all userSessions will be fetched to local cluster. Assume acceptable for now
        RemoteCacheSessionListener listener;
        if (isCoordinator) {
            logger.infof("Will fetch initial state from remote cache for cache '%s'", cache.getName());
            listener = new FetchInitialStateCacheListener();
        } else {
            logger.infof("Won't fetch initial state from remote cache for cache '%s'", cache.getName());
            listener = new DontFetchInitialStateCacheListener();
        }*/

        RemoteCacheSessionListener<K, V> listener = new RemoteCacheSessionListener<>();
        listener.init(session, cache, remoteCache, lifespanMsLoader, maxIdleTimeMsLoader);

        return listener;
    }


}
