package io.github.devoracode.feignmock.spel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;

/**
 * Method resolver that only allows {@code contains} on {@link String} and {@link Collection}.
 *
 * @author Wenjie Liu
 * @since 1.1.0
 */
final class RestrictedMethodResolver implements MethodResolver {

	private static final Set<String> ALLOWED_METHODS;

	static {
		Set<String> allowed = new HashSet<>();
		allowed.add("contains");
		ALLOWED_METHODS = Collections.unmodifiableSet(allowed);
	}

	private final ReflectiveMethodResolver delegate = new ReflectiveMethodResolver();

	@Override
	public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name,
			List<TypeDescriptor> argumentTypes) throws AccessException {
		if (targetObject == null || !ALLOWED_METHODS.contains(name)) {
			return null;
		}
		Class<?> targetType = targetObject.getClass();
		if (String.class.isAssignableFrom(targetType) || Collection.class.isAssignableFrom(targetType)) {
			return this.delegate.resolve(context, targetObject, name, argumentTypes);
		}
		return null;
	}

}
