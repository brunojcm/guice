package com.vaadin.guice.server;

import com.google.common.collect.Iterables;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.servlet.GuiceServletContextListener;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.guice.annotation.Import;
import com.vaadin.guice.annotation.OverrideBindings;
import org.reflections.Reflections;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.Guice.createInjector;
import static com.google.inject.util.Modules.override;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

public class GuiceVaadinListener extends GuiceServletContextListener {
	
	private final UIScope uiScope = new UIScope();
	private final VaadinSessionScope vaadinSessionScope = new VaadinSessionScope();
	
	private ServletContextEvent servletContextEvent;
	
	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		this.servletContextEvent = servletContextEvent;
		super.contextInitialized(servletContextEvent);
	}
	
	@Override
	protected Injector getInjector() {
		
		final String[] packagesToScan = ConfigurationHelper.getPackagesToScan(getClass(), p -> servletContextEvent.getServletContext().getInitParameter(p));
		
		Reflections reflections = new Reflections((Object[]) packagesToScan);
		
		final Set<Annotation> importAnnotations = stream(getClass().getAnnotations())
				.filter(annotation -> annotation.annotationType().isAnnotationPresent(Import.class))
				.collect(toSet());
		
		//import packages
		importAnnotations
				.stream()
				.map(annotation -> annotation.annotationType().getAnnotation(Import.class))
				.filter(i -> i.packagesToScan().length != 0)
				.forEach(i -> reflections.merge(new Reflections((Object[]) i.packagesToScan())));
		
		//import modules
		final Set<Module> modulesFromAnnotations = importAnnotations
				.stream()
				.map(annotation -> createModule(annotation.annotationType().getAnnotation(Import.class).value(), reflections, annotation, this.servletContextEvent.getServletContext()))
				.collect(toSet());
		
		final Set<Module> modulesFromPath = filterTypes(reflections.getSubTypesOf(Module.class))
				.stream()
				.filter(moduleClass -> !VaadinModule.class.equals(moduleClass))
				.map(moduleClass -> createModule(moduleClass, reflections, null, this.servletContextEvent.getServletContext()))
				.collect(toSet());
		
		List<Module> nonOverrideModules = new ArrayList<>();
		List<Module> overrideModules = new ArrayList<>();
		
		for (Module module : Iterables.concat(modulesFromAnnotations, modulesFromPath)) {
			if (module.getClass().isAnnotationPresent(OverrideBindings.class)) {
				overrideModules.add(module);
			} else {
				nonOverrideModules.add(module);
			}
		}
		
		/*
		 * combine bindings from the static modules in {@link GuiceVaadinServletConfiguration#modules()} with those bindings
		 * from dynamically loaded modules, see {@link RuntimeModule}.
		 * This is done first so modules can install their own reflections.
		 */
		Module combinedModules = override(nonOverrideModules).with(overrideModules);
		
		VaadinModule vaadinModule = new VaadinModule(uiScope, vaadinSessionScope);
		
		Set<Class<? extends I18NProvider>> i18NProviders = filterTypes(reflections.getSubTypesOf(I18NProvider.class));
		
		checkState(
				i18NProviders.size() < 2,
				"More than one I18NProvider found in Path: {}",
				i18NProviders.stream().map(Class::toGenericString).collect(joining(", "))
		);
		
		if (!i18NProviders.isEmpty()) {
			Class<? extends I18NProvider> i18NProviderClass = getOnlyElement(i18NProviders);
			vaadinModule.setI18NProviderClass(i18NProviderClass);
		}
		
		return createInjector(vaadinModule, combinedModules);
	}
	
	
	
	private <U> Set<Class<? extends U>> filterTypes(Set<Class<? extends U>> types) {
		return types
				.stream()
				.filter(t -> !isAbstract(t.getModifiers()))
				.collect(toSet());
	}
	
	private Module createModule(Class<? extends Module> moduleClass, Reflections reflections, Annotation annotation,
								ServletContext servletContext) {
		
		for (Constructor<?> constructor : moduleClass.getDeclaredConstructors()) {
			
			Object[] initArgs = new Object[constructor.getParameterCount()];
			
			Class<?>[] parameterTypes = constructor.getParameterTypes();
			
			boolean allParameterTypesResolved = true;
			
			for (int i = 0; i < parameterTypes.length; i++) {
				Class<?> parameterType = parameterTypes[i];
				
				if (Reflections.class.equals(parameterType)) {
					initArgs[i] = reflections;
				} else if (
						Provider.class.isAssignableFrom(parameterType) &&
								((ParameterizedType) parameterType.getGenericSuperclass()).getActualTypeArguments()[0].equals(Injector.class)
				) {
					initArgs[i] = (Provider<Injector>) this::getInjector;
				} else if (annotation != null && annotation.annotationType().equals(parameterType)) {
					initArgs[i] = annotation;
				} else if (ServletContext.class.equals(parameterType)) {
					initArgs[i] = servletContext;
				} else {
					allParameterTypesResolved = false;
					break;
				}
			}
			
			if (!allParameterTypesResolved) {
				continue;
			}
			
			constructor.setAccessible(true);
			
			try {
				return (Module) constructor.newInstance(initArgs);
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
		
		throw new IllegalStateException("no suitable constructor found for %s" + moduleClass);
	}
	
}
