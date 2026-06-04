package io.github.devoracode.feignmock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.devoracode.feignmock.exception.MockDataException;
import io.github.devoracode.feignmock.provider.MockDataProvider;

/**
 * Marks a Feign client method whose response should be mocked.
 *
 * <p>Resolution order:
 * <ol>
 * <li>{@link #provider()}</li>
 * <li>{@link #jsonFile()}</li>
 * <li>{@link #value()}</li>
 * </ol>
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MockMethod {

	/**
	 * Mock data key used for property and resource lookup.
	 * @return the mock data key, or an empty value to derive one automatically
	 */
	String value() default "";

	/**
	 * Classpath JSON resource used as the mock response.
	 * @return the resource path, or an empty value to disable direct resource lookup
	 */
	String jsonFile() default "";

	/**
	 * Custom mock data provider.
	 * @return the provider type, or {@link MockDataProvider.None} when not used
	 */
	Class<? extends MockDataProvider> provider() default MockDataProvider.None.class;

	/**
	 * Whether unresolved mock data should fail fast.
	 * @return {@code true} to throw {@link MockDataException}, otherwise {@code false}
	 */
	boolean failFast() default true;

	/**
	 * Optional description used in log output.
	 * @return the description text
	 */
	String description() default "";

}
