package io.github.feignmock.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.feignmock.aspect.MockMethodAspect;
import io.github.feignmock.el.MockElEvaluator;
import io.github.feignmock.loader.LocalFileMockDataSource;
import io.github.feignmock.loader.MockDataLoader;
import io.github.feignmock.loader.MockDataSource;
import io.github.feignmock.loader.PropertiesMockDataSource;
import io.github.feignmock.provider.MockProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * feign-mock-starter 自动配置类。
 *
 * <p>仅在 {@code feign.mock.enabled=true} 时激活，确保生产环境零开销、零侵入。
 *
 * <p><b>Bean 注册顺序：</b>
 * <ol>
 *   <li>{@link MockProperties}（配置绑定）</li>
 *   <li>{@link LocalFileMockDataSource}（本地文件数据源，order=20）</li>
 *   <li>{@link PropertiesMockDataSource}（配置文件数据源，order=10，优先于本地文件）</li>
 *   <li>{@link MockDataLoader}（统一加载器）</li>
 *   <li>{@link MockProviderRegistry}（Provider 注册中心）</li>
 *   <li>{@link MockMethodAspect}（AOP 切面）</li>
 * </ol>
 *
 * @author Wenjie
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(MockProperties.class)
@ConditionalOnProperty(prefix = "feign.mock", name = "enabled", havingValue = "true")
public class MockAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MockAutoConfiguration.class);

    // ── ObjectMapper ──────────────────────────────────────────────────────────

    /**
     * 提供专用的 {@link ObjectMapper}，禁用未知属性报错和日期时间戳序列化，并注册 Java 8 时间模块。
     * <p>使用 {@code @ConditionalOnMissingBean} 确保用户自定义 ObjectMapper 优先。
     *
     * @return 配置好的 ObjectMapper 实例
     */
    @Bean("mockObjectMapper")
    @ConditionalOnMissingBean(name = "mockObjectMapper")
    public ObjectMapper mockObjectMapper() {
        return new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(new JavaTimeModule());
    }

    // ── 数据源 ─────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(LocalFileMockDataSource.class)
    public LocalFileMockDataSource localFileMockDataSource(ResourceLoader resourceLoader) {
        return new LocalFileMockDataSource(resourceLoader);
    }

    /**
     * 基于 {@code feign.mock.responses} 配置的数据源（order=10，优先于本地文件）。
     * <p>配合 Nacos {@code @RefreshScope} 可实现运行时热更新，无需重启服务。
     *
     * @param mockProperties 配置属性，持有 responses 映射表
     * @return {@link PropertiesMockDataSource} 实例
     */
    @Bean
    @ConditionalOnMissingBean(PropertiesMockDataSource.class)
    public PropertiesMockDataSource propertiesMockDataSource(MockProperties mockProperties) {
        return new PropertiesMockDataSource(mockProperties);
    }

    // ── 核心组件 ───────────────────────────────────────────────────────────────

    /**
     * 统一数据加载器。
     * <p>通过 {@link ObjectProvider} 收集所有 {@link MockDataSource} 实现，支持用户扩展。
     *
     * @param mockObjectMapper     专用 ObjectMapper，用于 JSON 反序列化
     * @param dataSourceProvider   所有 {@link MockDataSource} Bean 的提供者
     * @return {@link MockDataLoader} 实例，持有按优先级排序的数据源列表
     */
    @Bean
    @ConditionalOnMissingBean(MockDataLoader.class)
    public MockDataLoader mockDataLoader(ObjectMapper mockObjectMapper,
                                         ObjectProvider<MockDataSource> dataSourceProvider) {
        List<MockDataSource> sources = new ArrayList<>();
        dataSourceProvider.forEach(sources::add);
        log.info("[FeignMock] Registered {} data source(s)", sources.size());
        return new MockDataLoader(mockObjectMapper, sources);
    }

    @Bean
    @ConditionalOnMissingBean(MockProviderRegistry.class)
    public MockProviderRegistry mockProviderRegistry() {
        return new MockProviderRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(MockElEvaluator.class)
    public MockElEvaluator mockElEvaluator() {
        return new MockElEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean(MockMethodAspect.class)
    public MockMethodAspect mockMethodAspect(MockDataLoader mockDataLoader,
                                              MockProviderRegistry mockProviderRegistry,
                                              MockElEvaluator mockElEvaluator,
                                              MockProperties mockProperties) {
        log.info("[FeignMock] Mock interceptor started, global switch=enabled");
        return new MockMethodAspect(mockDataLoader, mockProviderRegistry, mockElEvaluator, mockProperties);
    }
}
