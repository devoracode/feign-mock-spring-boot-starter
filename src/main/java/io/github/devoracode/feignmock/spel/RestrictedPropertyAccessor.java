package io.github.devoracode.feignmock.spel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;

/**
 * Property accessor that blocks access to sensitive Java object properties.
 *
 * @author Wenjie Liu
 * @since 1.1.0
 */
final class RestrictedPropertyAccessor extends ReflectivePropertyAccessor {

	private static final Set<String> DENIED_PROPERTIES;

	static {
		Set<String> denied = new HashSet<>();
		denied.add("class");
		denied.add("classLoader");
		denied.add("protectionDomain");
		DENIED_PROPERTIES = Collections.unmodifiableSet(denied);
	}

	@Override
	public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
		if (DENIED_PROPERTIES.contains(name)) {
			return false;
		}
		return super.canRead(context, target, name);
	}

}
