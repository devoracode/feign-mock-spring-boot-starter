package io.github.devoracode.feignmock.provider;

import org.aspectj.lang.ProceedingJoinPoint;

import java.lang.reflect.Method;

/**
 * 自定义 Mock 数据提供者接口。
 *
 * <p>实现此接口可完全编程控制 Mock 数据的生成逻辑，适合：
 * <ul>
 *   <li>根据入参动态返回不同 Mock 数据</li>
 *   <li>模拟业务异常（直接抛出即可）</li>
 *   <li>需要注入其他 Spring Bean 参与逻辑</li>
 * </ul>
 *
 * <p>推荐将实现类声明为 Spring Bean，以便注入其他依赖：
 * <pre>
 * &#64;Component
 * public class UserMockProvider implements MockDataProvider {
 *
 *     &#64;Autowired
 *     private SomeRepository repo;
 *
 *     &#64;Override
 *     public Object provide(ProceedingJoinPoint pjp, Method method) {
 *         Long userId = (Long) pjp.getArgs()[0];
 *         return UserDTO.builder().userId(userId).username("mock_" + userId).build();
 *     }
 * }
 * </pre>
 *
 * @author Wenjie
 * @since 1.0.0
 */
@FunctionalInterface
public interface MockDataProvider {

    /**
     * 提供 Mock 数据。
     *
     * @param pjp    切入点，可通过 {@code pjp.getArgs()} 获取方法入参
     * @param method 被拦截的方法（含泛型、注解等元信息）
     * @return Mock 数据对象，类型需与方法返回值兼容
     */
    Object provide(ProceedingJoinPoint pjp, Method method);

    /**
     * 哨兵类，标识"未指定 provider"，框架内部识别此标记并跳过。
     * 不可实例化，不可被用户使用。
     */
    final class None implements MockDataProvider {
        private None() {
            throw new UnsupportedOperationException("MockDataProvider.None is a sentinel class");
        }

        @Override
        public Object provide(ProceedingJoinPoint pjp, Method method) {
            throw new UnsupportedOperationException();
        }
    }
}
