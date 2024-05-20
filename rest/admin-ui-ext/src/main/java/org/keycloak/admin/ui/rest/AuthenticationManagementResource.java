package org.keycloak.admin.ui.rest;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.keycloak.admin.ui.rest.model.Authentication;
import org.keycloak.admin.ui.rest.model.AuthenticationMapper;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.DefaultAuthenticationFlows;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;


public class AuthenticationManagementResource extends RoleMappingResource {
    public AuthenticationManagementResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth) {
        super(session, realm, auth);
    }

    @GET
    @Path("/flows")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @Operation(
            summary = "List all authentication flows for this realm",
            description = "This endpoint returns all the authentication flows and lists if there they are used."
    )
    @APIResponse(
            responseCode = "200",
            description = "",
            content = {@Content(
                    schema = @Schema(
                            implementation = Authentication.class,
                            type = SchemaType.ARRAY
                    )
            )}
    )
    public final List<Authentication> listIdentityProviders() {
        auth.realm().requireViewAuthenticationFlows();

        return realm.getAuthenticationFlowsStream()
                .filter(flow -> flow.isTopLevel() && !Objects.equals(flow.getAlias(), DefaultAuthenticationFlows.SAML_ECP_FLOW))
                .map(flow -> AuthenticationMapper.convertToModel(flow, realm))
                .collect(Collectors.toList());

    }


    @GET
    @Path("/{type}/{id}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @Operation(
            summary = "List all clients or identity providers that this flow is used by",
            description = "List all the clients or identity providers this flow is used by as a paginated list"
    )
    @APIResponse(
            responseCode = "200",
            description = "",
            content = {@Content(
                    schema = @Schema(
                            implementation = String.class,
                            type = SchemaType.ARRAY
                    )
            )}
    )
    public final List<String> listUsed(@PathParam("id") String id, @PathParam("type") String type, @QueryParam("first") @DefaultValue("0") long first,
            @QueryParam("max") @DefaultValue("10") long max, @QueryParam("search") @DefaultValue("") String search) {
        auth.realm().requireViewAuthenticationFlows();

        final AuthenticationFlowModel flow = realm.getAuthenticationFlowsStream().filter(f -> id.equals(f.getId())).collect(Collectors.toList()).get(0);

        if ("clients".equals(type)) {
            final Stream<ClientModel> clients = realm.getClientsStream();
            return clients.filter(
                            c -> c.getAuthenticationFlowBindingOverrides().get("browser") != null && c.getAuthenticationFlowBindingOverrides()
                                    .get("browser").equals(flow.getId()) || c.getAuthenticationFlowBindingOverrides()
                                    .get("direct_grant") != null && c.getAuthenticationFlowBindingOverrides().get("direct_grant").equals(flow.getId()))
                    .map(ClientModel::getClientId).filter(f -> f.contains(search))
                    .skip("".equals(search) ? first : 0).limit(max).collect(Collectors.toList());
        }

        if ("idp".equals(type)) {
            final Stream<IdentityProviderModel> identityProviders = realm.getIdentityProvidersStream();
            return identityProviders.filter(idp -> flow.getId().equals(idp.getFirstBrokerLoginFlowId())
                            || flow.getId().equals(idp.getPostBrokerLoginFlowId()))
                    .map(IdentityProviderModel::getAlias).filter(f -> f.contains(search))
                    .skip("".equals(search) ? first : 0).limit(max).collect(Collectors.toList());
        }

        throw new IllegalArgumentException("Invalid type");
    }
}