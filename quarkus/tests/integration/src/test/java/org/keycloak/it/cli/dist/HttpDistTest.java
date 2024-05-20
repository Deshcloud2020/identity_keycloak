/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.TestProvider;
import org.keycloak.it.resource.realm.TestRealmResourceTestProvider;
import org.keycloak.it.utils.KeycloakDistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
@DistributionTest(keepAlive = true)
@RawDistOnly(reason = "Containers are immutable")
public class HttpDistTest {
    @Test
    @TestProvider(TestRealmResourceTestProvider.class)
    public void maxQueuedRequestsTest(KeycloakDistribution dist) {
        dist.run("start-dev", "--http-max-queued-requests=1", "--http-pool-max-threads=1");

        // run requests async
        List<CompletableFuture<Integer>> statusCodesFuture = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            statusCodesFuture.add(CompletableFuture.supplyAsync(() ->
                    when().get("/realms/master/test-resources/slow").getStatusCode()));
        }
        List<Integer> statusCodes = statusCodesFuture.stream().map(CompletableFuture::join).toList();

        assertThat("Some of the requests should be properly rejected", statusCodes, hasItem(503));
        assertThat("None of the requests should throw an unhandled exception", statusCodes, not(hasItem(500)));
    }
}
