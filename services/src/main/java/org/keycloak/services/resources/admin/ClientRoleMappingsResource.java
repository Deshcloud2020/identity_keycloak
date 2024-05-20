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
package org.keycloak.services.resources.admin;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.NoCache;

import jakarta.ws.rs.NotFoundException;
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.ModelIllegalStateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleMapperModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.storage.ReadOnlyException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @resource Client Role Mappings
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@Extension(name = KeycloakOpenAPI.Profiles.ADMIN, value = "")
public class ClientRoleMappingsResource {
    protected static final Logger logger = Logger.getLogger(ClientRoleMappingsResource.class);

    protected KeycloakSession session;
    protected RealmModel realm;
    protected AdminPermissionEvaluator auth;
    protected RoleMapperModel user;
    protected ClientModel client;
    protected AdminEventBuilder adminEvent;
    private UriInfo uriInfo;
    protected AdminPermissionEvaluator.RequirePermissionCheck managePermission;
    protected AdminPermissionEvaluator.RequirePermissionCheck viewPermission;


    public ClientRoleMappingsResource(UriInfo uriInfo, KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth,
                                      RoleMapperModel user, ClientModel client, AdminEventBuilder adminEvent,
                                      AdminPermissionEvaluator.RequirePermissionCheck manageCheck, AdminPermissionEvaluator.RequirePermissionCheck viewCheck ) {
        this.uriInfo = uriInfo;
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.user = user;
        this.client = client;
        this.managePermission = manageCheck;
        this.viewPermission = viewCheck;
        this.adminEvent = adminEvent.resource(ResourceType.CLIENT_ROLE_MAPPING);
    }

    /**
     * Get client-level role mappings for the user, and the app
     *
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENT_ROLE_MAPPINGS)
    @Operation( summary = "Get client-level role mappings for the user, and the app")
    public Stream<RoleRepresentation> getClientRoleMappings() {
        viewPermission.require();

        return user.getClientRoleMappingsStream(client).map(ModelToRepresentation::toBriefRepresentation);
    }

    /**
     * Get effective client-level role mappings
     *
     * This recurses any composite roles
     *
     * @param briefRepresentation if false, return roles with their attributes
     *
     * @return
     */
    @Path("composite")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENT_ROLE_MAPPINGS)
    @Operation( summary = "Get effective client-level role mappings This recurses any composite roles")
    public Stream<RoleRepresentation> getCompositeClientRoleMappings(@Parameter(description = "if false, return roles with their attributes") @QueryParam("briefRepresentation") @DefaultValue("true") boolean briefRepresentation) {
        viewPermission.require();

        Stream<RoleModel> roles = client.getRolesStream();
        Function<RoleModel, RoleRepresentation> toBriefRepresentation = briefRepresentation
                ? ModelToRepresentation::toBriefRepresentation : ModelToRepresentation::toRepresentation;
        return roles.filter(user::hasRole).map(toBriefRepresentation);
    }

    /**
     * Get available client-level roles that can be mapped to the user
     *
     * @return
     */
    @Path("available")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENT_ROLE_MAPPINGS)
    @Operation( summary = "Get available client-level roles that can be mapped to the user")
    public Stream<RoleRepresentation> getAvailableClientRoleMappings() {
        viewPermission.require();

        return client.getRolesStream()
                .filter(auth.roles()::canMapRole)
                .filter(((Predicate<RoleModel>) user::hasDirectRole).negate())
                .map(ModelToRepresentation::toBriefRepresentation);
    }

    /**
     * Add client-level roles to the user role mapping
     *
     * @param roles
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENT_ROLE_MAPPINGS)
    @Operation( summary = "Add client-level roles to the user role mapping")
    @APIResponse(responseCode = "204", description = "No Content")
    public void addClientRoleMapping(List<RoleRepresentation> roles) {
        managePermission.require();

        try {
            for (RoleRepresentation role : roles) {
                RoleModel roleModel = client.getRole(role.getName());
                if (roleModel == null || !roleModel.getId().equals(role.getId())) {
                    throw new NotFoundException("Role not found");
                }
                auth.roles().requireMapRole(roleModel);
                user.grantRole(roleModel);
            }
        } catch (ModelIllegalStateException e) {
            logger.error(e.getMessage(), e);
            throw ErrorResponse.error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (ModelException | ReadOnlyException me) {
            logger.warn(me.getMessage(), me);
            throw new ErrorResponseException("invalid_request", "Could not add user role mappings!", Response.Status.BAD_REQUEST);
        }

        adminEvent.operation(OperationType.CREATE).resourcePath(uriInfo).representation(roles).success();

    }

    /**
     * Delete client-level roles from user role mapping
     *
     * @param roles
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Tag(name = KeycloakOpenAPI.Admin.Tags.CLIENT_ROLE_MAPPINGS)
    @Operation( summary = "Delete client-level roles from user role mapping")
    public void deleteClientRoleMapping(List<RoleRepresentation> roles) {
        managePermission.require();

        if (roles == null) {
            roles = user.getClientRoleMappingsStream(client)
                    .peek(roleModel -> {
                        auth.roles().requireMapRole(roleModel);
                        user.deleteRoleMapping(roleModel);
                    })
                    .map(ModelToRepresentation::toBriefRepresentation)
                    .collect(Collectors.toList());
        } else {
            for (RoleRepresentation role : roles) {
                RoleModel roleModel = client.getRole(role.getName());
                if (roleModel == null || !roleModel.getId().equals(role.getId())) {
                    throw new NotFoundException("Role not found");
                }

                auth.roles().requireMapRole(roleModel);
                try {
                    user.deleteRoleMapping(roleModel);
                } catch (ModelIllegalStateException e) {
                    logger.error(e.getMessage(), e);
                    throw ErrorResponse.error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
                } catch (ModelException | ReadOnlyException me) {
                    logger.warn(me.getMessage(), me);
                    throw new ErrorResponseException("invalid_request", "Could not remove user role mappings!", Response.Status.BAD_REQUEST);
                }
            }
        }

        adminEvent.operation(OperationType.DELETE).resourcePath(uriInfo).representation(roles).success();
    }
}
