/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.protocol.oidc.grants;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.common.ClientConnection;
import org.keycloak.common.Profile;
import org.keycloak.common.VerificationException;
import org.keycloak.constants.AdapterConstants;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.http.HttpResponse;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCAdvancedConfigWrapper;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.protocol.oidc.utils.AuthorizeClientUtil;
import org.keycloak.rar.AuthorizationRequestContext;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.dpop.DPoP;
import org.keycloak.services.CorsErrorResponseException;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.cors.Cors;
import org.keycloak.services.util.AuthorizationContextUtil;
import org.keycloak.services.util.DPoPUtil;
import org.keycloak.services.util.MtlsHoKTokenUtil;
import org.keycloak.util.TokenUtil;

/**
 * Base class for OAuth 2.0 grant types
 *
 * @author <a href="mailto:demetrio@carretti.pro">Dmitry Telegin</a> (et al.)
 */
public abstract class OAuth2GrantTypeBase implements OAuth2GrantType {

    private static final Logger logger = Logger.getLogger(OAuth2GrantTypeBase.class);

    protected OAuth2GrantType.Context context;

    protected KeycloakSession session;
    protected RealmModel realm;
    protected ClientModel client;
    protected OIDCAdvancedConfigWrapper clientConfig;
    protected ClientConnection clientConnection;
    protected Map<String, String> clientAuthAttributes;
    protected MultivaluedMap<String, String> formParams;
    protected EventBuilder event;
    protected Cors cors;
    protected TokenManager tokenManager;
    protected DPoP dPoP;
    protected HttpRequest request;
    protected HttpResponse response;
    protected HttpHeaders headers;

    protected void setContext(Context context) {
        this.context = context;
        this.session = context.session;
        this.realm = context.realm;
        this.client = context.client;
        this.clientConfig = (OIDCAdvancedConfigWrapper) context.clientConfig;
        this.clientConnection = context.clientConnection;
        this.clientAuthAttributes = context.clientAuthAttributes;
        this.request = context.request;
        this.response = context.response;
        this.headers = context.headers;
        this.formParams = context.formParams;
        this.event = context.event;
        this.cors = context.cors;
        this.tokenManager = (TokenManager) context.tokenManager;
        this.dPoP = context.dPoP;
    }

    protected Response createTokenResponse(UserModel user, UserSessionModel userSession, ClientSessionContext clientSessionCtx,
        String scopeParam, boolean code, Function<TokenManager.AccessTokenResponseBuilder, ClientPolicyContext> clientPolicyContextGenerator) {
        AccessToken token = tokenManager.createClientAccessToken(session, realm, client, user, userSession, clientSessionCtx);

        TokenManager.AccessTokenResponseBuilder responseBuilder = tokenManager
            .responseBuilder(realm, client, event, session, userSession, clientSessionCtx).accessToken(token);
        boolean useRefreshToken = clientConfig.isUseRefreshToken();
        if (useRefreshToken) {
            responseBuilder.generateRefreshToken();
        }

        checkAndBindMtlsHoKToken(responseBuilder, useRefreshToken);
        checkAndBindDPoPToken(responseBuilder, useRefreshToken && client.isPublicClient(), Profile.isFeatureEnabled(Profile.Feature.DPOP));

        if (TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken().generateAccessTokenHash();
        }

        if (clientPolicyContextGenerator != null) {
            try {
                session.clientPolicy().triggerOnEvent(clientPolicyContextGenerator.apply(responseBuilder));
            } catch (ClientPolicyException cpe) {
                event.detail(Details.REASON, cpe.getErrorDetail());
                event.error(cpe.getError());
                throw new CorsErrorResponseException(cors, cpe.getError(), cpe.getErrorDetail(), cpe.getErrorStatus());
            }
        }

        AccessTokenResponse res = null;
        if (code) {
            try {
                res = responseBuilder.build();
            } catch (RuntimeException re) {
                if ("can not get encryption KEK".equals(re.getMessage())) {
                    throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_REQUEST,
                        "can not get encryption KEK", Response.Status.BAD_REQUEST);
                } else {
                    throw re;
                }
            }
        } else {
            res = responseBuilder.build();
        }

        event.success();

        return cors.add(Response.ok(res).type(MediaType.APPLICATION_JSON_TYPE));
    }

    protected void checkAndBindMtlsHoKToken(TokenManager.AccessTokenResponseBuilder responseBuilder, boolean useRefreshToken) {
        // KEYCLOAK-6771 Certificate Bound Token
        // https://tools.ietf.org/html/draft-ietf-oauth-mtls-08#section-3
        if (clientConfig.isUseMtlsHokToken()) {
            AccessToken.Confirmation confirmation = MtlsHoKTokenUtil.bindTokenWithClientCertificate(request, session);
            if (confirmation != null) {
                responseBuilder.getAccessToken().setConfirmation(confirmation);
                if (useRefreshToken) {
                    responseBuilder.getRefreshToken().setConfirmation(confirmation);
                }
            } else {
                String errorMessage = "Client Certification missing for MTLS HoK Token Binding";
                event.detail(Details.REASON, errorMessage);
                event.error(Errors.INVALID_REQUEST);
                throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_REQUEST,
                        errorMessage, Response.Status.BAD_REQUEST);
            }
        }
    }

    protected void checkAndBindDPoPToken(TokenManager.AccessTokenResponseBuilder responseBuilder, boolean useRefreshToken, boolean isDPoPSupported) {
        if (!isDPoPSupported) return;

        if (clientConfig.isUseDPoP() || dPoP != null) {
            DPoPUtil.bindToken(responseBuilder.getAccessToken(), dPoP);
            responseBuilder.getAccessToken().type(DPoPUtil.DPOP_TOKEN_TYPE);
            responseBuilder.responseTokenType(DPoPUtil.DPOP_TOKEN_TYPE);

            // Bind refresh tokens for public clients, See "Section 5. DPoP Access Token Request" from DPoP specification
            if (useRefreshToken) {
                DPoPUtil.bindToken(responseBuilder.getRefreshToken(), dPoP);
            }
        }
    }

    protected void updateClientSession(AuthenticatedClientSessionModel clientSession) {

        if(clientSession == null) {
            ServicesLogger.LOGGER.clientSessionNull();
            return;
        }

        String adapterSessionId = formParams.getFirst(AdapterConstants.CLIENT_SESSION_STATE);
        if (adapterSessionId != null) {
            String adapterSessionHost = formParams.getFirst(AdapterConstants.CLIENT_SESSION_HOST);
            logger.debugf("Adapter Session '%s' saved in ClientSession for client '%s'. Host is '%s'", adapterSessionId, client.getClientId(), adapterSessionHost);

            String oldClientSessionState = clientSession.getNote(AdapterConstants.CLIENT_SESSION_STATE);
            if (!adapterSessionId.equals(oldClientSessionState)) {
                clientSession.setNote(AdapterConstants.CLIENT_SESSION_STATE, adapterSessionId);
            }

            String oldClientSessionHost = clientSession.getNote(AdapterConstants.CLIENT_SESSION_HOST);
            if (!Objects.equals(adapterSessionHost, oldClientSessionHost)) {
                clientSession.setNote(AdapterConstants.CLIENT_SESSION_HOST, adapterSessionHost);
            }
        }
    }

    protected void updateUserSessionFromClientAuth(UserSessionModel userSession) {
        for (Map.Entry<String, String> attr : clientAuthAttributes.entrySet()) {
            userSession.setNote(attr.getKey(), attr.getValue());
        }
    }

    protected void checkAndRetrieveDPoPProof(boolean isDPoPSupported) {
        if (!isDPoPSupported) return;

        if (clientConfig.isUseDPoP() || request.getHttpHeaders().getHeaderString(DPoPUtil.DPOP_HTTP_HEADER) != null) {
            try {
                dPoP = new DPoPUtil.Validator(session).request(request).uriInfo(session.getContext().getUri()).validate();
                session.setAttribute(DPoPUtil.DPOP_SESSION_ATTRIBUTE, dPoP);
            } catch (VerificationException ex) {
                event.detail(Details.REASON, ex.getMessage());
                event.error(Errors.INVALID_DPOP_PROOF);
                throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_DPOP_PROOF, ex.getMessage(), Response.Status.BAD_REQUEST);
            }
        }
    }

    protected String getRequestedScopes() {
        String scope = formParams.getFirst(OAuth2Constants.SCOPE);

        boolean validScopes;
        if (Profile.isFeatureEnabled(Profile.Feature.DYNAMIC_SCOPES)) {
            AuthorizationRequestContext authorizationRequestContext = AuthorizationContextUtil.getAuthorizationRequestContextFromScopes(session, scope);
            validScopes = TokenManager.isValidScope(scope, authorizationRequestContext, client);
        } else {
            validScopes = TokenManager.isValidScope(scope, client);
        }

        if (!validScopes) {
            String errorMessage = "Invalid scopes: " + scope;
            event.detail(Details.REASON, errorMessage);
            event.error(Errors.INVALID_REQUEST);
            throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_SCOPE, errorMessage, Response.Status.BAD_REQUEST);
        }

        return scope;
    }

    protected void checkClient() {
        AuthorizeClientUtil.ClientAuthResult clientAuth = AuthorizeClientUtil.authorizeClient(session, event, cors);
        client = clientAuth.getClient();
        clientAuthAttributes = clientAuth.getClientAuthAttributes();
        clientConfig = OIDCAdvancedConfigWrapper.fromClientModel(client);

        cors.allowedOrigins(session, client);

        if (client.isBearerOnly()) {
            throw new CorsErrorResponseException(cors, OAuthErrorException.INVALID_CLIENT, "Bearer-only not allowed", Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public void close() {
    }

}
