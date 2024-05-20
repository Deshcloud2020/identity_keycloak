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

package org.keycloak.theme.beans;

import org.keycloak.models.RealmModel;

import jakarta.ws.rs.core.UriBuilder;
import java.text.Collator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class LocaleBean {

    private String current;
    private String currentLanguageTag;
    private List<Locale> supported;

    public LocaleBean(RealmModel realm, java.util.Locale current, UriBuilder uriBuilder, Properties messages) {
        this.currentLanguageTag = current.toLanguageTag();
        this.current = messages.getProperty("locale_" + this.currentLanguageTag, this.currentLanguageTag);

        Collator collator = Collator.getInstance(current);
        collator.setStrength(Collator.PRIMARY); // ignore case and accents

        supported = realm.getSupportedLocalesStream()
                .map(l -> {
                    String label = messages.getProperty("locale_" + l, l);
                    String url = uriBuilder.replaceQueryParam("kc_locale", l).build().toString();
                    return new Locale(l, label, url);
                })
                .sorted((o1, o2) -> collator.compare(o1.label, o2.label))
                .collect(Collectors.toList());
    }

    public String getCurrent() {
        return current;
    }

    public String getCurrentLanguageTag() {
        return currentLanguageTag;
    }

    public List<Locale> getSupported() {
        return supported;
    }

    public static class Locale {

        private String languageTag;
        private String label;
        private String url;

        public Locale(String languageTag, String label, String url) {
            this.languageTag = languageTag;
            this.label = label;
            this.url = url;
        }

        public String getLanguageTag() {
            return languageTag;
        }

        public String getUrl() {
            return url;
        }

        public String getLabel() {
            return label;
        }

    }

}
