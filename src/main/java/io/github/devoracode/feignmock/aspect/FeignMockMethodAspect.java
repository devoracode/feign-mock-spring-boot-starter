package io.github.devoracode.feignmock.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.autoconfigure.FeignMockProperties;
import io.github.devoracode.feignmock.exception.MockDataException;
import io.github.devoracode.feignmock.mock.MockDataLoader;
import io.github.devoracode.feignmock.provider.MockDataProvider;
import io.github.devoracode.feignmock.provider.MockDataProviderRegistry;
import io.github.devoracode.feignmock.spel.MockExpressionEvaluator;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.StringUtils;

/**
 * Aspect that resolves mock responses for methods annotated with {@link MockMethod}.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
@Aspect
public class FeignMockMethodAspect {

	private static final Logger logger = LoggerFactory.getLogger(FeignMockMethodAspect.class);

	private final MockDataLoader mockDataLoader;

	private final MockDataProviderRegistry providerRegistry;

	private final MockExpressionEvaluator expressionEvaluator;

	private final FeignMockProperties properties;

	public FeignMockMethodAspect(MockDataLoader mockDataLoader, MockDataProviderRegistry providerRegistry,
			MockExpressionEvaluator expressionEvaluator, FeignMockProperties properties) {
		this.mockDataLoader = mockDataLoader;
		this.providerRegistry = providerRegistry;
		this.expressionEvaluator = expressionEvaluator;
		this.properties = properties;
	}

	@Around("@annotation(mockMethod)")
	public Object invokeMockMethod(ProceedingJoinPoint joinPoint, MockMethod mockMethod) throws Throwable {
		if (!this.properties.isEnabled()) {
			return joinPoint.proceed();
		}

		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		String logTag = resolveLogTag(mockMethod, method);

		logger.info("Intercepted Feign mock method [{}] using {}{}", logTag, resolveSourceLabel(mockMethod, method),
				StringUtils.hasText(mockMethod.description()) ? " (" + mockMethod.description() + ")" : "");

		try {
			return resolveMockResponse(joinPoint, method, mockMethod);
		}
		catch (MockDataException ex) {
			if (mockMethod.failFast()) {
				logger.error("Failed to resolve mock data for [{}]: {}", logTag, ex.getMessage());
				throw ex;
			}
			logger.warn("Failed to resolve mock data for [{}], returning null because failFast=false: {}", logTag,
					ex.getMessage());
			return null;
		}
	}

	private Object resolveMockResponse(ProceedingJoinPoint joinPoint, Method method, MockMethod mockMethod) {
		Type returnType = method.getGenericReturnType();

		if (mockMethod.provider() != MockDataProvider.None.class) {
			logger.debug("Resolving mock response using provider {}", mockMethod.provider().getSimpleName());
			MockDataProvider provider = this.providerRegistry.getProvider(mockMethod.provider());
			return provider.provide(joinPoint, method);
		}

		if (StringUtils.hasText(mockMethod.jsonFile())) {
			String filePath = mockMethod.jsonFile();
			if (MockExpressionEvaluator.isExpression(filePath)) {
				filePath = this.expressionEvaluator.evaluate(filePath, joinPoint);
				logger.debug("Resolved mock resource expression to {}", filePath);
			}
			else {
				logger.debug("Loading mock response from resource {}", filePath);
			}
			return this.mockDataLoader.loadByFile(filePath, returnType);
		}

		String key = StringUtils.hasText(mockMethod.value()) ? mockMethod.value() : buildAutoKey(method);
		logger.debug("Resolving mock response using key {}", key);
		return this.mockDataLoader.loadByKey(key, returnType);
	}

	private static String buildAutoKey(Method method) {
		String simpleName = method.getDeclaringClass().getSimpleName();
		String prefix = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
		return prefix + "." + method.getName();
	}

	private String resolveSourceLabel(MockMethod mockMethod, Method method) {
		if (mockMethod.provider() != MockDataProvider.None.class) {
			return "provider " + mockMethod.provider().getSimpleName();
		}
		if (StringUtils.hasText(mockMethod.jsonFile())) {
			return "resource " + mockMethod.jsonFile();
		}
		String key = StringUtils.hasText(mockMethod.value()) ? mockMethod.value() : buildAutoKey(method);
		return "key " + key;
	}

	private String resolveLogTag(MockMethod mockMethod, Method method) {
		if (StringUtils.hasText(mockMethod.description())) {
			return mockMethod.description();
		}
		return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
	}

}
