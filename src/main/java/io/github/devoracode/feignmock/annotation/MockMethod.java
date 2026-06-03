package io.github.devoracode.feignmock.annotation;

import io.github.devoracode.feignmock.provider.MockDataProvider;
import io.github.devoracode.feignmock.exception.MockDataException;

import java.lang.annotation.*;

/**
 * 标记 Feign 客户端方法使用 Mock 数据响应。
 *
 * <p>数据源优先级（由高到低）：
 * <ol>
 *   <li>{@link #provider()} — 自定义数据提供者，可访问入参、注入 Bean、抛业务异常</li>
 *   <li>{@link #jsonFile()} — 直接指定 classpath JSON 文件路径</li>
 *   <li>{@link #value()}   — key 查找：先查配置文件（{@code feign.mock.responses}），再查本地 {@code classpath:mock/} 文件</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>
 * // 方式一：key 自动推导（接口类名首字母小写.方法名）
 * &#64;MockMethod
 * UserDTO getUserById(&#64;PathVariable Long userId);
 *
 * // 方式二：指定 JSON 文件
 * &#64;MockMethod(jsonFile = "mock/user/list.json")
 * List&lt;UserDTO&gt; listUsers();
 *
 * // 方式三：自定义 Provider
 * &#64;MockMethod(provider = UserMockProvider.class)
 * UserDTO getUserById(&#64;PathVariable Long userId);
 * </pre>
 *
 * @author Wenjie
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MockMethod {

    /**
     * Mock 数据的 key，用于配置文件（{@code feign.mock.responses}）或本地文件自动查找。
     * <p>不填时自动推导为 {@code interfaceSimpleName.methodName}（接口类名首字母小写），
     * 例如 {@code userFeignClient.getUserById}。
     *
     * @return Mock 数据 key，默认空字符串表示自动推导
     */
    String value() default "";

    /**
     * 直接指定 JSON 文件路径（相对 classpath 根目录）。
     * <p>示例：{@code "mock/user-service/getUser.json"}
     * <p>优先级高于 {@link #value()}，低于 {@link #provider()}。
     *
     * @return JSON 文件路径，默认空字符串表示不使用此方式
     */
    String jsonFile() default "";

    /**
     * 自定义数据提供者 Class，优先级最高。
     * <p>实现类可声明为 Spring Bean（支持 {@code @Autowired}），
     * 也可提供无参构造由框架反射创建。
     *
     * @return Provider 实现类，默认 {@link MockDataProvider.None} 表示不使用此方式
     */
    Class<? extends MockDataProvider> provider() default MockDataProvider.None.class;

    /**
     * 未找到 Mock 数据时是否抛出异常。
     * <p>{@code true}（默认）抛出 {@link MockDataException}；
     * {@code false} 静默返回 {@code null}。
     *
     * @return {@code true} 表示快速失败，{@code false} 表示静默返回 null
     */
    boolean failFast() default true;

    /**
     * 描述信息，打印在日志中便于排查。
     *
     * @return 描述文本，默认空字符串
     */
    String description() default "";
}
