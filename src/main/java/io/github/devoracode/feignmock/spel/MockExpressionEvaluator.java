package io.github.devoracode.feignmock.spel;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.devoracode.feignmock.exception.MockDataException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * Evaluates restricted SpEL expressions used to resolve mock JSON resource paths.
 *
 * @author Wenjie Liu
 * @since 1.1.0
 */
public class MockExpressionEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(MockExpressionEvaluator.class);

	public static final String EXPRESSION_PREFIX = "#";

	private static final Method SWITCH_METHOD;

	private static final Method CONTAINS_METHOD;

	private static final Method CHOOSE_METHOD;

	static {
		try {
			SWITCH_METHOD = MockSpelFunctions.class.getMethod("switchOf", Object.class, Object[].class);
			CONTAINS_METHOD = MockSpelFunctions.class.getMethod("containsOf", Object.class, Object.class);
			CHOOSE_METHOD = MockSpelFunctions.class.getMethod("chooseOf", Object[].class);
		}
		catch (NoSuchMethodException ex) {
			throw new MockDataException("Failed to register SpEL functions", ex);
		}
	}

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

	public static boolean isExpression(String expression) {
		return StringUtils.hasText(expression) && expression.trim().startsWith(EXPRESSION_PREFIX);
	}

	public String evaluate(String expression, ProceedingJoinPoint joinPoint) {
		String normalizedExpression = normalize(expression);
		logger.debug("Evaluating Feign mock expression: {}", normalizedExpression);

		StandardEvaluationContext context = buildEvaluationContext(joinPoint);
		try {
			Object value = getExpression(normalizedExpression).getValue(context);
			if (value == null) {
				return null;
			}
			if (value instanceof String) {
				return (String) value;
			}
			return String.valueOf(value);
		}
		catch (Exception ex) {
			MockDataException mockDataException = findMockDataException(ex);
			if (mockDataException != null) {
				throw mockDataException;
			}
			throw new MockDataException(
					"Failed to evaluate mock expression: [" + expression + "]. Cause: " + ex.getMessage(), ex);
		}
	}

	private MockDataException findMockDataException(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof MockDataException) {
				return (MockDataException) current;
			}
			current = current.getCause();
		}
		return null;
	}

	private Expression getExpression(String expression) {
		return this.expressionCache.computeIfAbsent(expression, this.parser::parseExpression);
	}

	private String normalize(String rawExpression) {
		if (!StringUtils.hasText(rawExpression)) {
			return "";
		}
		String expression = rawExpression.trim();
		if (expression.startsWith("#{") && expression.endsWith("}")) {
			return expression.substring(2, expression.length() - 1);
		}
		return expression;
	}

	private StandardEvaluationContext buildEvaluationContext(ProceedingJoinPoint joinPoint) {
		StandardEvaluationContext context = new StandardEvaluationContext();
		context.setTypeLocator(new RestrictedTypeLocator());
		context.setConstructorResolvers(Collections.emptyList());
		context.setPropertyAccessors(Arrays.asList(new MapAccessor(), new RestrictedPropertyAccessor()));
		context.setMethodResolvers(Collections.singletonList(new RestrictedMethodResolver()));

		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Object[] args = joinPoint.getArgs();

		for (int i = 0; i < args.length; i++) {
			context.setVariable("p" + i, args[i]);
		}

		String[] parameterNames = signature.getParameterNames();
		if (parameterNames == null || parameterNames.length == 0) {
			parameterNames = resolveParameterNamesFromInterface(signature.getMethod());
		}
		if (parameterNames != null) {
			for (int i = 0; i < Math.min(parameterNames.length, args.length); i++) {
				if (StringUtils.hasText(parameterNames[i])) {
					context.setVariable(parameterNames[i], args[i]);
				}
			}
		}

		registerFunctions(context);
		return context;
	}

	private void registerFunctions(StandardEvaluationContext context) {
		context.registerFunction("switch", SWITCH_METHOD);
		context.registerFunction("contains", CONTAINS_METHOD);
		context.registerFunction("choose", CHOOSE_METHOD);
	}

	private String[] resolveParameterNamesFromInterface(Method implementationMethod) {
		Class<?> declaringClass = implementationMethod.getDeclaringClass();
		for (Class<?> iface : declaringClass.getInterfaces()) {
			try {
				Method interfaceMethod = iface.getMethod(implementationMethod.getName(),
						implementationMethod.getParameterTypes());
				java.lang.reflect.Parameter[] parameters = interfaceMethod.getParameters();
				if (parameters.length > 0 && parameters[0].isNamePresent()) {
					String[] names = new String[parameters.length];
					for (int i = 0; i < parameters.length; i++) {
						names[i] = parameters[i].getName();
					}
					return names;
				}
			}
			catch (NoSuchMethodException ex) {
				// Continue with the next interface.
			}
		}
		return null;
	}

}
