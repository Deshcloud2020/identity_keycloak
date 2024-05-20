/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.testsuite.utils.arquillian;

public interface ContainerConstants {
    String APP_SERVER_PREFIX = "app-server-";

    String APP_SERVER_UNDERTOW = APP_SERVER_PREFIX + "undertow";

    String APP_SERVER_WILDFLY = APP_SERVER_PREFIX + "wildfly";
    String APP_SERVER_WILDFLY_CLUSTER = APP_SERVER_WILDFLY + "-ha-node-1;" + APP_SERVER_WILDFLY + "-ha-node-2";

    String APP_SERVER_EAP = APP_SERVER_PREFIX + "eap";
    String APP_SERVER_EAP_CLUSTER = APP_SERVER_EAP + "-ha-node-1;" + APP_SERVER_EAP + "-ha-node-2";

    String APP_SERVER_EAP8 = APP_SERVER_PREFIX + "eap8";
}
