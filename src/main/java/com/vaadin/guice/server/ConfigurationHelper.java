package com.vaadin.guice.server;

import com.vaadin.guice.annotation.PackagesToScan;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

public class ConfigurationHelper {
	
	static String[] getPackagesToScan(Class<?> annotatedClass, Function<String, @Nullable String> parameterResolver) {
		final String initParameter = parameterResolver.apply("packagesToScan");
		
		final String[] packagesToScan;
		
		final boolean annotationPresent = annotatedClass.isAnnotationPresent(PackagesToScan.class);
		
		if (!isNullOrEmpty(initParameter)) {
			checkState(
					!annotationPresent,
					"%s has both @PackagesToScan-annotation and an 'packagesToScan'-initParam",
					annotatedClass
			);
			packagesToScan = initParameter.split(",");
		} else if (annotationPresent) {
			packagesToScan = annotatedClass.getAnnotation(PackagesToScan.class).value();
		} else {
			throw new IllegalStateException("no packagesToScan-initParameter found and no @PackagesToScan-annotation present, please configure the packages to be scanned");
		}
		return packagesToScan;
	}
}
