package io.github.devoracode.feignmock.spel;

import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.StandardTypeLocator;

/**
 * SpEL type locator that rejects all type references.
 *
 * @author Wenjie Liu
 * @since 1.1.0
 */
final class RestrictedTypeLocator extends StandardTypeLocator {

	@Override
	public Class<?> findType(String typeName) {
		throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
	}

}
