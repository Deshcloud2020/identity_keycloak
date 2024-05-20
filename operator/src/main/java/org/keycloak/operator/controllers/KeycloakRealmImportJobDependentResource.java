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
package org.keycloak.operator.controllers;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import jakarta.inject.Inject;
import org.keycloak.operator.Config;
import org.keycloak.operator.Constants;
import org.keycloak.operator.Utils;
import org.keycloak.operator.crds.v2alpha1.realmimport.KeycloakRealmImport;

import java.util.List;
import java.util.Set;

import static org.keycloak.operator.Utils.addResources;
import static org.keycloak.operator.controllers.KeycloakDistConfigurator.getKeycloakOptionEnvVarName;

public class KeycloakRealmImportJobDependentResource extends KubernetesDependentResource<Job, KeycloakRealmImport> implements Creator<Job, KeycloakRealmImport>, GarbageCollected<KeycloakRealmImport> {

    private final Config config;

    KeycloakRealmImportJobDependentResource(Config config) {
        super(Job.class);
        this.config = config;
        this.configureWith(new KubernetesDependentResourceConfigBuilder<Job>()
                .withLabelSelector(Constants.DEFAULT_LABELS_AS_STRING)
                .build());
    }

    @Override
    protected Job desired(KeycloakRealmImport primary, Context<KeycloakRealmImport> context) {
        StatefulSet existingDeployment = context.managedDependentResourceContext().get(StatefulSet.class, StatefulSet.class).orElseThrow();

        var keycloakPodTemplate = existingDeployment
                .getSpec()
                .getTemplate();

        String secretName = KeycloakRealmImportSecretDependentResource.getSecretName(primary);
        String volumeName = KubernetesResourceUtil.sanitizeName(secretName + "-volume");

        buildKeycloakJobContainer(keycloakPodTemplate.getSpec().getContainers().get(0), primary, volumeName);
        keycloakPodTemplate.getSpec().getVolumes().add(buildSecretVolume(volumeName, secretName));

        var labels = keycloakPodTemplate.getMetadata().getLabels();

        // The Job should not be selected with app=keycloak
        labels.put("app", "keycloak-realm-import");

        var envvars = keycloakPodTemplate
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();

        var cacheEnvVarName = getKeycloakOptionEnvVarName("cache");
        var healthEnvVarName = getKeycloakOptionEnvVarName("health-enabled");
        var cacheStackEnvVarName = getKeycloakOptionEnvVarName("cache-stack");
        var toRemove = Set.of(cacheEnvVarName, healthEnvVarName, cacheStackEnvVarName);
        envvars.removeIf(e -> toRemove.contains(e.getName()));

        // The Job should not connect to the cache
        envvars.add(new EnvVarBuilder().withName(cacheEnvVarName).withValue("local").build());
        // The Job doesn't need health to be enabled
        envvars.add(new EnvVarBuilder().withName(healthEnvVarName).withValue("false").build());

        return buildJob(keycloakPodTemplate, primary);
    }

    private Job buildJob(PodTemplateSpec keycloakPodTemplate, KeycloakRealmImport primary) {
        keycloakPodTemplate.getSpec().setRestartPolicy("Never");

        return new JobBuilder()
                .withNewMetadata()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                // this is labeling the instance as the realm import, not the keycloak
                .withLabels(Utils.allInstanceLabels(primary))
                .endMetadata()
                .withNewSpec()
                .withTemplate(keycloakPodTemplate)
                .endSpec()
                .build();
    }

    private Volume buildSecretVolume(String volumeName, String secretName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withSecret(new SecretVolumeSourceBuilder()
                        .withSecretName(secretName)
                        .build())
                .build();
    }

    private void buildKeycloakJobContainer(Container keycloakContainer, KeycloakRealmImport keycloakRealmImport, String volumeName) {
        var importMntPath = "/mnt/realm-import/";

        var command = List.of("/bin/bash");

        var override = "--override=false";

        var runBuild = !keycloakContainer.getArgs().contains(KeycloakDeploymentDependentResource.OPTIMIZED_ARG) ? "/opt/keycloak/bin/kc.sh --verbose build && " : "";

        var commandArgs = List.of("-c",
                runBuild + "/opt/keycloak/bin/kc.sh --verbose import --optimized --file='" + importMntPath + keycloakRealmImport.getRealmName() + "-realm.json' " + override);

        keycloakContainer.setCommand(command);
        keycloakContainer.setArgs(commandArgs);
        var volumeMount = new VolumeMountBuilder()
            .withName(volumeName)
            .withReadOnly(true)
            .withMountPath(importMntPath)
            .build();

        keycloakContainer.getVolumeMounts().add(volumeMount);

        // Disable probes since we are not really starting the server
        keycloakContainer.setReadinessProbe(null);
        keycloakContainer.setLivenessProbe(null);

        addResources(keycloakRealmImport.getSpec().getResourceRequirements(), config, keycloakContainer);
    }
}
