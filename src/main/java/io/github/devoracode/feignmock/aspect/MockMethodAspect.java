package io.github.devoracode.feignmock.aspect;

import io.github.devoracode.feignmock.annotation.MockMethod;
import io.github.devoracode.feignmock.config.MockProperties;
import io.github.devoracode.feignmock.el.MockElEvaluator;
import io.github.devoracode.feignmock.exception.MockDataException;
import io.github.devoracode.feignmock.loader.MockDataLoader;
import io.github.devoracode.feignmock.provider.MockDataProvider;
import io.github.devoracode.feignmock.provider.MockProviderRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * {@link MockMethod} 注解的 AOP 处理器。
 *
 * <p>拦截所有标注了 {@link MockMethod} 的方法，根据注解配置按优先级选择数据源：
 * <ol>
 *   <li>{@link MockMethod#provider()} 自定义 Provider</li>
 *   <li>{@link MockMethod#jsonFile()} 指定 JSON 文件</li>
 *   <li>{@link MockMethod#value()} key 查找配置文件（feign.mock.responses）</li>
 * </ol>
 *
 * <p>仅在 {@code feign.mock.enabled=true} 时激活（由自动配置条件控制）。
 *
 * @author Wenjie
 * @since 1.0.0
 */
@Aspect
public class MockMethodAspect {

    private static final Logger log = LoggerFactory.getLogger(MockMethodAspect.class);

    private final MockDataLoader mockDataLoader;
    private final MockProviderRegistry providerRegistry;
    private final MockElEvaluator elEvaluator;
    private final MockProperties mockProperties;

    public MockMethodAspect(MockDataLoader mockDataLoader,
                            MockProviderRegistry providerRegistry,
                            MockElEvaluator elEvaluator,
                            MockProperties mockProperties) {
        this.mockDataLoader = mockDataLoader;
        this.providerRegistry = providerRegistry;
        this.elEvaluator = elEvaluator;
        this.mockProperties = mockProperties;
    }

    @Around("@annotation(mockMethod)")
    public Object around(ProceedingJoinPoint pjp, MockMethod mockMethod) throws Throwable {
        // 全局开关关闭 → 直接放行，不产生任何额外开销
        if (!mockProperties.isEnabled()) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String logTag = resolveLogTag(mockMethod, method);

        log.info("[MockAOP] Intercepted [{}] | source={}{}", 
            logTag,
            resolveSourceLabel(mockMethod, method),
            StringUtils.hasText(mockMethod.description())
                ? " | desc=" + mockMethod.description() : "");

        try {
            return doResolve(pjp, method, mockMethod);
        } catch (MockDataException e) {
            if (mockMethod.failFast()) {
                log.error("[MockAOP] [{}] Failed to load mock data: {}", logTag, e.getMessage());
                throw e;
            }
            log.warn("[MockAOP] [{}] Failed to load mock data, failFast=false, returning null: {}",
                logTag, e.getMessage());
            return null;
        }
    }

    /**
     * 按优先级依次尝试三种数据源。
     */
    private Object doResolve(ProceedingJoinPoint pjp, Method method, MockMethod mockMethod) {
        Type returnType = method.getGenericReturnType();

        // ── 优先级 1：自定义 Provider ────────────────────────────────
        if (mockMethod.provider() != MockDataProvider.None.class) {
            log.debug("[MockAOP] Using provider: {}", mockMethod.provider().getSimpleName());
            MockDataProvider provider = providerRegistry.getProvider(mockMethod.provider());
            return provider.provide(pjp, method);
        }

        // ── 优先级 2：指定 JSON 文件 ─────────────────────────────────
        if (StringUtils.hasText(mockMethod.jsonFile())) {
            String filePath = mockMethod.jsonFile();
            if (MockElEvaluator.isExpression(filePath)) {
                filePath = elEvaluator.evaluate(filePath, pjp);
                log.debug("[MockAOP] EL expression resolved to file: {}", filePath);
            } else {
                log.debug("[MockAOP] Using JSON file: {}", filePath);
            }
            return mockDataLoader.loadByFile(filePath, returnType);
        }

        // ── 优先级 3：key 查找（配置yml(feign.mock.responses) → 本地文件） ────────────────────
        String key = StringUtils.hasText(mockMethod.value())
            ? mockMethod.value()
            : buildAutoKey(method);
        log.debug("[MockAOP] Using key lookup: {}", key);
        return mockDataLoader.loadByKey(key, returnType);
    }

    /**
     * 自动推导 Mock key：{@code interfaceSimpleName.methodName}。
     * <p>接口类名首字母小写后与方法名以 {@code .} 拼接，与 yml 配置 key 保持一致。
     * <pre>
     *   UserFeignClient#getUserById  →  userFeignClient.getUserById
     *   OrderClient#getOrder         →  orderClient.getOrder
     * </pre>
     */
    private static String buildAutoKey(Method method) {
        String simpleName = method.getDeclaringClass().getSimpleName();
        String prefix = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        return prefix + "." + method.getName();
    }

    private String resolveSourceLabel(MockMethod mockMethod, Method method) {
        if (mockMethod.provider() != MockDataProvider.None.class) {
            return "Provider(" + mockMethod.provider().getSimpleName() + ")";
        }
        if (StringUtils.hasText(mockMethod.jsonFile())) {
            return "JsonFile(" + mockMethod.jsonFile() + ")";
        }
        String key = StringUtils.hasText(mockMethod.value())
            ? mockMethod.value()
            : buildAutoKey(method);
        return "Key(" + key + ")";
    }

    private String resolveLogTag(MockMethod mockMethod, Method method) {
        if (StringUtils.hasText(mockMethod.description())) {
            return mockMethod.description();
        }
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
