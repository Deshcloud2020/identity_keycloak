/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.cli.command;

import org.keycloak.quarkus.runtime.KeycloakMain;
import org.keycloak.quarkus.runtime.cli.ExecutionExceptionHandler;
import org.keycloak.quarkus.runtime.configuration.mappers.HttpPropertyMappers;

import picocli.CommandLine;

public abstract class AbstractStartCommand extends AbstractCommand implements Runnable {
    public static final String OPTIMIZED_BUILD_OPTION_LONG = "--optimized";

    @Override
    public void run() {
        doBeforeRun();
        CommandLine cmd = spec.commandLine();
        HttpPropertyMappers.validateConfig();
        validateConfig();
        KeycloakMain.start((ExecutionExceptionHandler) cmd.getExecutionExceptionHandler(), cmd.getErr(), cmd.getParseResult().originalArgs().toArray(new String[0]));
    }

    protected void doBeforeRun() {

    }
}
