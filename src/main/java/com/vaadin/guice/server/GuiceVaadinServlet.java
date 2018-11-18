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

import com.google.inject.Injector;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.*;
import org.reflections.Reflections;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static java.lang.reflect.Modifier.isAbstract;
import static java.util.stream.Collectors.toSet;

/**
 * Subclass of the standard {@link com.vaadin.flow.server.VaadinServlet Vaadin servlet}
 *
 * @author Bernd Hopp (bernd@vaadin.com)
 */
@SuppressWarnings("unused")
public class GuiceVaadinServlet extends VaadinServlet {
    
    private Injector injector;
    private final Set<Class<? extends SessionInitListener>> sessionInitListenerClasses = new HashSet<>();
    private final Set<Class<? extends SessionDestroyListener>> sessionDestroyListenerClasses = new HashSet<>();
    private final Set<Class<? extends ServiceDestroyListener>> serviceDestroyListeners = new HashSet<>();
    private final Set<Class<? extends UI>> uiClasses = new HashSet<>();
    private final Set<Class<? extends RequestHandler>> requestHandlerClasses = new HashSet<>();
    private final Set<Class<? extends VaadinServiceInitListener>> vaadinServiceInitListenerClasses = new HashSet<>();
    
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
    
		final String[] packagesToScan = ConfigurationHelper.getPackagesToScan(getClass(), p -> servletConfig.getInitParameter(p));

        Reflections reflections = new Reflections((Object[]) packagesToScan);

        this.uiClasses.addAll(filterTypes(reflections.getSubTypesOf(UI.class)));
        this.vaadinServiceInitListenerClasses.addAll(filterTypes(reflections.getSubTypesOf(VaadinServiceInitListener.class)));
        this.requestHandlerClasses.addAll(filterTypes(reflections.getSubTypesOf(RequestHandler.class)));
        this.sessionInitListenerClasses.addAll(filterTypes(reflections.getSubTypesOf(SessionInitListener.class)));
        this.sessionDestroyListenerClasses.addAll(filterTypes(reflections.getSubTypesOf(SessionDestroyListener.class)));
        this.serviceDestroyListeners.addAll(filterTypes(reflections.getSubTypesOf(ServiceDestroyListener.class)));
    
        this.injector = (Injector) servletConfig.getServletContext().getAttribute(Injector.class.getName());
        
        super.init(servletConfig);
    }
    
    Injector getInjector() {
        return this.injector;
    }

    private <U> Set<Class<? extends U>> filterTypes(Set<Class<? extends U>> types) {
        return types
                .stream()
                .filter(t -> !isAbstract(t.getModifiers()))
                .collect(toSet());
    }

    @Override
    protected void servletInitialized() {
        final VaadinService vaadinService = VaadinService.getCurrent();

        vaadinService.addSessionInitListener(this::sessionInit);

        sessionInitListenerClasses
                .stream()
                .map(injector::getInstance)
                .forEach(vaadinService::addSessionInitListener);

        sessionDestroyListenerClasses
                .stream()
                .map(injector::getInstance)
                .forEach(vaadinService::addSessionDestroyListener);

        serviceDestroyListeners
                .stream()
                .map(injector::getInstance)
                .forEach(vaadinService::addServiceDestroyListener);
    }

    @Override
    protected VaadinServletService createServletService(DeploymentConfiguration deploymentConfiguration) throws ServiceException {
        final GuiceVaadinServletService guiceVaadinServletService = new GuiceVaadinServletService(this, deploymentConfiguration);

        guiceVaadinServletService.init();

        return guiceVaadinServletService;
    }

    private void sessionInit(SessionInitEvent event) {
        VaadinSession session = event.getSession();

        requestHandlerClasses
                .stream()
                .map(injector::getInstance)
                .forEach(session::addRequestHandler);
    }

    Set<Class<? extends UI>> getUiClasses() {
        return uiClasses;
    }

    Iterator<VaadinServiceInitListener> getServiceInitListeners() {
        return vaadinServiceInitListenerClasses
                .stream()
                .map(key -> (VaadinServiceInitListener) getInjector().getInstance(key))
                .iterator();
    }


}
