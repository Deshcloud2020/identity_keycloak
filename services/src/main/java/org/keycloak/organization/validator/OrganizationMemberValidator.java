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

package org.keycloak.organization.validator;

import static java.util.Optional.ofNullable;
import static org.keycloak.organization.utils.Organizations.resolveBroker;
import static org.keycloak.validate.BuiltinValidators.emailValidator;

import java.util.List;

import org.keycloak.Config.Scope;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.common.Profile;
import org.keycloak.common.Profile.Feature;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationDomainModel;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.UserModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.utils.StringUtil;
import org.keycloak.validate.AbstractSimpleValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

public class OrganizationMemberValidator extends AbstractSimpleValidator implements EnvironmentDependentProviderFactory {

    public static final String ID = "organization-member-validator";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected void doValidate(Object value, String inputHint, ValidationContext context, ValidatorConfig config) {
        KeycloakSession session = context.getSession();
        OrganizationModel organization = resolveOrganization(context, session);

        if (organization == null) {
            return;
        }

        validateEmailDomain((String) value, inputHint, context, organization);
    }

    @Override
    protected boolean skipValidation(Object value, ValidatorConfig config) {
        return false;
    }

    @Override
    public boolean isSupported(Scope config) {
        return Profile.isFeatureEnabled(Feature.ORGANIZATION);
    }

    private void validateEmailDomain(String email, String inputHint, ValidationContext context, OrganizationModel organization) {
        if (!UserModel.EMAIL.equals(inputHint)) {
            return;
        }

        if (StringUtil.isBlank(email)) {
            context.addError(new ValidationError(ID, inputHint, "Email not set"));
            return;
        }

        if (!emailValidator().validate(email, inputHint, context).isValid()) {
            return;
        }

        UserProfileAttributeValidationContext upContext = (UserProfileAttributeValidationContext) context;
        AttributeContext attributeContext = upContext.getAttributeContext();
        UserModel user = attributeContext.getUser();
        String emailDomain = email.substring(email.indexOf('@') + 1);
        List<String> expectedDomains = organization.getDomains().map(OrganizationDomainModel::getName).toList();

        if (expectedDomains.isEmpty()) {
            // no domain to check
            return;
        }

        if (UserProfileContext.IDP_REVIEW.equals(attributeContext.getContext())) {
            expectedDomains = resolveExpectedDomainsWhenReviewingFederatedUserProfile(organization, attributeContext);
        } else if (organization.isManaged(user)) {
            expectedDomains = resolveExpectedDomainsForManagedUser(context, user);
        } else {
            // no validation happens for unmanaged users as they are realm users linked to an organization
            return;
        }

        if (expectedDomains.isEmpty() || expectedDomains.contains(emailDomain)) {
            // valid email domain
            return;
        }

        context.addError(new ValidationError(ID, inputHint, "Email domain does not match any domain from the organization"));
    }

    private static List<String> resolveExpectedDomainsForManagedUser(ValidationContext context, UserModel user) {
        IdentityProviderModel broker = resolveBroker(context.getSession(), user);

        if (broker == null) {
            return List.of();
        }

        String domain = broker.getConfig().get(OrganizationModel.ORGANIZATION_DOMAIN_ATTRIBUTE);
        return ofNullable(domain).map(List::of).orElse(List.of());
    }

    private static List<String> resolveExpectedDomainsWhenReviewingFederatedUserProfile(OrganizationModel organization, AttributeContext attributeContext) {
        // validating in the context of the brokering flow
        KeycloakSession session = attributeContext.getSession();
        BrokeredIdentityContext brokerContext = (BrokeredIdentityContext) session.getAttribute(BrokeredIdentityContext.class.getName());

        if (brokerContext == null) {
            return List.of();
        }

        String alias = brokerContext.getIdpConfig().getAlias();
        IdentityProviderModel broker = organization.getIdentityProviders()
                .filter((p) -> p.getAlias().equals(alias))
                .findAny()
                .orElse(null);

        if (broker == null) {
            // the broker the user is authenticating is not linked to the organization
            return List.of();
        }

        // expect the email domain to match the domain set to the broker or none if not set
        String brokerDomain = broker.getConfig().get(OrganizationModel.ORGANIZATION_DOMAIN_ATTRIBUTE);
        return  ofNullable(brokerDomain).map(List::of).orElse(List.of());
    }

    private OrganizationModel resolveOrganization(ValidationContext context, KeycloakSession session) {
        OrganizationModel organization = (OrganizationModel) session.getAttribute(OrganizationModel.class.getName());

        if (organization != null) {
            return organization;
        }

        UserProfileAttributeValidationContext upContext = (UserProfileAttributeValidationContext) context;
        AttributeContext attributeContext = upContext.getAttributeContext();
        UserModel user = attributeContext.getUser();

        if (user != null) {
            OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
            return provider.getByMember(user);
        }

        return null;
    }
}
