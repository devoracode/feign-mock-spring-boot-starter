package io.github.devoracode.feignmock.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.devoracode.feignmock.exception.MockDataException;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Registry for {@link MockDataProvider} instances.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public class MockDataProviderRegistry implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	private final Map<Class<?>, MockDataProvider> instanceCache = new ConcurrentHashMap<>();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public MockDataProvider getProvider(Class<? extends MockDataProvider> providerClass) {
		try {
			return this.applicationContext.getBean(providerClass);
		}
		catch (BeansException ex) {
			return this.instanceCache.computeIfAbsent(providerClass, this::instantiateProvider);
		}
	}

	private MockDataProvider instantiateProvider(Class<?> providerClass) {
		try {
			return (MockDataProvider) providerClass.getDeclaredConstructor().newInstance();
		}
		catch (Exception ex) {
			throw new MockDataException(String.format(
					"Failed to instantiate MockDataProvider [%s]. "
							+ "Ensure the class has a public no-arg constructor, or register it as a Spring bean.",
					providerClass.getName()), ex);
		}
	}

}
