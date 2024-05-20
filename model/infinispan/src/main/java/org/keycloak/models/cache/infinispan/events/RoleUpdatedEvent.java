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

package org.keycloak.models.cache.infinispan.events;

import java.util.Objects;
import java.util.Set;

import org.keycloak.models.cache.infinispan.RealmCacheManager;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallUtil;
import org.infinispan.commons.marshall.SerializeWith;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@SerializeWith(RoleUpdatedEvent.ExternalizerImpl.class)
public class RoleUpdatedEvent extends InvalidationEvent implements RealmCacheInvalidationEvent {

    private String roleId;
    private String roleName;
    private String containerId;

    public static RoleUpdatedEvent create(String roleId, String roleName, String containerId) {
        RoleUpdatedEvent event = new RoleUpdatedEvent();
        event.roleId = roleId;
        event.roleName = roleName;
        event.containerId = containerId;
        return event;
    }

    @Override
    public String getId() {
        return roleId;
    }

    @Override
    public String toString() {
        return String.format("RoleUpdatedEvent [ roleId=%s, roleName=%s, containerId=%s ]", roleId, roleName, containerId);
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        realmCache.roleUpdated(containerId, roleName, invalidations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RoleUpdatedEvent that = (RoleUpdatedEvent) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(roleName, that.roleName) && Objects.equals(containerId, that.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), roleId, roleName, containerId);
    }

    public static class ExternalizerImpl implements Externalizer<RoleUpdatedEvent> {

        private static final int VERSION_1 = 1;

        @Override
        public void writeObject(ObjectOutput output, RoleUpdatedEvent obj) throws IOException {
            output.writeByte(VERSION_1);

            MarshallUtil.marshallString(obj.roleId, output);
            MarshallUtil.marshallString(obj.roleName, output);
            MarshallUtil.marshallString(obj.containerId, output);
        }

        @Override
        public RoleUpdatedEvent readObject(ObjectInput input) throws IOException, ClassNotFoundException {
            switch (input.readByte()) {
                case VERSION_1:
                    return readObjectVersion1(input);
                default:
                    throw new IOException("Unknown version");
            }
        }

        public RoleUpdatedEvent readObjectVersion1(ObjectInput input) throws IOException, ClassNotFoundException {
            RoleUpdatedEvent res = new RoleUpdatedEvent();
            res.roleId = MarshallUtil.unmarshallString(input);
            res.roleName = MarshallUtil.unmarshallString(input);
            res.containerId = MarshallUtil.unmarshallString(input);

            return res;
        }
    }
}
