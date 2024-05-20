/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.cluster.infinispan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.jboss.logging.Logger;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheSessionsLoaderContext;
import org.keycloak.connections.infinispan.InfinispanUtil;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class JDGPutTest {

    public static final Logger logger = Logger.getLogger(JDGPutTest.class);

    public static void main(String[] args) throws Exception {
        Cache<String, Object> cache1 = createManager(1).getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);
        Cache<String, Object> cache2 = createManager(2).getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);

        try {
            //RemoteCache remoteCache1 = InfinispanUtil.getRemoteCache(cache1);
            //RemoteCache remoteCache2 = InfinispanUtil.getRemoteCache(cache2);

            //remoteCache1.put("key1", new Book("book1", "desc", 1));
            //remoteCache2.put("key2", );
            String uuidStr = UUID.randomUUID().toString();
            System.out.println(uuidStr);
            UUID uuid = UUID.fromString(uuidStr);
            AuthenticatedClientSessionEntity ace = new AuthenticatedClientSessionEntity(uuid);
            SessionEntityWrapper wrapper = new SessionEntityWrapper(ace);

            cache1.put("key1", wrapper);
            //cache1.put("key1", "val1");

            //AuthenticatedClientSessionEntity val1 = (AuthenticatedClientSessionEntity) cache2.get("key1");
            //RemoteCache remoteCache1 = InfinispanUtil.getRemoteCache(cache1);
            //remoteCache1.put("key1", "val1");
            RemoteCache remoteCache2 = InfinispanUtil.getRemoteCache(cache2);
            Object o = remoteCache2.get("key1");

            logger.info("Before retrieve entries");
            try (CloseableIterator it = remoteCache2.retrieveEntries(null, 64)) {
                Object o2 = it.next();
                logger.info("o2: " + o2);
            }

            //Object key = remoteCache2.keySet().iterator().next();
            //Object value = remoteCache2.values().iterator().next();
            //logger.info("Key: " + key + ", val: " + value);

            bulkLoadSessions(remoteCache2);
        }  finally {
            Thread.sleep(2000);

            // Finish JVM
            cache1.getCacheManager().stop();
            cache2.getCacheManager().stop();
        }
    }

    private static EmbeddedCacheManager createManager(int threadId) {
        return new TestCacheManagerFactory().createManager(threadId, InfinispanConnectionProvider.USER_SESSION_CACHE_NAME, RemoteStoreConfigurationBuilder.class);
    }

    private static void bulkLoadSessions(RemoteCache remoteCache) {
        RemoteCacheSessionsLoaderContext ctx = new RemoteCacheSessionsLoaderContext(64);

        Map<Object, Object> toInsert = new HashMap<>(ctx.getSessionsPerSegment());

        try (CloseableIterator<Map.Entry<Object, Object>> it = remoteCache.retrieveEntries(null, ctx.getSessionsPerSegment())) {
            while (it.hasNext()) {
                Map.Entry<?,?> entry = it.next();
                toInsert.put(entry.getKey(), entry.getValue());
            }

        } catch (RuntimeException e) {
            logger.warnf(e, "Error loading sessions from remote cache '%s'", remoteCache.getName());
            throw e;
        }

        logger.info("Loaded " + toInsert);

    }


}
