package io.github.devoracode.feignmock.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于本地 classpath 文件的 Mock 数据源。
 *
 * <p>文件查找规则：将 key 中的 {@code .} 替换为 {@code /}，
 * 加上 {@code .json} 后缀，从 {@code classpath:mock/} 目录查找。
 * <pre>
 *   key: "userFeignClient.getUserById"
 *   路径: classpath:mock/userFeignClient/getUserById.json
 * </pre>
 *
 * <p>成功加载的文件内容会被缓存在内存中（{@link ConcurrentHashMap}），
 * 避免每次请求重复读取磁盘 IO。文件不存在或读取异常时不写入缓存，下次仍可重试。
 *
 * @author Wenjie
 * @since 1.0.0
 */
public class LocalFileMockDataSource implements MockDataSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFileMockDataSource.class);
    private static final String MOCK_CLASSPATH_PREFIX = "classpath:mock/";

    private final ResourceLoader resourceLoader;

    /** 成功加载的文件内容缓存：path → JSON 字符串 */
    private final Map<String, Optional<String>> fileCache = new ConcurrentHashMap<>();

    public LocalFileMockDataSource(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Optional<String> findByKey(String key) {
        // "userClient.getUserById" → "classpath:mock/userClient/getUserById.json"
        String path = MOCK_CLASSPATH_PREFIX + key.replace('.', '/') + ".json";
        return loadFromPath(path);
    }

    /**
     * 按完整路径加载文件内容（供 {@code @MockMethod(jsonFile=...)} 使用）。
     *
     * @param filePath 相对 classpath 的路径，如 {@code "mock/user/list.json"}，
     *                 也接受带 {@code classpath:} 前缀的全路径
     * @return 文件内容 JSON 字符串，文件不存在时返回 {@link Optional#empty()}
     */
    public Optional<String> loadByFilePath(String filePath) {
        String fullPath = filePath.startsWith("classpath:")
            ? filePath
            : "classpath:" + filePath;
        return loadFromPath(fullPath);
    }

    private Optional<String> loadFromPath(String path) {
        // 只缓存成功读取的结果；文件不存在或 IO 异常时不写入缓存，下次仍可重试
        Optional<String> cached = fileCache.get(path);
        if (cached != null) {
            return cached;
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.debug("[MockFile] File not found: {}", path);
                return Optional.empty();
            }
            String content = StreamUtils.copyToString(
                resource.getInputStream(), StandardCharsets.UTF_8);
            log.debug("[MockFile] Loaded successfully: {}", path);
            Optional<String> result = Optional.of(content);
            fileCache.put(path, result);
            return result;
        } catch (IOException e) {
            log.warn("[MockFile] Failed to read file: {} - {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 清除文件缓存（测试或动态刷新时使用）。
     */
    public void clearCache() {
        fileCache.clear();
        log.info("[MockFile] File cache cleared");
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
