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

package org.keycloak.models.jpa.session;

import jakarta.persistence.Version;
import org.keycloak.storage.jpa.KeyUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@NamedQueries({
        @NamedQuery(name="deleteUserSessionsByRealm", query="delete from PersistentUserSessionEntity sess where sess.realmId = :realmId"),
        @NamedQuery(name="deleteUserSessionsByRealmSessionType", query="delete from PersistentUserSessionEntity sess where sess.realmId = :realmId and sess.offline = :offline"),
        @NamedQuery(name="deleteUserSessionsByUser", query="delete from PersistentUserSessionEntity sess where sess.userId = :userId"),
        @NamedQuery(name="deleteExpiredUserSessions", query="delete from PersistentUserSessionEntity sess where sess.realmId = :realmId AND sess.offline = :offline AND sess.lastSessionRefresh < :lastSessionRefresh"),
        @NamedQuery(name="updateUserSessionLastSessionRefresh", query="update PersistentUserSessionEntity sess set lastSessionRefresh = :lastSessionRefresh where sess.realmId = :realmId" +
                " AND sess.offline = :offline AND sess.userSessionId IN (:userSessionIds)"),
        @NamedQuery(name="findUserSessionsCount", query="select count(sess) from PersistentUserSessionEntity sess where sess.offline = :offline"),
        @NamedQuery(name="findUserSessionsOrderedById", query="select sess from PersistentUserSessionEntity sess, RealmEntity realm where realm.id = sess.realmId AND sess.offline = :offline" +
                " AND sess.userSessionId > :lastSessionId" +
                " order by sess.userSessionId"),
        @NamedQuery(name="findUserSession", query="select sess from PersistentUserSessionEntity sess where sess.offline = :offline" +
                " AND sess.userSessionId = :userSessionId AND sess.realmId = :realmId"),
        @NamedQuery(name="findUserSessionsByUserId", query="select sess from PersistentUserSessionEntity sess where sess.offline = :offline" +
                " AND sess.realmId = :realmId AND sess.userId = :userId ORDER BY sess.userSessionId"),
        @NamedQuery(name="findUserSessionsByBrokerSessionId", query="select sess from PersistentUserSessionEntity sess where sess.brokerSessionId = :brokerSessionId" +
                " AND sess.realmId = :realmId AND sess.offline = :offline ORDER BY sess.userSessionId"),
        @NamedQuery(name="findUserSessionsByClientId", query="SELECT sess FROM PersistentUserSessionEntity sess INNER JOIN PersistentClientSessionEntity clientSess " +
                " ON sess.userSessionId = clientSess.userSessionId AND sess.offline = clientSess.offline AND clientSess.clientId = :clientId WHERE sess.offline = :offline " +
                " AND sess.realmId = :realmId ORDER BY sess.userSessionId"),
        @NamedQuery(name="findUserSessionsByExternalClientId", query="SELECT sess FROM PersistentUserSessionEntity sess INNER JOIN PersistentClientSessionEntity clientSess " +
                " ON sess.userSessionId = clientSess.userSessionId AND clientSess.clientStorageProvider = :clientStorageProvider AND sess.offline = clientSess.offline AND clientSess.externalClientId = :externalClientId WHERE sess.offline = :offline " +
                " AND sess.realmId = :realmId ORDER BY sess.userSessionId"),
        @NamedQuery(name="findClientSessionsClientIds", query="SELECT clientSess.clientId, clientSess.externalClientId, clientSess.clientStorageProvider, count(clientSess)" +
                " FROM PersistentClientSessionEntity clientSess INNER JOIN PersistentUserSessionEntity sess ON clientSess.userSessionId = sess.userSessionId AND sess.offline = clientSess.offline" +
                " WHERE sess.offline = :offline AND sess.realmId = :realmId " +
                " GROUP BY clientSess.clientId, clientSess.externalClientId, clientSess.clientStorageProvider")

})
@Table(name="OFFLINE_USER_SESSION")
@Entity
@IdClass(PersistentUserSessionEntity.Key.class)
public class PersistentUserSessionEntity {

    @Id
    @Column(name="USER_SESSION_ID", length = 36)
    protected String userSessionId;

    @Column(name = "REALM_ID", length = 36)
    protected String realmId;

    @Column(name="USER_ID", length = 255)
    protected String userId;

    @Column(name = "CREATED_ON")
    protected int createdOn;

    @Column(name = "LAST_SESSION_REFRESH")
    protected int lastSessionRefresh;

    @Column(name = "BROKER_SESSION_ID")
    protected String brokerSessionId;

    @Version
    private int version;

    @Id
    @Column(name = "OFFLINE_FLAG")
    protected String offline;

    @Column(name="DATA")
    protected String data;

    public String getUserSessionId() {
        return userSessionId;
    }

    public void setUserSessionId(String userSessionId) {
        this.userSessionId = userSessionId;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        KeyUtils.assertValidKey(userId);
        this.userId = userId;
    }

    public int getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(int createdOn) {
        this.createdOn = createdOn;
    }

    public int getLastSessionRefresh() {
        return lastSessionRefresh;
    }

    public void setLastSessionRefresh(int lastSessionRefresh) {
        this.lastSessionRefresh = lastSessionRefresh;
    }

    public String getOffline() {
        return offline;
    }

    public void setOffline(String offline) {
        this.offline = offline;
    }

    public String getBrokerSessionId() {
        return brokerSessionId;
    }

    public void setBrokerSessionId(String brokerSessionId) {
        this.brokerSessionId = brokerSessionId;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public static class Key implements Serializable {

        protected String userSessionId;

        protected String offline;

        public Key() {
        }

        public Key(String userSessionId, String offline) {
            this.userSessionId = userSessionId;
            this.offline = offline;
        }

        public String getUserSessionId() {
            return userSessionId;
        }

        public String getOffline() {
            return offline;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (this.userSessionId != null ? !this.userSessionId.equals(key.userSessionId) : key.userSessionId != null) return false;
            if (this.offline != null ? !this.offline.equals(key.offline) : key.offline != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = this.userSessionId != null ? this.userSessionId.hashCode() : 0;
            result = 31 * result + (this.offline != null ? this.offline.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "PersistentUserSessionEntity$Key [" +
                   "userSessionId='" + userSessionId + '\'' +
                   ", offline='" + offline + '\'' +
                   ']';
        }
    }
}
