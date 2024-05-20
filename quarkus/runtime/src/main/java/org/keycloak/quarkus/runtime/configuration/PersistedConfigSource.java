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

package org.keycloak.quarkus.runtime.configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.smallrye.config.PropertiesConfigSource;
import org.keycloak.quarkus.runtime.Environment;

/**
 * A {@link org.eclipse.microprofile.config.spi.ConfigSource} based on the configuration properties persisted into the server
 * image.
 */
public final class PersistedConfigSource extends PropertiesConfigSource {

    public static final String NAME = "PersistedConfigSource";
    public static final String PERSISTED_PROPERTIES = "META-INF/keycloak-persisted.properties";
    private static final PersistedConfigSource INSTANCE = new PersistedConfigSource();

    /**
     * MicroProfile Config does not allow removing a config source when resolving properties. In order to be able
     * to resolve the current (not the persisted value) value for a property, even if not explicitly set at runtime, we need
     * to ignore this config source. Otherwise, default values are not resolved at runtime because the property will be
     * resolved from this config source, if persisted.
     */
    private static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> true);

    private PersistedConfigSource() {
        super(readProperties(), "", 200);
    }

    public static PersistedConfigSource getInstance() {
        return INSTANCE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getValue(String propertyName) {
        if (isEnabled()) {
            String value = super.getValue(propertyName);

            if (value != null) {
                return value;
            }

            return super.getValue(propertyName.replace(Configuration.OPTION_PART_SEPARATOR_CHAR, '.'));
        }

        return null;
    }

    @Override
    public Set<String> getPropertyNames() {
        if (isEnabled()) {
            return super.getPropertyNames();
        }

        return Set.of();
    }

    private static Map<String, String> readProperties() {
        if (Environment.isRuntimeMode()) {
            InputStream fileStream = loadPersistedConfig();

            if (fileStream == null) {
                return Collections.emptyMap();
            }

            try (fileStream) {
                Properties properties = new Properties();

                properties.load(fileStream);

                Map<String, String> props = new HashMap<>();

                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    props.put(entry.getKey().toString(), entry.getValue().toString());
                }

                return props;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load persisted properties.", e);
            }
        }

        return Collections.emptyMap();
    }

    private static InputStream loadPersistedConfig() {
        Path homePath = Environment.getHomePath();

        if (homePath == null) {
            return null;
        }

        File configFile = homePath.resolve("lib").resolve("quarkus").resolve("generated-bytecode.jar").toFile();

        if (!configFile.exists()) {
            return null;
        }

        try (ZipInputStream is = new ZipInputStream(new FileInputStream(configFile))) {
            ZipEntry entry;

            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals(PERSISTED_PROPERTIES)) {
                    return new ByteArrayInputStream(is.readAllBytes());
                }
            }
        } catch (Exception cause) {
            throw new RuntimeException("Failed to load persisted properties from " + configFile, cause);
        }

        return null;
    }

    public void enable() {
        ENABLED.set(true);
    }

    public void disable() {
        ENABLED.set(false);
    }

    private boolean isEnabled() {
        return Boolean.TRUE.equals(ENABLED.get());
    }

    public <T> T runWithDisabled(Supplier<T> execution) {
        try {
            disable();
            return execution.get();
        } finally {
            enable();
        }
    }
}
