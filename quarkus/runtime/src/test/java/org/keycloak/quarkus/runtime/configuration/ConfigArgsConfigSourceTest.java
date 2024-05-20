/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConfigArgsConfigSourceTest {

    @Test
    public void testParseArgs() {
        List<String> values = new ArrayList<>();
        ConfigArgsConfigSource.parseConfigArgs(Arrays.asList("--key=value", "-cf", "file", "command", "arg", "--db", "value"), (key, value) -> values.add(key+'='+value), values::add);
        assertEquals(Arrays.asList("--key=value", "-cf=file", "command", "arg", "--db=value"), values);
    }

}
