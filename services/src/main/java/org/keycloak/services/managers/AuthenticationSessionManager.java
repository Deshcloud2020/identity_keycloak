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

package org.keycloak.services.managers;

import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.cookie.CookieProvider;
import org.keycloak.cookie.CookieType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.SessionExpiration;
import org.keycloak.protocol.RestartLoginCookie;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.sessions.StickySessionEncoderProvider;


/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class AuthenticationSessionManager {

    private static final Logger log = Logger.getLogger(AuthenticationSessionManager.class);

    private final KeycloakSession session;

    public AuthenticationSessionManager(KeycloakSession session) {
        this.session = session;
    }


    /**
     * Creates a fresh authentication session for the given realm . Optionally sets the browser
     * authentication session cookie with the ID of the new session.
     * @param realm
     * @param browserCookie Set the cookie in the browser for the
     * @return
     */
    public RootAuthenticationSessionModel createAuthenticationSession(RealmModel realm, boolean browserCookie) {
        RootAuthenticationSessionModel rootAuthSession = session.authenticationSessions().createRootAuthenticationSession(realm);

        if (browserCookie) {
            setAuthSessionCookie(rootAuthSession.getId());
        }

        return rootAuthSession;
    }


    public RootAuthenticationSessionModel getCurrentRootAuthenticationSession(RealmModel realm) {
        String oldEncodedId = getAuthSessionCookies(realm);
        if (oldEncodedId == null) {
            return null;
        }

        AuthSessionId authSessionId = decodeAuthSessionId(oldEncodedId);
        String sessionId = authSessionId.getDecodedId();

        RootAuthenticationSessionModel rootAuthSession = session.authenticationSessions().getRootAuthenticationSession(realm, sessionId);

        if (rootAuthSession != null) {
            reencodeAuthSessionCookie(oldEncodedId, authSessionId, realm);
            return rootAuthSession;
        } else {
            return null;
        }
    }

    /**
     * Returns current authentication session if it exists, otherwise returns {@code null}.
     * @param realm
     * @return
     */
    public AuthenticationSessionModel getCurrentAuthenticationSession(RealmModel realm, ClientModel client, String tabId) {
        String oldEncodedId = getAuthSessionCookies(realm);
        if (oldEncodedId == null) {
            return null;
        }

        AuthSessionId authSessionId = decodeAuthSessionId(oldEncodedId);
        String sessionId = authSessionId.getDecodedId();

        AuthenticationSessionModel authSession = getAuthenticationSessionByIdAndClient(realm, sessionId, client, tabId);

        if (authSession != null) {
            reencodeAuthSessionCookie(oldEncodedId, authSessionId, realm);
            return authSession;
        } else {
            return null;
        }
    }


    /**
     * @param authSessionId decoded authSessionId (without route info attached)
     */
    public void setAuthSessionCookie(String authSessionId) {
        StickySessionEncoderProvider encoder = session.getProvider(StickySessionEncoderProvider.class);
        String encodedAuthSessionId = encoder.encodeSessionId(authSessionId);

        session.getProvider(CookieProvider.class).set(CookieType.AUTH_SESSION_ID, encodedAuthSessionId);

        log.debugf("Set AUTH_SESSION_ID cookie with value %s", encodedAuthSessionId);
    }


    /**
     *
     * @param encodedAuthSessionId encoded ID with attached route in cluster environment (EG. "5e161e00-d426-4ea6-98e9-52eb9844e2d7.node1" )
     * @return object with decoded and actually encoded authSessionId
     */
    AuthSessionId decodeAuthSessionId(String encodedAuthSessionId) {
        log.debugf("Found AUTH_SESSION_ID cookie with value %s", encodedAuthSessionId);
        StickySessionEncoderProvider encoder = session.getProvider(StickySessionEncoderProvider.class);
        String decodedAuthSessionId = encoder.decodeSessionId(encodedAuthSessionId);
        String reencoded = encoder.encodeSessionId(decodedAuthSessionId);

        return new AuthSessionId(decodedAuthSessionId, reencoded);
    }


    void reencodeAuthSessionCookie(String oldEncodedAuthSessionId, AuthSessionId newAuthSessionId, RealmModel realm) {
        if (!oldEncodedAuthSessionId.equals(newAuthSessionId.getEncodedId())) {
            log.debugf("Route changed. Will update authentication session cookie. Old: '%s', New: '%s'", oldEncodedAuthSessionId,
                    newAuthSessionId.getEncodedId());
            setAuthSessionCookie(newAuthSessionId.getDecodedId());
        }
    }


    /**
     * @param realm
     * @return the value of the AUTH_SESSION_ID cookie. It is assumed that values could be encoded with route added (EG. "5e161e00-d426-4ea6-98e9-52eb9844e2d7.node1" )
     */
    String getAuthSessionCookies(RealmModel realm) {
        String oldEncodedId = session.getProvider(CookieProvider.class).get(CookieType.AUTH_SESSION_ID);
        if (oldEncodedId == null || oldEncodedId.isEmpty()) {
            return null;
        }

        StickySessionEncoderProvider encoder = session.getProvider(StickySessionEncoderProvider.class);
        // in case the id is encoded with a route when running in a cluster
        String decodedId = encoder.decodeSessionId(oldEncodedId);
        // we can't blindly trust the cookie and assume it is valid and referencing a valid root auth session
        // but make sure the root authentication session actually exists
        // without this check there is a risk of resolving user sessions from invalid root authentication sessions as they share the same id
        RootAuthenticationSessionModel rootAuthenticationSession = session.authenticationSessions().getRootAuthenticationSession(realm, decodedId);
        return rootAuthenticationSession != null ? oldEncodedId : null;
    }


    public void removeAuthenticationSession(RealmModel realm, AuthenticationSessionModel authSession, boolean expireRestartCookie) {
        RootAuthenticationSessionModel rootAuthSession = authSession.getParentSession();

        log.debugf("Removing root authSession '%s'. Expire restart cookie: %b", rootAuthSession.getId(), expireRestartCookie);
        session.authenticationSessions().removeRootAuthenticationSession(realm, rootAuthSession);

        // expire restart cookie
        if (expireRestartCookie) {
            RestartLoginCookie.expireRestartCookie(session);

            // With browser session, this makes sure that info/error pages will be rendered correctly when locale is changed on them
            session.getProvider(LoginFormsProvider.class).setDetachedAuthSession();
        }
    }

    /**
     * Remove authentication session from root session. Possibly remove whole root authentication session if there are no other browser tabs
     * @param realm
     * @param authSession
     * @return true if whole root authentication session was removed. False just if single tab was removed
     */
    public boolean removeTabIdInAuthenticationSession(RealmModel realm, AuthenticationSessionModel authSession) {
        RootAuthenticationSessionModel rootAuthSession = authSession.getParentSession();
        rootAuthSession.removeAuthenticationSessionByTabId(authSession.getTabId());
        if (rootAuthSession.getAuthenticationSessions().isEmpty()) {
            // no more tabs, remove the session completely
            removeAuthenticationSession(realm, authSession, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * This happens when one browser tab successfully finished authentication (including required actions and consent screen if applicable)
     * Just authenticationSession of the current browser tab is removed from "root authentication session" and other tabs are kept, so
     * authentication can be automatically finished in other browser tabs (typically with authChecker.js javascript)
     *
     * @param realm
     * @param authSession
     */
    public void updateAuthenticationSessionAfterSuccessfulAuthentication(RealmModel realm, AuthenticationSessionModel authSession) {
        boolean removedRootAuthSession = removeTabIdInAuthenticationSession(realm, authSession);
        if (!removedRootAuthSession) {
            if(realm.getSsoSessionIdleTimeout() < SessionExpiration.getAuthSessionLifespan(realm) && realm.getSsoSessionMaxLifespan() < SessionExpiration.getAuthSessionLifespan(realm)) {
                removeAuthenticationSession(realm, authSession, true);
            }
            else {
                RootAuthenticationSessionModel rootAuthSession = authSession.getParentSession();

                // 1 minute by default. Same timeout, which is used for client to complete "authorization code" flow
                // Very short timeout should be OK as when this cookie is set, other existing browser tabs are supposed to be refreshed immediately by JS script authChecker.js
                // and login user automatically. No need to have authenticationSession and cookie living any longer
                int authSessionExpiresIn = realm.getAccessCodeLifespan();

                // Set timestamp to the past to make sure that authSession is scheduled for expiration in "authSessionExpiresIn" seconds
                int authSessionExpirationTime = Time.currentTime() - SessionExpiration.getAuthSessionLifespan(realm) + authSessionExpiresIn;
                rootAuthSession.setTimestamp(authSessionExpirationTime);

                log.tracef("Removed authentication session of root session '%s' with tabId '%s'. But there are remaining tabs in the root session. Root authentication session will expire in %d seconds", rootAuthSession.getId(), authSession.getTabId(), authSessionExpiresIn);
            }
        }
    }

    // Check to see if we already have authenticationSession with same ID
    public UserSessionModel getUserSession(AuthenticationSessionModel authSession) {
        return session.sessions().getUserSession(authSession.getRealm(), authSession.getParentSession().getId());
    }


    // Don't look at cookie. Just lookup authentication session based on the ID and client. Return null if not found
    public AuthenticationSessionModel getAuthenticationSessionByIdAndClient(RealmModel realm, String authSessionId, ClientModel client, String tabId) {
        RootAuthenticationSessionModel rootAuthSession = session.authenticationSessions().getRootAuthenticationSession(realm, authSessionId);
        return rootAuthSession==null ? null : rootAuthSession.getAuthenticationSession(client, tabId);
    }
}
