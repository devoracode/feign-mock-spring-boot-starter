package io.github.feignmock.loader;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.feignmock.exception.MockDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Mock 数据统一加载器。
 *
 * <p>持有所有 {@link MockDataSource} 实现，按 {@link MockDataSource#getOrder()} 升序
 * 依次查找，命中即返回，不再继续。当前内置数据源及优先级：
 * <ul>
 *   <li>order=10：{@link PropertiesMockDataSource}（配置文件 / Nacos）</li>
 *   <li>order=20：{@link LocalFileMockDataSource}（classpath 本地文件）</li>
 * </ul>
 *
 * <p>业务代码直接使用 {@link #loadByKey} 或 {@link #loadByFile}，
 * 无需关心底层数据源细节。
 *
 * @author Wenjie
 * @since 1.0.0
 */
public class MockDataLoader {

    private static final Logger log = LoggerFactory.getLogger(MockDataLoader.class);

    private final ObjectMapper objectMapper;
    private final List<MockDataSource> dataSources;

    public MockDataLoader(ObjectMapper objectMapper, List<MockDataSource> dataSources) {
        this.objectMapper = objectMapper;
        // 按 order 升序排列，order 越小优先级越高
        dataSources.sort(Comparator.comparingInt(MockDataSource::getOrder));
        this.dataSources = dataSources;
    }

    /**
     * 按 key 在所有数据源中查找并反序列化为目标类型。
     *
     * @param key        数据键，如 {@code "userClient.getUserById"}
     * @param returnType 目标反序列化类型（支持泛型）
     * @param <T>        返回类型
     * @return 反序列化后的对象
     * @throws MockDataException key 在所有数据源均未找到时抛出
     */
    public <T> T loadByKey(String key, Type returnType) {
        String json = findJsonByKey(key).orElseThrow(() ->
            new MockDataException(buildNotFoundMessage(key)));
        return deserialize(json, returnType);
    }

    /**
     * 按文件路径加载并反序列化。
     * 路径相对 classpath 根目录，如 {@code "mock/user/getUser.json"}。
     * 委托给数据源链中的 {@link LocalFileMockDataSource} 处理。
     *
     * @param filePath   相对 classpath 的文件路径，如 {@code "mock/user/getUser.json"}
     * @param returnType 目标反序列化类型（支持泛型）
     * @param <T>        返回类型
     * @return 反序列化后的对象
     * @throws MockDataException 文件不存在或 {@link LocalFileMockDataSource} 未注册时抛出
     */
    public <T> T loadByFile(String filePath, Type returnType) {
        LocalFileMockDataSource localFileSource = dataSources.stream()
            .filter(s -> s instanceof LocalFileMockDataSource)
            .map(s -> (LocalFileMockDataSource) s)
            .findFirst()
            .orElseThrow(() -> new MockDataException("LocalFileMockDataSource not found, cannot load file: " + filePath));
        String json = localFileSource.loadByFilePath(filePath).orElseThrow(() ->
            new MockDataException("Mock file not found: classpath:" + filePath));
        return deserialize(json, returnType);
    }

    /**
     * 查找原始 JSON 字符串（不进行反序列化）。
     *
     * @param key 数据键
     * @return 找到时返回 JSON 字符串，否则 empty
     */
    public Optional<String> findJsonByKey(String key) {
        for (MockDataSource source : dataSources) {
            Optional<String> result = source.findByKey(key);
            if (result.isPresent()) {
                log.debug("[MockLoader] key={} matched data source: {}", key,
                    source.getClass().getSimpleName());
                return result;
            }
        }
        return Optional.empty();
    }

    private <T> T deserialize(String json, Type returnType) {
        try {
            JavaType javaType = objectMapper.constructType(returnType);
            return objectMapper.readValue(json, javaType);
        } catch (Exception e) {
            throw new MockDataException(
                "Failed to deserialize mock data, please check JSON format and return type. Cause: " + e.getMessage(), e);
        }
    }

    private String buildNotFoundMessage(String key) {
        return String.format(
            "Mock data not found, key=[%s]%n" +
            "  · config: feign.mock.responses.%s%n" +
            "  · local file: classpath:mock/%s.json",
            key, key, key.replace('.', '/'));
    }
}
