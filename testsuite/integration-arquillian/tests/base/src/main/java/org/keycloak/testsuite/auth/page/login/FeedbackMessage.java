/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.auth.page.login;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.keycloak.testsuite.util.UIUtils.getTextFromElement;

/**
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 * @author Martin Bartos <mabartos@redhat.com>
 */
public class FeedbackMessage {

    private final String SUCCESS = "success";
    private final String WARNING = "warning";
    private final String ERROR = "error";
    private final String INFO = "info";

    @FindBy(css = "div[class^='alert']")
    private WebElement alertRoot;

    @FindBy(css = "span[id^='input-error']")
    private WebElement inputErrorRoot;

    public boolean isPresent() {
        try {
            return alertRoot.isDisplayed();
        } catch (NoSuchElementException e) {
            return getInputError() != null && !getInputError().isEmpty();
        }
    }

    public String getInputError() {
        try {
            return getTextFromElement(inputErrorRoot);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public String getText() {
        try {
            return getTextFromElement(alertRoot.findElement(By.className("kc-feedback-text")));
        } catch (NoSuchElementException e) {
            return getInputError();
        }
    }

    public String getType() {
        try {
            String cssClass = alertRoot.getAttribute("class");
            Matcher classMatcher = Pattern.compile("alert-(.+)").matcher(cssClass);
            if (!classMatcher.find()) {
                throw new RuntimeException("Failed to identify feedback message type");
            }
            return classMatcher.group(1);
        } catch (NoSuchElementException e) {
            return getInputError() != null ? ERROR : null;
        }
    }

    public boolean isSuccess() {
        return getType().contains(SUCCESS);
    }

    public boolean isWarning() {
        return getType().contains(WARNING);
    }

    public boolean isError() {
        return getType().contains(ERROR);
    }

    public boolean isInfo() {
        return getType().contains(INFO);
    }
}
