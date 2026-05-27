package io.github.feignmock.loader;

import io.github.feignmock.config.MockProperties;

import java.util.Optional;

/**
 * 基于配置文件（{@code feign.mock.responses}）的 Mock 数据源。
 *
 * <p>从 {@link MockProperties} 的 responses 映射中按 key 查找 JSON 字符串，
 * 优先级高于本地文件（order=10 &lt; {@link LocalFileMockDataSource#getOrder()}=20）。
 *
 * <p>配合 Nacos {@code @RefreshScope} 可实现运行时热更新，无需重启服务：
 * <pre>
 * feign:
 *   mock:
 *     enabled: true
 *     responses:
 *       userFeignClient.getUserById: '{"userId":1,"username":"nacos_user"}'
 * </pre>
 *
 * @author Wenjie
 * @since 1.0.0
 */
public class PropertiesMockDataSource implements MockDataSource {

    private final MockProperties mockProperties;

    public PropertiesMockDataSource(MockProperties mockProperties) {
        this.mockProperties = mockProperties;
    }

    @Override
    public Optional<String> findByKey(String key) {
        return mockProperties.getResponse(key);
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
