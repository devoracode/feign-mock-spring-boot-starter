package io.github.devoracode.feignmock.provider;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Supplies mock data for a Feign client method.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
@FunctionalInterface
public interface MockDataProvider {

	/**
	 * Provide mock data for the intercepted method call.
	 * @param joinPoint the intercepted join point
	 * @param method the intercepted method
	 * @return the mock response
	 */
	Object provide(ProceedingJoinPoint joinPoint, Method method);

	/**
	 * Sentinel type indicating that no provider has been configured.
	 */
	final class None implements MockDataProvider {

		private None() {
			throw new UnsupportedOperationException("MockDataProvider.None is a sentinel type");
		}

		@Override
		public Object provide(ProceedingJoinPoint joinPoint, Method method) {
			throw new UnsupportedOperationException();
		}

	}

}
