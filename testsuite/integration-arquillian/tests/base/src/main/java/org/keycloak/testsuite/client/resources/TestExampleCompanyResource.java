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

package org.keycloak.testsuite.client.resources;

import org.keycloak.testsuite.domainextension.CompanyRepresentation;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@Path("/realms/{realmName}/example/companies")
@Consumes(MediaType.APPLICATION_JSON)
public interface TestExampleCompanyResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<CompanyRepresentation> getCompanies(@PathParam("realmName") String realmName);

    @GET
    @Path("/{companyId}")
    @Produces(MediaType.APPLICATION_JSON)
    CompanyRepresentation getCompany(@PathParam("realmName") String realmName, @PathParam("companyId") String companyId);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response createCompany(@PathParam("realmName") String realmName, CompanyRepresentation rep);

    @DELETE
    void deleteAllCompanies(@PathParam("realmName") String realmName);
}