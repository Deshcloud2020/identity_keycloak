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
package org.keycloak.saml.processing.core.parsers.saml.assertion;

import org.keycloak.dom.saml.v2.assertion.EncryptedAssertionType;
import org.keycloak.saml.common.exceptions.ParsingException;
import org.keycloak.saml.common.parsers.StaxParser;
import org.keycloak.saml.common.util.StaxParserUtil;
import javax.xml.stream.XMLEventReader;

public class SAMLEncryptedAssertionParser implements StaxParser {

    private static final SAMLEncryptedAssertionParser INSTANCE = new SAMLEncryptedAssertionParser();

    public static SAMLEncryptedAssertionParser getInstance() {
        return INSTANCE;
    }

    @Override
    public EncryptedAssertionType parse(XMLEventReader xmlEventReader) throws ParsingException {
        EncryptedAssertionType res = new EncryptedAssertionType();
        res.setEncryptedElement(StaxParserUtil.getDOMElement(xmlEventReader));
        return res;
    }
}
