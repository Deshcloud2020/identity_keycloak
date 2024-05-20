/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.operator.crds.v2alpha1.deployment.spec;

import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.sundr.builder.annotations.Buildable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CacheSpec {

    private ConfigMapKeySelector configMapFile;

    public ConfigMapKeySelector getConfigMapFile() {
        return configMapFile;
    }

    public void setConfigMapFile(ConfigMapKeySelector configMapFile) {
        this.configMapFile = configMapFile;
    }

}
