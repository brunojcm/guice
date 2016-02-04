package com.vaadin.guice.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import com.vaadin.guice.annotation.GuiceUI;
import com.vaadin.guice.annotation.GuiceView;
import com.vaadin.guice.annotation.GuiceViewChangeListener;
import com.vaadin.guice.annotation.UIModule;
import com.vaadin.guice.annotation.UIScope;
import com.vaadin.navigator.ViewProvider;
import com.vaadin.server.DefaultUIProvider;
import com.vaadin.server.ServiceException;
import com.vaadin.server.SessionInitEvent;
import com.vaadin.server.SessionInitListener;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;

import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class VaadinModule extends AbstractModule {

    private final GuiceViewProvider viewProvider;
    private final GuiceUIProvider uiProvider;
    private final SessionBasedScoper uiScoper;

    public VaadinModule(SessionProvider sessionProvider, String... basePackages) throws IllegalAccessException {
        Reflections reflections = new Reflections(basePackages);

        Set<Class<?>> uis = reflections.getTypesAnnotatedWith(GuiceUI.class);
        Set<Class<?>> views = reflections.getTypesAnnotatedWith(GuiceView.class);
        Set<Class<?>> modules = reflections.getTypesAnnotatedWith(UIModule.class);
        Set<Class<?>> viewChangeListeners = reflections.getTypesAnnotatedWith(GuiceViewChangeListener.class);

        for(Class<?> moduleClass: modules){
            if(!Module.class.isAssignableFrom(moduleClass)){
                throw new IllegalArgumentException("@UIModule can only be attached to classes implementing com.google.inject.Module");
            }

            try {
                install((Module)moduleClass.newInstance());
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        uiScoper = new SessionBasedScoper(sessionProvider);
        viewProvider = new GuiceViewProvider(views);
        uiProvider = new GuiceUIProvider(uis, viewChangeListeners);
    }

    @Override
    protected void configure() {
        bindScope(UIScope.class, uiScoper);
        bindScope(GuiceView.class, uiScoper);
        bind(UIProvider.class).toInstance(uiProvider);
        bind(ViewProvider.class).toInstance(viewProvider);
    }

    public void vaadinInitialized(VaadinService service) {
        service.addSessionInitListener(new SessionInitListener() {
            @Override
            public void sessionInit(SessionInitEvent sessionInitEvent)
                    throws ServiceException {
                // remove DefaultUIProvider instances to avoid mapping
                // extraneous UIs if e.g. a servlet is declared as a nested
                // class in a UI class
                VaadinSession session = sessionInitEvent.getSession();
                List<UIProvider> uiProviders = new ArrayList<UIProvider>(
                        session.getUIProviders());
                for (UIProvider provider : uiProviders) {
                    // use canonical names as these may have been loaded with
                    // different classloaders
                    if (DefaultUIProvider.class.getCanonicalName().equals(
                            provider.getClass().getCanonicalName())) {
                        session.removeUIProvider(provider);
                    }
                }
            }
        });

        service.addSessionDestroyListener(uiScoper);
        service.addSessionInitListener(uiScoper);
        service.addSessionDestroyListener(viewProvider);
        service.addSessionInitListener(viewProvider);
        service.addSessionInitListener(uiProvider);
    }
}