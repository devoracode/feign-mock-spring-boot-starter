package io.github.devoracode.feignmock.loader;

import java.util.Optional;

/**
 * Mock 数据源抽象接口。
 *
 * <p>实现类通过 {@link #getOrder()} 声明优先级，{@link MockDataLoader} 按升序依次查找，
 * 命中即返回，不再查找后续数据源。
 *
 * @author Wenjie
 * @since 1.0.0
 */
public interface MockDataSource {

    /**
     * 根据 key 查找原始 JSON 字符串。
     *
     * @param key mock 数据键，如 {@code "userClient.getUserById"}
     * @return 存在时返回 JSON 字符串，否则返回 {@link Optional#empty()}
     */
    Optional<String> findByKey(String key);

    /**
     * 数据源优先级，数字越小优先级越高。
     * <p>内置数据源参考值：
     * <ul>
     *   <li>{@link PropertiesMockDataSource}（配置文件 / Nacos）= 10</li>
     *   <li>{@link LocalFileMockDataSource}（classpath 本地文件）= 20</li>
     * </ul>
     * 自定义扩展建议从 30 开始，避免与内置数据源冲突。
     *
     * @return 优先级数值，越小越优先
     */
    int getOrder();
}
