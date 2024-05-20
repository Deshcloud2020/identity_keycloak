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
package org.keycloak.models;

import org.keycloak.common.Profile;
import org.keycloak.common.Profile.Feature;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A model type representing the configuration for identity providers. It provides some common properties and also a {@link org.keycloak.models.IdentityProviderModel#config}
 * for configuration options and properties specifics to a identity provider.</p>
 *
 * @author Pedro Igor
 */
public class IdentityProviderModel implements Serializable {

    public static final String ALLOWED_CLOCK_SKEW = "allowedClockSkew";
    public static final String LOGIN_HINT = "loginHint";
    public static final String PASS_MAX_AGE = "passMaxAge";

    public static final String SYNC_MODE = "syncMode";
    
    public static final String HIDE_ON_LOGIN = "hideOnLoginPage";

    public static final String FILTERED_BY_CLAIMS = "filteredByClaim";
    public static final String CLAIM_FILTER_NAME = "claimFilterName";
    public static final String CLAIM_FILTER_VALUE = "claimFilterValue";
    public static final String DO_NOT_STORE_USERS = "doNotStoreUsers";
    public static final String METADATA_DESCRIPTOR_URL = "metadataDescriptorUrl";

    private String internalId;

    /**
     * <p>An user-defined identifier to unique identify an identity provider instance.</p>
     */
    private String alias;

    /**
     * <p>An identifier used to reference a specific identity provider implementation. The value of this field is the same
     * across instances of the same provider implementation.</p>
     */
    private String providerId;

    private boolean enabled;
    
    private boolean trustEmail;

    private boolean storeToken;

    protected boolean addReadTokenRoleOnCreate;

    protected boolean linkOnly;

    /**
     * Specifies if particular provider should be used by default for authentication even before displaying login screen
     */
    private boolean authenticateByDefault;

    private String firstBrokerLoginFlowId;

    private String postBrokerLoginFlowId;

    private String displayName;

    private String displayIconClasses;

    private IdentityProviderSyncMode syncMode;

    /**
     * <p>A map containing the configuration and properties for a specific identity provider instance and implementation. The items
     * in the map are understood by the identity provider implementation.</p>
     */
    private Map<String, String> config = new HashMap<>();

    public IdentityProviderModel() {
    }

    public IdentityProviderModel(IdentityProviderModel model) {
        if (model != null) {
            this.internalId = model.getInternalId();
            this.providerId = model.getProviderId();
            this.alias = model.getAlias();
            this.displayName = model.getDisplayName();
            this.config = new HashMap<>(model.getConfig());
            this.enabled = model.isEnabled();
            this.trustEmail = model.isTrustEmail();
            this.storeToken = model.isStoreToken();
            this.linkOnly = model.isLinkOnly();
            this.authenticateByDefault = model.isAuthenticateByDefault();
            this.addReadTokenRoleOnCreate = model.addReadTokenRoleOnCreate;
            this.firstBrokerLoginFlowId = model.getFirstBrokerLoginFlowId();
            this.postBrokerLoginFlowId = model.getPostBrokerLoginFlowId();
            this.displayIconClasses = model.getDisplayIconClasses();
        }
    }

    public String getInternalId() {
        return this.internalId;
    }

    public void setInternalId(String internalId) {
        this.internalId = internalId;
    }

    public String getAlias() {
        return this.alias;
    }

    public void setAlias(String id) {
        this.alias = id;
    }

    public String getProviderId() {
        return this.providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStoreToken() {
        return this.storeToken;
    }

    public void setStoreToken(boolean storeToken) {
        this.storeToken = storeToken;
    }

    public boolean isLinkOnly() {
        return linkOnly;
    }

    public void setLinkOnly(boolean linkOnly) {
        this.linkOnly = linkOnly;
    }

    @Deprecated
    public boolean isAuthenticateByDefault() {
        return authenticateByDefault;
    }

    @Deprecated
    public void setAuthenticateByDefault(boolean authenticateByDefault) {
        this.authenticateByDefault = authenticateByDefault;
    }

    public String getFirstBrokerLoginFlowId() {
        return firstBrokerLoginFlowId;
    }

    public void setFirstBrokerLoginFlowId(String firstBrokerLoginFlowId) {
        this.firstBrokerLoginFlowId = firstBrokerLoginFlowId;
    }

    public String getPostBrokerLoginFlowId() {
        return postBrokerLoginFlowId;
    }

    public void setPostBrokerLoginFlowId(String postBrokerLoginFlowId) {
        this.postBrokerLoginFlowId = postBrokerLoginFlowId;
    }

    public Map<String, String> getConfig() {
        return this.config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public boolean isAddReadTokenRoleOnCreate() {
        return addReadTokenRoleOnCreate;
    }

    public void setAddReadTokenRoleOnCreate(boolean addReadTokenRoleOnCreate) {
        this.addReadTokenRoleOnCreate = addReadTokenRoleOnCreate;
    }

    public boolean isTrustEmail() {
        return trustEmail;
    }

    public void setTrustEmail(boolean trustEmail) {
        this.trustEmail = trustEmail;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayIconClasses() {
        return displayIconClasses;
    }

    public String getOrganizationId() {
        return getConfig().get(OrganizationModel.ORGANIZATION_ATTRIBUTE);
    }

    public void setOrganizationId(String organizationId) {
        getConfig().put(OrganizationModel.ORGANIZATION_ATTRIBUTE, organizationId);
    }

    /**
     * <p>Validates this configuration.
     * 
     * <p>Sub-classes can override this method in order to enforce provider specific validations.
     * 
     * @param realm the realm
     */
    public void validate(RealmModel realm) {
    }
        
    public IdentityProviderSyncMode getSyncMode() {
        return IdentityProviderSyncMode.valueOf(getConfig().getOrDefault(SYNC_MODE, "LEGACY"));
    }

    public void setSyncMode(IdentityProviderSyncMode syncMode) {
        getConfig().put(SYNC_MODE, syncMode.toString());
    }

    public boolean isLoginHint() {
        return Boolean.valueOf(getConfig().get(LOGIN_HINT));
    }

    public void setLoginHint(boolean loginHint) {
        getConfig().put(LOGIN_HINT, String.valueOf(loginHint));
    }

    public boolean isPassMaxAge() {
        return Boolean.valueOf(getConfig().get(PASS_MAX_AGE));
    }

    public void setPassMaxAge(boolean passMaxAge) {
        getConfig().put(PASS_MAX_AGE, String.valueOf(passMaxAge));
    }

     
    public boolean isHideOnLogin() {
        return Boolean.valueOf(getConfig().get(HIDE_ON_LOGIN));
    }

    public void setHideOnLogin(boolean hideOnLogin) {
        getConfig().put(HIDE_ON_LOGIN, String.valueOf(hideOnLogin));
    }

    /**
     * Returns flag whether the users within this IdP should be transient, ie. not stored in Keycloak database.
     * Default value: {@code false}.
     * @return
     */
    public boolean isTransientUsers() {
        return Profile.isFeatureEnabled(Feature.TRANSIENT_USERS) && Boolean.valueOf(getConfig().get(DO_NOT_STORE_USERS));
    }

    /**
     * Configures the IdP to not store users in Keycloak database. Default value: {@code false}.
     * @return
     */
    public void setTransientUsers(boolean transientUsers) {
        getConfig().put(DO_NOT_STORE_USERS, String.valueOf(transientUsers));
    }

    public boolean isFilteredByClaims() {
        return Boolean.valueOf(getConfig().getOrDefault(FILTERED_BY_CLAIMS, Boolean.toString(false)));
    }

    public void setFilteredByClaims(boolean filteredByClaims) {
        getConfig().put(FILTERED_BY_CLAIMS, String.valueOf(filteredByClaims));
    }

    public String getClaimFilterName() {
        return String.valueOf(getConfig().getOrDefault(CLAIM_FILTER_NAME, ""));
    }

    public void setClaimFilterName(String claimFilterName) {
        getConfig().put(CLAIM_FILTER_NAME, claimFilterName);
    }

    public String getClaimFilterValue() {
        return String.valueOf(getConfig().getOrDefault(CLAIM_FILTER_VALUE, ""));
    }

    public void setClaimFilterValue(String claimFilterValue) {
        getConfig().put(CLAIM_FILTER_VALUE, claimFilterValue);
    }

    public String getMetadataDescriptorUrl() {
        return getConfig().get(METADATA_DESCRIPTOR_URL);
    }

    public void setMetadataDescriptorUrl(String metadataDescriptorUrl) {
        getConfig().put(METADATA_DESCRIPTOR_URL, metadataDescriptorUrl);
    }
}
