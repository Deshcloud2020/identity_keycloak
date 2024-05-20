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
package org.keycloak.client.admin.cli.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import org.keycloak.client.admin.cli.KcAdmMain;
import org.keycloak.client.admin.cli.operations.ClientOperations;
import org.keycloak.client.admin.cli.operations.GroupOperations;
import org.keycloak.client.admin.cli.operations.RoleOperations;
import org.keycloak.client.admin.cli.operations.LocalSearch;
import org.keycloak.client.admin.cli.operations.UserOperations;
import org.keycloak.client.cli.config.ConfigData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.keycloak.client.admin.cli.KcAdmMain.CMD;
import static org.keycloak.client.cli.util.ConfigUtil.credentialsAvailable;
import static org.keycloak.client.cli.util.ConfigUtil.loadConfig;
import static org.keycloak.client.cli.util.OsUtil.PROMPT;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
@Command(name = "add-roles", description = "[ARGUMENTS]")
public class AddRolesCmd extends AbstractAuthOptionsCmd {

    @Option(names = "--uusername", description = "Target user's 'username'")
    String uusername;

    @Option(names = "--uid", description = "Target user's 'id'")
    String uid;

    @Option(names = "--gname", description = "Target group's 'name'")
    String gname;

    @Option(names = "--gpath", description = "Target group's 'path'")
    String gpath;

    @Option(names = "--gid", description = "Target group's 'id'")
    String gid;

    @Option(names = "--rname", description = "Composite role's 'name'")
    String rname;

    @Option(names = "--rid", description = "Composite role's 'id'")
    String rid;

    @Option(names = "--cclientid", description = "Target client's 'clientId'")
    String cclientid;

    @Option(names = "--cid", description = "Target client's 'id'")
    String cid;

    @Option(names = "--rolename", description = "Role's 'name' attribute")
    List<String> roleNames = new ArrayList<>();

    @Option(names = "--roleid", description = "Role's 'id' attribute")
    List<String> roleIds = new ArrayList<>();

    @Override
    protected void process() {
        if (uid != null && uusername != null) {
            throw new IllegalArgumentException("Incompatible options: --uid and --uusername are mutually exclusive");
        }

        if ((gid != null && gname != null) || (gid != null && gpath != null) || (gname != null && gpath != null)) {
            throw new IllegalArgumentException("Incompatible options: --gid, --gname and --gpath are mutually exclusive");
        }

        if (roleNames.isEmpty() && roleIds.isEmpty()) {
            throw new IllegalArgumentException("No role to add specified. Use --rolename or --roleid to specify roles to add");
        }

        if (cid != null && cclientid != null) {
            throw new IllegalArgumentException("Incompatible options: --cid and --cclientid are mutually exclusive");
        }

        if (rid != null && rname != null) {
            throw new IllegalArgumentException("Incompatible options: --rid and --rname are mutually exclusive");
        }

        if (isUserSpecified() && isGroupSpecified()) {
            throw new IllegalArgumentException("Incompatible options: --uusername / --uid can't be used at the same time as --gname / --gid / --gpath");
        }

        if (isUserSpecified() && isCompositeRoleSpecified()) {
            throw new IllegalArgumentException("Incompatible options: --uusername / --uid can't be used at the same time as --rname / --rid");
        }

        if (isGroupSpecified() && isCompositeRoleSpecified()) {
            throw new IllegalArgumentException("Incompatible options: --rname / --rid can't be used at the same time as --gname / --gid / --gpath");
        }

        if (!isUserSpecified() && !isGroupSpecified() && !isCompositeRoleSpecified()) {
            throw new IllegalArgumentException("No user nor group nor composite role specified. Use --uusername / --uid to specify user or --gname / --gid / --gpath to specify group or --rname / --rid to specify a composite role");
        }


        ConfigData config = loadConfig();
        config = copyWithServerInfo(config);

        setupTruststore(config);

        String auth = null;

        config = ensureAuthInfo(config);
        config = copyWithServerInfo(config);
        if (credentialsAvailable(config)) {
            auth = ensureToken(config);
        }

        auth = auth != null ? "Bearer " + auth : null;

        final String server = config.getServerUrl();
        final String realm = getTargetRealm(config);
        final String adminRoot = adminRestRoot != null ? adminRestRoot : composeAdminRoot(server);


        if (isUserSpecified()) {
            if (uid == null) {
                uid = UserOperations.getIdFromUsername(adminRoot, realm, auth, uusername);
            }
            if (isClientSpecified()) {
                // list client roles for a user
                if (cid == null) {
                    cid = ClientOperations.getIdFromClientId(adminRoot, realm, auth, cclientid);
                }

                List<ObjectNode> roles = RoleOperations.getClientRoles(adminRoot, realm, cid, auth);
                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds, new LocalSearch(roles));

                // now add all the roles
                UserOperations.addClientRoles(adminRoot, realm, auth, uid, cid, new ArrayList<>(rolesToAdd));

            } else {

                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds,
                        new LocalSearch(RoleOperations.getRealmRolesAsNodes(adminRoot, realm, auth)));

                // now add all the roles
                UserOperations.addRealmRoles(adminRoot, realm, auth, uid, new ArrayList<>(rolesToAdd));
            }

        } else if (isGroupSpecified()) {
            if (gname != null) {
                gid = GroupOperations.getIdFromName(adminRoot, realm, auth, gname);
            } else if (gpath != null) {
                gid = GroupOperations.getIdFromPath(adminRoot, realm, auth, gpath);
            }
            if (isClientSpecified()) {
                // list client roles for a group
                if (cid == null) {
                    cid = ClientOperations.getIdFromClientId(adminRoot, realm, auth, cclientid);
                }

                List<ObjectNode> roles = RoleOperations.getClientRoles(adminRoot, realm, cid, auth);
                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds, new LocalSearch(roles));

                // now add all the roles
                GroupOperations.addClientRoles(adminRoot, realm, auth, gid, cid, new ArrayList<>(rolesToAdd));

            } else {

                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds,
                        new LocalSearch(RoleOperations.getRealmRolesAsNodes(adminRoot, realm, auth)));

                // now add all the roles
                GroupOperations.addRealmRoles(adminRoot, realm, auth, gid, new ArrayList<>(rolesToAdd));
            }

        } else if (isCompositeRoleSpecified()) {
            if (rid == null) {
                rid = RoleOperations.getIdFromRoleName(adminRoot, realm, auth, rname);
            }
            if (isClientSpecified()) {
                // list client roles for a composite role
                if (cid == null) {
                    cid = ClientOperations.getIdFromClientId(adminRoot, realm, auth, cclientid);
                }

                List<ObjectNode> roles = RoleOperations.getClientRoles(adminRoot, realm, cid, auth);
                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds, new LocalSearch(roles));

                // now add all the roles
                RoleOperations.addClientRoles(adminRoot, realm, auth, rid, new ArrayList<>(rolesToAdd));

            } else {
                Set<ObjectNode> rolesToAdd = getRoleRepresentations(roleNames, roleIds,
                        new LocalSearch(RoleOperations.getRealmRolesAsNodes(adminRoot, realm, auth)));

                // now add all the roles
                RoleOperations.addRealmRoles(adminRoot, realm, auth, rid, new ArrayList<>(rolesToAdd));
            }

        } else {
            throw new IllegalArgumentException("No user nor group, nor composite role specified. Use --uusername / --uid to specify user or --gname / --gid / --gpath to specify group or --rname / --rid to specify a composite role");
        }
    }

    private Set<ObjectNode> getRoleRepresentations(List<String> roleNames, List<String> roleIds, LocalSearch roleSearch) {
        Set<ObjectNode> rolesToAdd = new HashSet<>();

        // now we process roles
        for (String name : roleNames) {
            ObjectNode r = roleSearch.exactMatchOne(name, "name");
            if (r == null) {
                throw new RuntimeException("Role not found for name: " + name);
            }
            rolesToAdd.add(r);
        }
        for (String id : roleIds) {
            ObjectNode r = roleSearch.exactMatchOne(id, "id");
            if (r == null) {
                throw new RuntimeException("Role not found for id: " + id);
            }
            rolesToAdd.add(r);
        }
        return rolesToAdd;
    }

    private boolean isClientSpecified() {
        return cid != null || cclientid != null;
    }

    private boolean isGroupSpecified() {
        return gid != null || gname != null || gpath != null;
    }

    private boolean isUserSpecified() {
        return uid != null || uusername != null;
    }

    private boolean isCompositeRoleSpecified() {
        return rid != null || rname != null;
    }

    @Override
    protected boolean nothingToDo() {
        return super.nothingToDo() && uusername == null && uid == null && cclientid == null && roleIds.isEmpty() && roleNames.isEmpty();
    }

    @Override
    protected String help() {
        return usage();
    }

    public static String usage() {
        StringWriter sb = new StringWriter();
        PrintWriter out = new PrintWriter(sb);
        out.println("Usage: " + CMD + " add-roles (--uusername USERNAME | --uid ID) [--cclientid CLIENT_ID | --cid ID] (--rolename NAME | --roleid ID)+ [ARGUMENTS]");
        out.println("       " + CMD + " add-roles (--gname NAME | --gpath PATH | --gid ID) [--cclientid CLIENT_ID | --cid ID] (--rolename NAME | --roleid ID)+ [ARGUMENTS]");
        out.println("       " + CMD + " add-roles (--rname ROLE_NAME | --rid ROLE_ID) [--cclientid CLIENT_ID | --cid ID] (--rolename NAME | --roleid ID)+ [ARGUMENTS]");
        out.println();
        out.println("Command to add realm or client roles to a user, a group or a composite role.");
        out.println();
        out.println("Use `" + CMD + " config credentials` to establish an authenticated session, or use CREDENTIALS OPTIONS");
        out.println("to perform one time authentication.");
        out.println();
        out.println("If client is specified using --cclientid or --cid then roles to add are client roles, otherwise they are realm roles.");
        out.println("Either a user, or a group needs to be specified. If user is specified using --uusername or --uid then roles are added");
        out.println("to a specific user. If group is specified using --gname, --gpath or --gid then roles are added to a specific group.");
        out.println("If composite role is specified using --rname or --rid then roles are added to a specific composite role.");
        out.println("One or more roles have to be specified using --rolename or --roleid so that they are added to a group, a user or a composite role.");
        out.println();
        out.println("Arguments:");
        out.println();
        out.println("  Global options:");
        out.println("    -x                    Print full stack trace when exiting with error");
        out.println("    --config              Path to the config file (" + KcAdmMain.DEFAULT_CONFIG_FILE_STRING + " by default)");
        out.println("    --no-config           Don't use config file - no authentication info is loaded or saved");
        out.println("    --token               Token to use to invoke on Keycloak.  Other credential may be ignored if this flag is set.");
        out.println("    --truststore PATH     Path to a truststore containing trusted certificates");
        out.println("    --trustpass PASSWORD  Truststore password (prompted for if not specified and --truststore is used)");
        out.println("    CREDENTIALS OPTIONS   Same set of options as accepted by '" + CMD + " config credentials' in order to establish");
        out.println("                          an authenticated sessions. In combination with --no-config option this allows transient");
        out.println("                          (on-the-fly) authentication to be performed which leaves no tokens in config file.");
        out.println();
        out.println("  Command specific options:");
        out.println("    --uusername           User's 'username'. If more than one user exists with the same username");
        out.println("                          you'll have to use --uid to specify the target user");
        out.println("    --uid                 User's 'id' attribute");
        out.println("    --gname               Group's 'name'. If more than one group exists with the same name you'll have");
        out.println("                          to use --gid, or --gpath to specify the target group");
        out.println("    --gpath               Group's 'path' attribute");
        out.println("    --gid                 Group's 'id' attribute");
        out.println("    --rname               Composite role's 'name' attribute");
        out.println("    --rid                 Composite role's 'id' attribute");
        out.println("    --cclientid           Client's 'clientId' attribute");
        out.println("    --cid                 Client's 'id' attribute");
        out.println("    --rolename            Role's 'name' attribute");
        out.println("    --roleid              Role's 'id' attribute");
        out.println("    -a, --admin-root URL      URL of Admin REST endpoint root if not default - e.g. http://localhost:8080/admin");
        out.println("    -r, --target-realm REALM  Target realm to issue requests against if not the one authenticated against");
        out.println();
        out.println("Examples:");
        out.println();
        out.println("Add 'offline_access' realm role to a user:");
        out.println("  " + PROMPT + " " + CMD + " add-roles -r demorealm --uusername testuser --rolename offline_access");
        out.println();
        out.println("Add 'realm-management' client roles 'view-users', 'view-clients' and 'view-realm' to a user:");
        out.println("  " + PROMPT + " " + CMD + " add-roles -r demorealm --uusername testuser --cclientid realm-management --rolename view-users --rolename view-clients --rolename view-realm");
        out.println();
        out.println("Add 'uma_authorization' realm role to a group:");
        out.println("  " + PROMPT + " " + CMD + " add-roles -r demorealm --gname PowerUsers --rolename uma_authorization");
        out.println();
        out.println("Add 'realm-management' client roles 'realm-admin' to a group:");
        out.println("  " + PROMPT + " " + CMD + " add-roles -r demorealm --gname PowerUsers --cclientid realm-management --rolename realm-admin");
        out.println();
        out.println();
        out.println("Use '" + CMD + " help' for general information and a list of commands");
        return sb.toString();
    }
}
