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

package org.keycloak.models.cache.infinispan.entities;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.keycloak.common.enums.SslRequired;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.CibaConfig;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.OAuth2DeviceConfig;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.ParConfig;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.RequiredCredentialModel;
import org.keycloak.models.WebAuthnPolicy;
import org.keycloak.models.cache.infinispan.DefaultLazyLoader;
import org.keycloak.models.cache.infinispan.LazyLoader;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class CachedRealm extends AbstractExtendableRevisioned {

    protected String name;
    protected String displayName;
    protected String displayNameHtml;
    protected boolean enabled;
    protected SslRequired sslRequired;
    protected boolean registrationAllowed;
    protected boolean registrationEmailAsUsername;
    protected boolean rememberMe;
    protected boolean verifyEmail;
    protected boolean loginWithEmailAllowed;
    protected boolean duplicateEmailsAllowed;
    protected boolean resetPasswordAllowed;
    protected boolean identityFederationEnabled;
    protected boolean editUsernameAllowed;
    //--- brute force settings
    protected boolean bruteForceProtected;
    protected boolean permanentLockout;
    protected int maxTemporaryLockouts;
    protected int maxFailureWaitSeconds;
    protected int minimumQuickLoginWaitSeconds;
    protected int waitIncrementSeconds;
    protected long quickLoginCheckMilliSeconds;
    protected int maxDeltaTimeSeconds;
    protected int failureFactor;
    //--- end brute force settings

    protected String defaultSignatureAlgorithm;
    protected boolean revokeRefreshToken;
    protected int refreshTokenMaxReuse;
    protected int ssoSessionIdleTimeout;
    protected int ssoSessionMaxLifespan;
    protected int ssoSessionIdleTimeoutRememberMe;
    protected int ssoSessionMaxLifespanRememberMe;
    protected int offlineSessionIdleTimeout;
    // KEYCLOAK-7688 Offline Session Max for Offline Token
    protected boolean offlineSessionMaxLifespanEnabled;
    protected int offlineSessionMaxLifespan;
    protected int clientSessionIdleTimeout;
    protected int clientSessionMaxLifespan;
    protected int clientOfflineSessionIdleTimeout;
    protected int clientOfflineSessionMaxLifespan;
    protected int accessTokenLifespan;
    protected int accessTokenLifespanForImplicitFlow;
    protected int accessCodeLifespan;
    protected int accessCodeLifespanUserAction;
    protected int accessCodeLifespanLogin;
    protected LazyLoader<RealmModel, OAuth2DeviceConfig> deviceConfig;
    protected LazyLoader<RealmModel, CibaConfig> cibaConfig;
    protected LazyLoader<RealmModel, ParConfig> parConfig;
    protected int actionTokenGeneratedByAdminLifespan;
    protected int actionTokenGeneratedByUserLifespan;
    protected int notBefore;
    protected PasswordPolicy passwordPolicy;
    protected OTPPolicy otpPolicy;
    protected WebAuthnPolicy webAuthnPolicy;
    protected WebAuthnPolicy webAuthnPasswordlessPolicy;

    protected String loginTheme;
    protected String accountTheme;
    protected String adminTheme;
    protected String emailTheme;
    protected String masterAdminClient;

    protected List<RequiredCredentialModel> requiredCredentials;
    protected MultivaluedHashMap<String, ComponentModel> componentsByParent = new MultivaluedHashMap<>();
    protected MultivaluedHashMap<String, ComponentModel> componentsByParentAndType = new MultivaluedHashMap<>();
    protected Map<String, ComponentModel> components;
    protected List<IdentityProviderModel> identityProviders;

    protected Map<String, String> browserSecurityHeaders;
    protected Map<String, String> smtpConfig;
    protected Map<String, AuthenticationFlowModel> authenticationFlows = new HashMap<>();
    protected List<AuthenticationFlowModel> authenticationFlowList;
    protected Map<String, AuthenticatorConfigModel> authenticatorConfigs;
    protected Map<String, RequiredActionProviderModel> requiredActionProviders = new HashMap<>();
    protected List<RequiredActionProviderModel> requiredActionProviderList;
    protected Map<String, RequiredActionProviderModel> requiredActionProvidersByAlias = new HashMap<>();
    protected MultivaluedHashMap<String, AuthenticationExecutionModel> authenticationExecutions = new MultivaluedHashMap<>();
    protected Map<String, AuthenticationExecutionModel> executionsById = new HashMap<>();
    protected Map<String, AuthenticationExecutionModel> executionsByFlowId = new HashMap<>();

    protected AuthenticationFlowModel browserFlow;
    protected AuthenticationFlowModel registrationFlow;
    protected AuthenticationFlowModel orgRegistrationFlow;
    protected AuthenticationFlowModel directGrantFlow;
    protected AuthenticationFlowModel resetCredentialsFlow;
    protected AuthenticationFlowModel clientAuthenticationFlow;
    protected AuthenticationFlowModel dockerAuthenticationFlow;
    protected AuthenticationFlowModel firstBrokerLoginFlow;

    protected boolean eventsEnabled;
    protected long eventsExpiration;
    protected Set<String> eventsListeners;
    protected Set<String> enabledEventTypes;
    protected boolean adminEventsEnabled;
    protected boolean adminEventsDetailsEnabled;
    protected String defaultRoleId;
    private boolean allowUserManagedAccess;

    public Set<IdentityProviderMapperModel> getIdentityProviderMapperSet() {
        return identityProviderMapperSet;
    }

    protected List<String> defaultGroups;
    protected List<String> defaultDefaultClientScopes = new LinkedList<>();
    protected List<String> optionalDefaultClientScopes = new LinkedList<>();
    protected boolean internationalizationEnabled;
    protected Set<String> supportedLocales;
    protected String defaultLocale;
    protected MultivaluedHashMap<String, IdentityProviderMapperModel> identityProviderMappers = new MultivaluedHashMap<>();
    protected Set<IdentityProviderMapperModel> identityProviderMapperSet;

    protected Map<String, String> attributes;

    private Map<String, Integer> userActionTokenLifespans;

    protected Map<String, Map<String,String>> realmLocalizationTexts;

    public CachedRealm(Long revision, RealmModel model) {
        super(revision, model.getId());
        name = model.getName();
        displayName = model.getDisplayName();
        displayNameHtml = model.getDisplayNameHtml();
        enabled = model.isEnabled();
        allowUserManagedAccess = model.isUserManagedAccessAllowed();
        sslRequired = model.getSslRequired();
        registrationAllowed = model.isRegistrationAllowed();
        registrationEmailAsUsername = model.isRegistrationEmailAsUsername();
        rememberMe = model.isRememberMe();
        verifyEmail = model.isVerifyEmail();
        loginWithEmailAllowed = model.isLoginWithEmailAllowed();
        duplicateEmailsAllowed = model.isDuplicateEmailsAllowed();
        resetPasswordAllowed = model.isResetPasswordAllowed();
        identityFederationEnabled = model.isIdentityFederationEnabled();
        editUsernameAllowed = model.isEditUsernameAllowed();
        //--- brute force settings
        bruteForceProtected = model.isBruteForceProtected();
        permanentLockout = model.isPermanentLockout();
        maxTemporaryLockouts = model.getMaxTemporaryLockouts();
        maxFailureWaitSeconds = model.getMaxFailureWaitSeconds();
        minimumQuickLoginWaitSeconds = model.getMinimumQuickLoginWaitSeconds();
        waitIncrementSeconds = model.getWaitIncrementSeconds();
        quickLoginCheckMilliSeconds = model.getQuickLoginCheckMilliSeconds();
        maxDeltaTimeSeconds = model.getMaxDeltaTimeSeconds();
        failureFactor = model.getFailureFactor();
        //--- end brute force settings

        defaultSignatureAlgorithm = model.getDefaultSignatureAlgorithm();
        revokeRefreshToken = model.isRevokeRefreshToken();
        refreshTokenMaxReuse = model.getRefreshTokenMaxReuse();
        ssoSessionIdleTimeout = model.getSsoSessionIdleTimeout();
        ssoSessionMaxLifespan = model.getSsoSessionMaxLifespan();
        ssoSessionIdleTimeoutRememberMe = model.getSsoSessionIdleTimeoutRememberMe();
        ssoSessionMaxLifespanRememberMe = model.getSsoSessionMaxLifespanRememberMe();
        offlineSessionIdleTimeout = model.getOfflineSessionIdleTimeout();
        // KEYCLOAK-7688 Offline Session Max for Offline Token
        offlineSessionMaxLifespanEnabled = model.isOfflineSessionMaxLifespanEnabled();
        offlineSessionMaxLifespan = model.getOfflineSessionMaxLifespan();
        clientSessionIdleTimeout = model.getClientSessionIdleTimeout();
        clientSessionMaxLifespan = model.getClientSessionMaxLifespan();
        clientOfflineSessionIdleTimeout = model.getClientOfflineSessionIdleTimeout();
        clientOfflineSessionMaxLifespan = model.getClientOfflineSessionMaxLifespan();
        accessTokenLifespan = model.getAccessTokenLifespan();
        accessTokenLifespanForImplicitFlow = model.getAccessTokenLifespanForImplicitFlow();
        accessCodeLifespan = model.getAccessCodeLifespan();
        deviceConfig = new DefaultLazyLoader<>(OAuth2DeviceConfig::new, null);
        cibaConfig = new DefaultLazyLoader<>(CibaConfig::new, null);
        parConfig = new DefaultLazyLoader<>(ParConfig::new, null);
        accessCodeLifespanUserAction = model.getAccessCodeLifespanUserAction();
        accessCodeLifespanLogin = model.getAccessCodeLifespanLogin();
        actionTokenGeneratedByAdminLifespan = model.getActionTokenGeneratedByAdminLifespan();
        actionTokenGeneratedByUserLifespan = model.getActionTokenGeneratedByUserLifespan();
        notBefore = model.getNotBefore();
        passwordPolicy = model.getPasswordPolicy();
        otpPolicy = model.getOTPPolicy();
        webAuthnPolicy = model.getWebAuthnPolicy();
        webAuthnPasswordlessPolicy = model.getWebAuthnPolicyPasswordless();

        loginTheme = model.getLoginTheme();
        accountTheme = model.getAccountTheme();
        adminTheme = model.getAdminTheme();
        emailTheme = model.getEmailTheme();

        requiredCredentials = model.getRequiredCredentialsStream().collect(Collectors.toList());
        userActionTokenLifespans = Collections.unmodifiableMap(new HashMap<>(model.getUserActionTokenLifespans()));

        this.identityProviders = model.getIdentityProvidersStream().map(IdentityProviderModel::new)
                .collect(Collectors.toList());
        this.identityProviders = Collections.unmodifiableList(this.identityProviders);

        this.identityProviderMapperSet = model.getIdentityProviderMappersStream().collect(Collectors.toSet());
        for (IdentityProviderMapperModel mapper : identityProviderMapperSet) {
            identityProviderMappers.add(mapper.getIdentityProviderAlias(), mapper);
        }



        smtpConfig = model.getSmtpConfig();
        browserSecurityHeaders = model.getBrowserSecurityHeaders();

        eventsEnabled = model.isEventsEnabled();
        eventsExpiration = model.getEventsExpiration();
        eventsListeners = model.getEventsListenersStream().collect(Collectors.toSet());
        enabledEventTypes = model.getEnabledEventTypesStream().collect(Collectors.toSet());

        adminEventsEnabled = model.isAdminEventsEnabled();
        adminEventsDetailsEnabled = model.isAdminEventsDetailsEnabled();

        defaultRoleId = model.getDefaultRole().getId();
        ClientModel masterAdminClient = model.getMasterAdminClient();
        this.masterAdminClient = (masterAdminClient != null) ? masterAdminClient.getId() : null;

        cacheClientScopes(model);

        internationalizationEnabled = model.isInternationalizationEnabled();
        supportedLocales = model.getSupportedLocalesStream().collect(Collectors.toSet());
        defaultLocale = model.getDefaultLocale();
        authenticationFlowList = model.getAuthenticationFlowsStream().collect(Collectors.toList());
        for (AuthenticationFlowModel flow : authenticationFlowList) {
            this.authenticationFlows.put(flow.getId(), flow);
            authenticationExecutions.put(flow.getId(), new LinkedList<>());
            model.getAuthenticationExecutionsStream(flow.getId()).forEachOrdered(execution -> {
                authenticationExecutions.add(flow.getId(), execution);
                executionsById.put(execution.getId(), execution);
                if (execution.getFlowId() != null) {
                    executionsByFlowId.put(execution.getFlowId(), execution);
                }
            });
        }

        authenticatorConfigs = model.getAuthenticatorConfigsStream()
                .collect(Collectors.toMap(AuthenticatorConfigModel::getId, Function.identity()));
        requiredActionProviderList = model.getRequiredActionProvidersStream().collect(Collectors.toList());
        for (RequiredActionProviderModel action : requiredActionProviderList) {
            this.requiredActionProviders.put(action.getId(), action);
            requiredActionProvidersByAlias.put(action.getAlias(), action);
        }

        defaultGroups = model.getDefaultGroupsStream().map(GroupModel::getId).collect(Collectors.toList());

        browserFlow = model.getBrowserFlow();
        registrationFlow = model.getRegistrationFlow();
        directGrantFlow = model.getDirectGrantFlow();
        resetCredentialsFlow = model.getResetCredentialsFlow();
        clientAuthenticationFlow = model.getClientAuthenticationFlow();
        dockerAuthenticationFlow = model.getDockerAuthenticationFlow();
        firstBrokerLoginFlow = model.getFirstBrokerLoginFlow();

        model.getComponentsStream().forEach(component ->
            componentsByParentAndType.add(component.getParentId() + component.getProviderType(), component)
        );
        model.getComponentsStream().forEach(component ->
            componentsByParent.add(component.getParentId(), component)
        );
        components = model.getComponentsStream().collect(Collectors.toMap(component -> component.getId(), Function.identity()));

        try {
            attributes = model.getAttributes();
        } catch (UnsupportedOperationException ex) {
        }

        realmLocalizationTexts = model.getRealmLocalizationTexts();
    }

    protected void cacheClientScopes(RealmModel model) {
        defaultDefaultClientScopes = model.getDefaultClientScopesStream(true).map(ClientScopeModel::getId)
                .collect(Collectors.toList());
        optionalDefaultClientScopes = model.getDefaultClientScopesStream(false).map(ClientScopeModel::getId)
                .collect(Collectors.toList());
    }

    public String getMasterAdminClient() {
        return masterAdminClient;
    }

    public String getDefaultRoleId() {
        return defaultRoleId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameHtml() {
        return displayNameHtml;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SslRequired getSslRequired() {
        return sslRequired;
    }

    public boolean isRegistrationAllowed() {
        return registrationAllowed;
    }

    public boolean isRegistrationEmailAsUsername() {
        return registrationEmailAsUsername;
    }

    public boolean isRememberMe() {
        return this.rememberMe;
    }

    public boolean isBruteForceProtected() {
        return bruteForceProtected;
    }

    public boolean isPermanentLockout() {
        return permanentLockout;
    }

    public int getMaxTemporaryLockouts() {
        return maxTemporaryLockouts;
    }

    public int getMaxFailureWaitSeconds() {
        return this.maxFailureWaitSeconds;
    }

    public int getWaitIncrementSeconds() {
        return this.waitIncrementSeconds;
    }

    public int getMinimumQuickLoginWaitSeconds() {
        return this.minimumQuickLoginWaitSeconds;
    }

    public long getQuickLoginCheckMilliSeconds() {
        return quickLoginCheckMilliSeconds;
    }

    public int getMaxDeltaTimeSeconds() {
        return maxDeltaTimeSeconds;
    }

    public int getFailureFactor() {
        return failureFactor;
    }

    public boolean isVerifyEmail() {
        return verifyEmail;
    }
    
    public boolean isLoginWithEmailAllowed() {
        return loginWithEmailAllowed;
    }
    
    public boolean isDuplicateEmailsAllowed() {
        return duplicateEmailsAllowed;
    }

    public boolean isResetPasswordAllowed() {
        return resetPasswordAllowed;
    }

    public boolean isEditUsernameAllowed() {
        return editUsernameAllowed;
    }

    public String getDefaultSignatureAlgorithm() {
        return defaultSignatureAlgorithm;
    }

    public boolean isRevokeRefreshToken() {
        return revokeRefreshToken;
    }

    public int getRefreshTokenMaxReuse() {
        return refreshTokenMaxReuse;
    }

    public int getSsoSessionIdleTimeout() {
        return ssoSessionIdleTimeout;
    }

    public int getSsoSessionMaxLifespan() {
        return ssoSessionMaxLifespan;
    }

    public int getSsoSessionIdleTimeoutRememberMe() {
        return ssoSessionIdleTimeoutRememberMe;
    }

    public int getSsoSessionMaxLifespanRememberMe() {
        return ssoSessionMaxLifespanRememberMe;
    }

    public int getOfflineSessionIdleTimeout() {
        return offlineSessionIdleTimeout;
    }

    // KEYCLOAK-7688 Offline Session Max for Offline Token
    public boolean isOfflineSessionMaxLifespanEnabled() {
        return offlineSessionMaxLifespanEnabled;
    }

    public int getOfflineSessionMaxLifespan() {
        return offlineSessionMaxLifespan;
    }

    public int getClientSessionIdleTimeout() {
        return clientSessionIdleTimeout;
    }

    public int getClientSessionMaxLifespan() {
        return clientSessionMaxLifespan;
    }

    public int getClientOfflineSessionIdleTimeout() {
        return clientOfflineSessionIdleTimeout;
    }

    public int getClientOfflineSessionMaxLifespan() {
        return clientOfflineSessionMaxLifespan;
    }

    public int getAccessTokenLifespan() {
        return accessTokenLifespan;
    }

    public int getAccessTokenLifespanForImplicitFlow() {
        return accessTokenLifespanForImplicitFlow;
    }

    public int getAccessCodeLifespan() {
        return accessCodeLifespan;
    }

    public int getAccessCodeLifespanUserAction() {
        return accessCodeLifespanUserAction;
    }

    public Map<String, Integer> getUserActionTokenLifespans() {
        return userActionTokenLifespans;
    }

    public int getAccessCodeLifespanLogin() {
        return accessCodeLifespanLogin;
    }

    public OAuth2DeviceConfig getOAuth2DeviceConfig(Supplier<RealmModel> modelSupplier) {
        return deviceConfig.get(modelSupplier);
    }

    public CibaConfig getCibaConfig(Supplier<RealmModel> modelSupplier) {
        return cibaConfig.get(modelSupplier);
    }

    public ParConfig getParConfig(Supplier<RealmModel> modelSupplier) {
        return parConfig.get(modelSupplier);
    }

    public int getActionTokenGeneratedByAdminLifespan() {
        return actionTokenGeneratedByAdminLifespan;
    }

    public int getActionTokenGeneratedByUserLifespan() {
        return actionTokenGeneratedByUserLifespan;
    }

    /**
     * This method is supposed to return user lifespan based on the action token ID
     * provided. If nothing is provided, it will return the default lifespan.
     * @param actionTokenId
     * @return lifespan
     */
    public int getActionTokenGeneratedByUserLifespan(String actionTokenId) {
        if (actionTokenId == null || this.userActionTokenLifespans.get(actionTokenId) == null)
            return getActionTokenGeneratedByUserLifespan();
        return this.userActionTokenLifespans.get(actionTokenId);
    }

    public List<RequiredCredentialModel> getRequiredCredentials() {
        return requiredCredentials;
    }

    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public boolean isIdentityFederationEnabled() {
        return identityFederationEnabled;
    }

    public Map<String, String> getSmtpConfig() {
        return smtpConfig;
    }

    public Map<String, String> getBrowserSecurityHeaders() {
        return browserSecurityHeaders;
    }

    public String getLoginTheme() {
        return loginTheme;
    }

    public String getAccountTheme() {
        return accountTheme;
    }

    public String getAdminTheme() {
        return this.adminTheme;
    }

    public String getEmailTheme() {
        return emailTheme;
    }

    public int getNotBefore() {
        return notBefore;
    }

    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    public long getEventsExpiration() {
        return eventsExpiration;
    }

    public Set<String> getEventsListeners() {
        return eventsListeners;
    }

    public Set<String> getEnabledEventTypes() {
        return enabledEventTypes;
    }

    public boolean isAdminEventsEnabled() {
        return adminEventsEnabled;
    }

    public boolean isAdminEventsDetailsEnabled() {
        return adminEventsDetailsEnabled;
    }

    public List<IdentityProviderModel> getIdentityProviders() {
        return identityProviders;
    }

    public boolean isInternationalizationEnabled() {
        return internationalizationEnabled;
    }

    public Set<String> getSupportedLocales() {
        return supportedLocales;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public MultivaluedHashMap<String, IdentityProviderMapperModel> getIdentityProviderMappers() {
        return identityProviderMappers;
    }

    public Map<String, AuthenticationFlowModel> getAuthenticationFlows() {
        return authenticationFlows;
    }

    public Map<String, AuthenticatorConfigModel> getAuthenticatorConfigs() {
        return authenticatorConfigs;
    }

    public MultivaluedHashMap<String, AuthenticationExecutionModel> getAuthenticationExecutions() {
        return authenticationExecutions;
    }

    public AuthenticationExecutionModel getAuthenticationExecutionByFlowId(String flowId) {
        return executionsByFlowId.get(flowId);
    }
    
    public Map<String, AuthenticationExecutionModel> getExecutionsById() {
        return executionsById;
    }

    public Map<String, RequiredActionProviderModel> getRequiredActionProviders() {
        return requiredActionProviders;
    }

    public Map<String, RequiredActionProviderModel> getRequiredActionProvidersByAlias() {
        return requiredActionProvidersByAlias;
    }

    public OTPPolicy getOtpPolicy() {
        return otpPolicy;
    }

    public WebAuthnPolicy getWebAuthnPolicy() {
        return webAuthnPolicy;
    }

    public WebAuthnPolicy getWebAuthnPasswordlessPolicy() {
        return webAuthnPasswordlessPolicy;
    }

    public AuthenticationFlowModel getBrowserFlow() {
        return browserFlow;
    }

    public AuthenticationFlowModel getRegistrationFlow() {
        return registrationFlow;
    }

    public AuthenticationFlowModel getDirectGrantFlow() {
        return directGrantFlow;
    }

    public AuthenticationFlowModel getResetCredentialsFlow() {
        return resetCredentialsFlow;
    }

    public AuthenticationFlowModel getClientAuthenticationFlow() {
        return clientAuthenticationFlow;
    }

    public AuthenticationFlowModel getDockerAuthenticationFlow() {
        return dockerAuthenticationFlow;
    }

    public AuthenticationFlowModel getFirstBrokerLoginFlow() {
        return firstBrokerLoginFlow;
    }

    public List<String> getDefaultGroups() {
        return defaultGroups;
    }

    public List<String> getDefaultDefaultClientScopes() {
        return defaultDefaultClientScopes;
    }

    public List<String> getOptionalDefaultClientScopes() {
        return optionalDefaultClientScopes;
    }

    public List<AuthenticationFlowModel> getAuthenticationFlowList() {
        return authenticationFlowList;
    }

    public List<RequiredActionProviderModel> getRequiredActionProviderList() {
        return requiredActionProviderList;
    }

    public MultivaluedHashMap<String, ComponentModel> getComponentsByParent() {
        return componentsByParent;
    }

    public MultivaluedHashMap<String, ComponentModel> getComponentsByParentAndType() {
        return componentsByParentAndType;
    }

    public Map<String, ComponentModel> getComponents() {
        return components;
    }

    public String getAttribute(String name) {
        return attributes != null ? attributes.get(name) : null;
    }

    public Integer getAttribute(String name, Integer defaultValue) {
        String v = getAttribute(name);
        return v != null && !v.isEmpty() ? Integer.valueOf(v) : defaultValue;
    }

    public Long getAttribute(String name, Long defaultValue) {
        String v = getAttribute(name);
        return v != null && !v.isEmpty() ? Long.valueOf(v) : defaultValue;
    }

    public Boolean getAttribute(String name, Boolean defaultValue) {
        String v = getAttribute(name);
        return v != null && !v.isEmpty() ? Boolean.valueOf(v) : defaultValue;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public boolean isAllowUserManagedAccess() {
        return allowUserManagedAccess;
    }

    public Map<String, Map<String, String>> getRealmLocalizationTexts() {
        return realmLocalizationTexts;
    }
}
