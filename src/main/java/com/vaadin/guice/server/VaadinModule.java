/*
 * Copyright 2000-2017 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.guice.server;

import com.google.inject.AbstractModule;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.guice.annotation.UIScope;
import com.vaadin.guice.annotation.VaadinSessionScope;

class VaadinModule extends AbstractModule {
    
    private final com.vaadin.guice.server.UIScope uiScope;
    private final com.vaadin.guice.server.VaadinSessionScope vaadinSessionScope;

    private Class<? extends I18NProvider> i18NProviderClass;
    
    VaadinModule(com.vaadin.guice.server.UIScope uiScope, com.vaadin.guice.server.VaadinSessionScope vaadinSessionScope) {
        this.uiScope = uiScope;
        this.vaadinSessionScope = vaadinSessionScope;
    }

    @Override
    protected void configure() {
        bindScope(UIScope.class, uiScope);
        bindScope(VaadinSessionScope.class, vaadinSessionScope);
        
        if (getI18NProviderClass()  != null) {
            bind(I18NProvider.class).to(getI18NProviderClass());
        }
    }
    
    @SuppressWarnings("WeakerAccess")
	Class<? extends I18NProvider> getI18NProviderClass() {
        return i18NProviderClass;
    }
    
    void setI18NProviderClass(Class<? extends I18NProvider> i18NProviderClass) {
        this.i18NProviderClass = i18NProviderClass;
    }
    
}
