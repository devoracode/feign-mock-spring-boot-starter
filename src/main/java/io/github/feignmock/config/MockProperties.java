package io.github.feignmock.config;

import io.github.feignmock.annotation.MockMethod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * feign-mock-starter 配置属性绑定。
 *
 * <p>支持以下配置项（均以 {@code feign.mock} 为前缀）：
 *
 * <pre>
 * feign:
 *   mock:
 *     enabled: true                          # 全局开关，默认 false
 *     responses:                             # key-value Mock 数据，支持 Nacos 热更新
 *       userFeignClient.getUserById: '{"userId":1,"username":"mock"}'
 *       userFeignClient.listUsers: '[{"userId":1},{"userId":2}]'
 * </pre>
 *
 * <p>当使用 Nacos 时，建议在 Nacos 控制台的 {@code your-service-dev.yml} 中
 * 配置此节点，并开启 {@code refresh: true}，配合 {@code @RefreshScope} 实现热更新。
 *
 * @author Wenjie
 * @since 1.0.0
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "feign.mock")
public class MockProperties {

    /**
     * 全局开关。{@code true} 时激活 Mock，{@code false} 时所有拦截直接放行。
     * 默认 {@code false}，保证生产环境绝对安全。
     */
    private boolean enabled = false;

    /**
     * Mock 数据映射表。
     * <ul>
     *   <li>key：{@link MockMethod#value()} 指定的 key，
     *       或自动推导的 {@code interfaceSimpleName.methodName}（接口类名首字母小写）</li>
     *   <li>value：JSON 字符串</li>
     * </ul>
     */
    private Map<String, String> responses = new HashMap<>();

    /**
     * 查询指定 key 对应的 Mock JSON 数据。
     *
     * @param key 数据键
     * @return 存在时返回 JSON 字符串，否则 {@link Optional#empty()}
     */
    public Optional<String> getResponse(String key) {
        return Optional.ofNullable(responses.get(key));
    }
}
