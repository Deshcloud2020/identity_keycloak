/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.it.cli.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.keycloak.quarkus.runtime.cli.command.AbstractStartCommand.OPTIMIZED_BUILD_OPTION_LONG;
import static org.keycloak.quarkus.runtime.cli.command.Main.CONFIG_FILE_LONG_NAME;

import org.junit.jupiter.api.Test;
import org.keycloak.config.database.Database;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;

import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.WithEnvVars;
import org.keycloak.it.utils.KeycloakDistribution;

import java.nio.file.Paths;

@DistributionTest
class BuildCommandDistTest {

    @Test
    @Launch({ "build" })
    void resetConfig(LaunchResult result) {
        assertTrue(result.getOutput().contains("Updating the configuration and installing your custom providers, if any. Please wait."),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(result.getOutput().contains("Quarkus augmentation completed"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(result.getOutput().contains("Server configuration updated and persisted. Run the following command to review the configuration:"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
        assertTrue(result.getOutput().contains(KeycloakDistribution.SCRIPT_CMD + " show-config"),
                () -> "The Output:\n" + result.getOutput() + "doesn't contains the expected string.");
    }

    @Test
    @Launch({ "--profile=dev", "build" })
    void failIfDevProfile(LaunchResult result) {
        assertTrue(result.getErrorOutput().contains("ERROR: Failed to run 'build' command."),
                () -> "The Error Output:\n" + result.getErrorOutput() + "doesn't contains the expected string.");
        assertTrue(result.getErrorOutput().contains("You can not 'build' the server in development mode. Please re-build the server first, using 'kc.sh build' for the default production mode."),
                () -> "The Error Output:\n" + result.getErrorOutput() + "doesn't contains the expected string.");
        assertTrue(result.getErrorOutput().contains("For more details run the same command passing the '--verbose' option. Also you can use '--help' to see the details about the usage of the particular command."),
                () -> "The Error Output:\n" + result.getErrorOutput() + "doesn't contains the expected string.");
        assertEquals(4, result.getErrorStream().size());
    }

    @Test
    @Launch({ "build", "--db=postgres", "--db-username=myuser", "--db-password=mypassword", "--http-enabled=true" })
    void testFailRuntimeOptions(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertError("Run time option: '--db-username' not usable with build");
    }

    @Test
    @WithEnvVars({"KC_DB", "invalid"})
    @Launch({ "build" })
    void testFailInvalidOptionInEnv(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertError("Invalid value for option 'KC_DB': invalid. Expected values are: dev-file, dev-mem, mariadb, mssql, mysql, oracle, postgres");
    }

    @Test
    @RawDistOnly(reason = "Raw is enough and we avoid issues with including custom conf file in the container")
    public void testFailInvalidOptionInConf(KeycloakDistribution distribution) {
        CLIResult cliResult = distribution.run(CONFIG_FILE_LONG_NAME + "=" + Paths.get("src/test/resources/BuildCommandDistTest/keycloak.conf").toAbsolutePath().normalize(), "build");
        cliResult.assertError("Invalid value for option 'kc.db' in keycloak.conf: foo. Expected values are: dev-file, dev-mem, mariadb, mssql, mysql, oracle, postgres");
    }

    @Test
    @RawDistOnly(reason = "Containers are immutable")
    void testDoNotRecordRuntimeOptionsDuringBuild(KeycloakDistribution distribution) {
        distribution.setProperty("proxy", "edge");
        distribution.run("build");
        distribution.removeProperty("proxy");

        CLIResult result = distribution.run("start", "--hostname=mykeycloak", "--cache=local", OPTIMIZED_BUILD_OPTION_LONG);
        result.assertError("Key material not provided to setup HTTPS");
    }

    @Test
    @RawDistOnly(reason = "Containers are immutable")
    @Launch({"build", "--db=oracle"})
    void missingOracleJdbcDriver(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;

        String dbDriver = Database.getDriver("oracle", true).orElse("");
        String errorMessage = String.format("ERROR: Unable to find the JDBC driver (%s). You need to install it.", dbDriver);

        boolean isProduct = System.getProperty("product") != null;
        if (isProduct) {
            cliResult.assertError(errorMessage);
            cliResult.assertNoBuild();
        } else {
            cliResult.assertNoMessage(errorMessage);
            cliResult.assertBuild();
        }
    }
}
