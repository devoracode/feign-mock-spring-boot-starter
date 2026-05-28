package io.github.feignmock.provider;

import io.github.feignmock.exception.MockDataException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link MockDataProvider} 注册中心。
 *
 * <p>获取 Provider 实例的策略（优先级从高到低）：
 * <ol>
 *   <li>从 Spring 容器获取（支持 {@code @Autowired} 等依赖注入）</li>
 *   <li>反射调用无参构造创建（轻量场景，无 Spring 管理）</li>
 * </ol>
 *
 * <p>实例会被缓存以避免重复创建，对于 Spring Bean 则由容器管理生命周期。
 *
 * @author Wenjie
 * @since 1.0.0
 */
public class MockProviderRegistry implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /** 非 Spring 管理的 Provider 实例缓存 */
    @SuppressWarnings("rawtypes")
    private final Map<Class, MockDataProvider> instanceCache = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    /**
     * 获取指定类型的 {@link MockDataProvider} 实例。
     *
     * @param providerClass Provider 实现类
     * @return Provider 实例，永不为 null
     * @throws MockDataException 无法实例化时抛出
     */
    public MockDataProvider getProvider(Class<? extends MockDataProvider> providerClass) {
        // 1. 优先从 Spring 容器获取（享受完整的 IoC 支持）
        try {
            return applicationContext.getBean(providerClass);
        } catch (BeansException ignored) {
            // 容器中未注册，降级为反射创建
        }
        // 2. 反射创建并缓存（computeIfAbsent 保证并发安全，不会重复创建）
        @SuppressWarnings("unchecked")
        MockDataProvider result = instanceCache.computeIfAbsent(providerClass, cls -> {
            try {
                return (MockDataProvider) cls.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new MockDataException(
                        String.format(
                                "Failed to instantiate MockDataProvider [%s]. " +
                                        "Ensure the class has a public no-arg constructor, or register it as a Spring Bean.",
                                cls.getName()), e);
            }
        });
        return result;
    }
}
