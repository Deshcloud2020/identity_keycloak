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
package org.keycloak.forms.login.freemarker.model;

import java.util.stream.Stream;

import jakarta.ws.rs.core.MultivaluedMap;

import org.keycloak.authentication.requiredactions.util.UpdateProfileContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;

/**
 * @author Vlastimil Elias <velias@redhat.com>
 */
public class IdpReviewProfileBean extends AbstractUserProfileBean {

    private UpdateProfileContext idpCtx;
    
    public IdpReviewProfileBean(UpdateProfileContext idpCtx, MultivaluedMap<String, String> formData, KeycloakSession session) {
        super(formData);
        this.idpCtx = idpCtx;
        init(session, true);
    }

    @Override
    protected UserProfile createUserProfile(UserProfileProvider provider) {
        return provider.create(UserProfileContext.IDP_REVIEW, null, null);
    }

    @Override
    protected Stream<String> getAttributeDefaultValues(String name) {
        return idpCtx.getAttributeStream(name);
    }
    
    @Override 
    public String getContext() {
        return UserProfileContext.IDP_REVIEW.name();
    }
    
}