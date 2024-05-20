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

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class LoginUpdateProfileEditUsernameAllowedPage extends LoginUpdateProfilePage {

    @FindBy(id = "username")
    private WebElement usernameInput;

    public Update prepareUpdate() {
        return new Update(this);
    }

    public String getUsername() {
        return usernameInput.getAttribute("value");
    }

    public boolean isCurrent() {
        return PageUtils.getPageTitle(driver).equals("Update Account Information");
    }
    
    public boolean isUsernamePresent() {
        try {
            return driver.findElement(By.id("username")).isDisplayed();
        } catch (NoSuchElementException nse) {
            return false;
        }
    }

    @Override
    public void open() {
        throw new UnsupportedOperationException();
    }

    public static class Update extends LoginUpdateProfilePage.Update {

        private final LoginUpdateProfileEditUsernameAllowedPage page;
        private String username;

        protected Update(LoginUpdateProfileEditUsernameAllowedPage page) {
            super(page);
            this.page = page;
        }

        public Update username(String username) {
            this.username = username;
            return this;
        }

        @Override
        public void submit() {
            if (username != null) {
                page.usernameInput.clear();
                page.usernameInput.sendKeys(username);
            }
            super.submit();
        }
    }

}
