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

package org.keycloak.testsuite.pages;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class IdpLinkEmailPage extends AbstractPage {

    @FindBy(id = "instruction1")
    private WebElement message;

    @FindBy(linkText = "Click here")
    private WebElement resendEmailLink;

    @Override
    public boolean isCurrent() {
        return PageUtils.getPageTitle(driver).startsWith("Link ");
    }

    @Override
    public void open() throws Exception {
        throw new UnsupportedOperationException();
    }

    public String getMessage() {
        return message.getText();
    }

    public void resendEmail() {
        resendEmailLink.click();
    }
}
