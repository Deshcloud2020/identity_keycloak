/*
 *
 *  * Copyright 2021  Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.keycloak.userprofile;

import static org.keycloak.userprofile.UserProfileUtil.createUserProfileMetadata;
import static org.keycloak.userprofile.UserProfileUtil.isRootAttribute;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.keycloak.common.util.CollectionUtil;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.AbstractUserRepresentation;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.utils.StringUtil;

/**
 * <p>The default implementation for {@link UserProfile}. Should be reused as much as possible by the different implementations
 * of {@link UserProfileProvider}.
 *
 * <p>This implementation is not specific to any user profile implementation.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public final class DefaultUserProfile implements UserProfile {

    private final UserProfileMetadata metadata;
    private final Function<Attributes, UserModel> userSupplier;
    private final Attributes attributes;
    private final KeycloakSession session;
    private boolean validated;
    private UserModel user;

    public DefaultUserProfile(UserProfileMetadata metadata, Attributes attributes, Function<Attributes, UserModel> userCreator, UserModel user,
            KeycloakSession session) {
        this.metadata = metadata;
        this.userSupplier = userCreator;
        this.attributes = attributes;
        this.user = user;
        this.session = session;
    }

    @Override
    public void validate() {
        ValidationException validationException = new ValidationException();

        for (String attributeName : attributes.nameSet()) {
            this.attributes.validate(attributeName, validationException);
        }

        if (validationException.hasError()) {
            throw validationException;
        }

        validated = true;
    }

    @Override
    public UserModel create() throws ValidationException {
        if (user != null) {
            throw new RuntimeException("User already created");
        }

        if (!validated) {
            validate();
        }

        user = userSupplier.apply(this.attributes);

        return updateInternal(user, false);
    }

    @Override
    public void update(boolean removeAttributes, AttributeChangeListener... changeListener) {
        if (!validated) {
            validate();
        }

        updateInternal(user, removeAttributes, changeListener);
    }

    private UserModel updateInternal(UserModel user, boolean removeAttributes, AttributeChangeListener... changeListener) {
        if (user == null) {
            throw new RuntimeException("No user model provided for persisting changes");
        }

        try {
            Map<String, List<String>> writable = new HashMap<>(attributes.getWritable());

            for (Map.Entry<String, List<String>> attribute : writable.entrySet()) {
                String name = attribute.getKey();
                List<String> currentValue = user.getAttributeStream(name)
                        .filter(Objects::nonNull).collect(Collectors.toList());
                List<String> updatedValue = attribute.getValue();

                if (CollectionUtil.collectionEquals(currentValue, updatedValue)) {
                    continue;
                }

                boolean ignoreEmptyValue = !removeAttributes && updatedValue.isEmpty();

                if (isCustomAttribute(name) && ignoreEmptyValue) {
                    continue;
                }

                if (updatedValue.stream().allMatch(StringUtil::isBlank)) {
                    user.removeAttribute(name);
                } else {
                    user.setAttribute(name, updatedValue.stream().filter(StringUtil::isNotBlank).collect(Collectors.toList()));
                }

                if (UserModel.EMAIL.equals(name) && metadata.getContext().isResetEmailVerified()) {
                    user.setEmailVerified(false);
                }

                for (AttributeChangeListener listener : changeListener) {
                    listener.onChange(name, user, currentValue);
                }
            }

            // this is a workaround for supporting contexts where the decision to whether attributes should be removed depends on
            // specific aspect. For instance, old account should never remove attributes, the admin rest api should only remove if
            // the attribute map was sent.
            if (removeAttributes) {
                Set<String> attrsToRemove = new HashSet<>(user.getAttributes().keySet());

                attrsToRemove.removeAll(attributes.nameSet());

                for (String name : attrsToRemove) {
                    if (attributes.isReadOnly(name)) {
                        continue;
                    }

                    List<String> currentValue = user.getAttributeStream(name).filter(Objects::nonNull).collect(Collectors.toList());

                    if (isRootAttribute(name)) {
                        if (UserModel.FIRST_NAME.equals(name)) {
                            user.setFirstName(null);
                        } else if (UserModel.LAST_NAME.equals(name)) {
                            user.setLastName(null);
                        } else if (UserModel.LOCALE.equals(name)) {
                            user.removeAttribute(name);
                        }
                    } else {
                        user.removeAttribute(name);
                    }

                    for (AttributeChangeListener listener : changeListener) {
                        listener.onChange(name, user, currentValue);
                    }
                }
            }
        } catch (ModelException | ReadOnlyException e) {
            // some client code relies on these exceptions to react to exceptions from the storage
            throw e;
        } catch (Exception cause) {
            throw new RuntimeException("Unexpected error when persisting user profile", cause);
        }

        return user;
    }

    private boolean isCustomAttribute(String name) {
        return !isRootAttribute(name);
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public <R extends AbstractUserRepresentation> R toRepresentation() {
        if (user == null) {
            throw new IllegalStateException("Can not create the representation because the user is not yet created");
        }

        R rep = createUserRepresentation();
        Map<String, List<String>> readable = attributes.getReadable();
        Map<String, List<String>> attributesRep = new HashMap<>(readable);

        // all the attributes here have read access and might be available in the representation
        for (String name : readable.keySet()) {
            List<String> values = attributesRep.getOrDefault(name, Collections.emptyList())
                    .stream().filter(StringUtil::isNotBlank)
                    .collect(Collectors.toList());

            if (values.isEmpty()) {
                // make sure empty attributes are not in the representation
                attributesRep.remove(name);
                continue;
            }

            if (isRootAttribute(name)) {
                if (UserModel.LOCALE.equals(name)) {
                    // local is a special root attribute as it does not have a field in the user representation
                    // it should be available as a regular attribute if set
                    continue;
                }

                boolean isUnmanagedAttribute = metadata.getAttribute(name).isEmpty();
                String value = isUnmanagedAttribute ? null : values.stream().findFirst().orElse(null);

                if (UserModel.USERNAME.equals(name)) {
                    rep.setUsername(value);
                } else if (UserModel.EMAIL.equals(name)) {
                    rep.setEmail(value);
                    rep.setEmailVerified(user.isEmailVerified());
                } else if (UserModel.FIRST_NAME.equals(name)) {
                    rep.setFirstName(value);
                } else if (UserModel.LAST_NAME.equals(name)) {
                    rep.setLastName(value);
                }

                // we don't have root attributes as a regular attribute in the representation as they have their own fields
                attributesRep.remove(name);
            }
        }

        rep.setId(user.getId());
        rep.setAttributes(attributesRep.isEmpty() ? null : attributesRep);
        rep.setUserProfileMetadata(createUserProfileMetadata(session, this));

        return rep;
    }

    @SuppressWarnings("unchecked")
    private <R extends AbstractUserRepresentation> R createUserRepresentation() {
        UserProfileContext context = metadata.getContext();
        R rep;

        if (context.isAdminContext()) {
            RealmModel realm = session.getContext().getRealm();
            rep = (R) ModelToRepresentation.toRepresentation(session, realm, user);
        } else {
            // by default, we build the simplest representation without exposing much information about users
            rep = (R) new org.keycloak.representations.account.UserRepresentation();
        }

        // reset the root attribute values so that they are calculated based on the user profile configuration
        rep.setUsername(null);
        rep.setEmail(null);
        rep.setFirstName(null);
        rep.setLastName(null);

        return rep;
    }
}
