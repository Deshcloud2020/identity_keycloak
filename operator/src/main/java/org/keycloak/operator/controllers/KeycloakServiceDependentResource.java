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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import org.keycloak.operator.Constants;
import org.keycloak.operator.Utils;
import org.keycloak.operator.crds.v2alpha1.deployment.Keycloak;
import org.keycloak.operator.crds.v2alpha1.deployment.KeycloakSpec;
import org.keycloak.operator.crds.v2alpha1.deployment.spec.HttpSpec;
import org.keycloak.operator.crds.v2alpha1.deployment.spec.HttpManagementSpec;

import java.util.Optional;

import static org.keycloak.operator.crds.v2alpha1.CRDUtils.isTlsConfigured;

@KubernetesDependent(labelSelector = Constants.DEFAULT_LABELS_AS_STRING, resourceDiscriminator = KeycloakServiceDependentResource.NameResourceDiscriminator.class)
public class KeycloakServiceDependentResource extends CRUDKubernetesDependentResource<Service, Keycloak> {

    public static class NameResourceDiscriminator implements ResourceDiscriminator<Service, Keycloak> {
        @Override
        public Optional<Service> distinguish(Class<Service> resource, Keycloak primary, Context<Keycloak> context) {
            return Utils.getByName(Service.class, KeycloakServiceDependentResource::getServiceName, primary, context);
        }
    }

    public KeycloakServiceDependentResource() {
        super(Service.class);
    }

    private ServiceSpec getServiceSpec(Keycloak keycloak) {
        var builder = new ServiceSpecBuilder().withSelector(Utils.allInstanceLabels(keycloak));

        boolean tlsConfigured = isTlsConfigured(keycloak);
        Optional<HttpSpec> httpSpec = Optional.ofNullable(keycloak.getSpec().getHttpSpec());
        boolean httpEnabled = httpSpec.map(HttpSpec::getHttpEnabled).orElse(false);
        if (!tlsConfigured || httpEnabled) {
            builder.addNewPort()
                    .withPort(getServicePort(false, keycloak))
                    .withName(Constants.KEYCLOAK_HTTP_PORT_NAME)
                    .withProtocol(Constants.KEYCLOAK_SERVICE_PROTOCOL)
                    .endPort();
        }
        if (tlsConfigured) {
            builder.addNewPort()
                    .withPort(getServicePort(true, keycloak))
                    .withName(Constants.KEYCLOAK_HTTPS_PORT_NAME)
                    .withProtocol(Constants.KEYCLOAK_SERVICE_PROTOCOL)
                    .endPort();
        }

        var managementPort = Optional.ofNullable(keycloak.getSpec())
                .map(KeycloakSpec::getHttpManagementSpec)
                .map(HttpManagementSpec::getPort)
                .orElse(Constants.KEYCLOAK_MANAGEMENT_PORT);

        builder.addNewPort()
                .withPort(managementPort)
                .withName(Constants.KEYCLOAK_MANAGEMENT_PORT_NAME)
                .withProtocol(Constants.KEYCLOAK_SERVICE_PROTOCOL)
                .endPort();

        return builder.build();
    }

    @Override
    protected Service desired(Keycloak primary, Context<Keycloak> context) {
        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels(Utils.allInstanceLabels(primary))
                .endMetadata()
                .withSpec(getServiceSpec(primary))
                .build();
        return service;
    }

    public static String getServiceName(HasMetadata keycloak) {
        return keycloak.getMetadata().getName() + Constants.KEYCLOAK_SERVICE_SUFFIX;
    }

    public static int getServicePort(boolean tls, Keycloak keycloak) {
        Optional<HttpSpec> httpSpec = Optional.ofNullable(keycloak.getSpec().getHttpSpec());
        if (tls) {
            return httpSpec.map(HttpSpec::getHttpsPort).orElse(Constants.KEYCLOAK_HTTPS_PORT);
        }
        return httpSpec.map(HttpSpec::getHttpPort).orElse(Constants.KEYCLOAK_HTTP_PORT);
    }
}
