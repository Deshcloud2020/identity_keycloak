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

package org.keycloak.connections.infinispan;

import org.infinispan.Cache;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.LocalModeAddress;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jboss.logging.Logger;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.NameCache;
import org.keycloak.Config;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TopologyInfo {

    private static final Logger logger = Logger.getLogger(TopologyInfo.class);


    // Node name used in clustered environment. This typically points to "jboss.node.name" . If "jboss.node.name" is not set, it is randomly generated
    // name
    private final String myNodeName;

    // Used just if "site" is configured (typically in cross-dc environment). Otherwise null
    private final String mySiteName;

    private final boolean isGeneratedNodeName;


    public TopologyInfo(EmbeddedCacheManager cacheManager, Config.Scope config, boolean embedded, String providerId) {
        String siteName;
        String nodeName;
        boolean isGeneratedNodeName = false;

        if (System.getProperty(InfinispanConnectionProvider.JBOSS_SITE_NAME) != null) {
            throw new IllegalArgumentException(
                    String.format("System property %s is in use. Use --spi-connections-infinispan-%s-site-name config option instead",
                            InfinispanConnectionProvider.JBOSS_SITE_NAME, providerId));
        }

        if (!embedded) {
            var addr = cacheManager.getAddress();
            if (addr != null) {
                nodeName = addr.toString();
                siteName = cacheManager.getCacheManagerConfiguration().transport().siteId();
                if (siteName == null) {
                    siteName = config.get("siteName");
                }
            } else {
                nodeName = System.getProperty(InfinispanConnectionProvider.JBOSS_NODE_NAME);
                siteName = config.get("siteName");
            }
            if (nodeName == null || nodeName.equals("localhost")) {
                isGeneratedNodeName = true;
                nodeName = generateNodeName();
            }
        } else {
            boolean clustered = config.getBoolean("clustered", false);

            nodeName = config.get("nodeName", System.getProperty(InfinispanConnectionProvider.JBOSS_NODE_NAME));
            if (nodeName != null && nodeName.isEmpty()) {
                nodeName = null;
            }

            siteName = config.get("siteName");
            if (siteName != null && siteName.isEmpty()) {
                siteName = null;
            }

            if (nodeName == null) {
                if (!clustered) {
                    isGeneratedNodeName = true;
                    nodeName = generateNodeName();
                } else {
                    throw new IllegalStateException("You must set jboss.node.name if you use clustered mode for InfinispanConnectionProvider");
                }
            }
        }

        this.myNodeName = nodeName;
        this.mySiteName = siteName;
        this.isGeneratedNodeName = isGeneratedNodeName;
    }


    private String generateNodeName() {
        return InfinispanConnectionProvider.NODE_PREFIX + new SecureRandom().nextInt(1000000);
    }


    public String getMyNodeName() {
        return myNodeName;
    }

    public String getMySiteName() {
        return mySiteName;
    }


    @Override
    public String toString() {
        return String.format("Node name: %s, Site name: %s", myNodeName, mySiteName);
    }


    /**
     * True if I am primary owner of the key in case of distributed caches. In case of local caches, always return true
     */
    public boolean amIOwner(Cache<?, ?> cache, Object key) {
        Address myAddress = cache.getCacheManager().getAddress();
        Address objectOwnerAddress = getOwnerAddress(cache, key);

        // NOTE: For scattered caches, this will always return true, which may not be correct. Need to review this if we add support for scattered caches
        return Objects.equals(myAddress, objectOwnerAddress);
    }


    /**
     * Get route to be used as the identifier for sticky session. Return null if I am not able to find the appropriate route (or in case of local mode)
     */
    public String getRouteName(Cache<?, ?> cache, Object key) {
        if (cache.getCacheConfiguration().clustering().cacheMode().isClustered() && isGeneratedNodeName) {
            logger.warn("Clustered configuration used, but node name is not properly set. Make sure to start server with jboss.node.name property identifying cluster node");
        }

        if (isGeneratedNodeName) {
            return null;
        }

        // Impl based on Wildfly sticky session algorithm for generating routes ( org.wildfly.clustering.web.infinispan.session.InfinispanRouteLocator )
        Address address = getOwnerAddress(cache, key);

        // Local mode
        if (address == null ||  (address == LocalModeAddress.INSTANCE)) {
            return myNodeName;
        }

        org.jgroups.Address jgroupsAddress = toJGroupsAddress(address);
        String name = NameCache.get(jgroupsAddress);

        // If no logical name exists, create one using physical address
        if (name == null) {

            var transport = GlobalComponentRegistry.componentOf(cache.getCacheManager(), Transport.class);
            var channel = ((JGroupsTransport) transport).getChannel();
            var ipAddress = (IpAddress) channel.getProtocolStack().getTransport().localPhysicalAddress();
            // Physical address might be null if node is no longer a member of the cluster.
            // This seems broken, it is returning the IP/PORT for JGroups!?
            InetSocketAddress socketAddress = (ipAddress != null) ? new InetSocketAddress(ipAddress.getIpAddress(), ipAddress.getPort()) : new InetSocketAddress(0);
            name = String.format("%s:%s", socketAddress.getHostString(), socketAddress.getPort());

            logger.debugf("Address not found in NameCache. Fallback to %s", name);
        }

        return name;
    }


    private Address getOwnerAddress(Cache<?, ?> cache, Object key) {
        DistributionManager dist = cache.getAdvancedCache().getDistributionManager();
        return dist == null ? cache.getCacheManager().getAddress() : dist.getCacheTopology().getDistribution(key).primary();
    }


    // See org.wildfly.clustering.server.group.CacheGroup
    private static org.jgroups.Address toJGroupsAddress(Address address) {
        if ((address == null) || (address == LocalModeAddress.INSTANCE)) return null;
        if (address instanceof JGroupsAddress jgroupsAddress) {
            return jgroupsAddress.getJGroupsAddress();
        }
        throw new IllegalArgumentException(address.toString());
    }


}
