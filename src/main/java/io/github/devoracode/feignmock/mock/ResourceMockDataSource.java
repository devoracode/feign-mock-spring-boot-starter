package io.github.devoracode.feignmock.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

/**
 * {@link MockDataSource} that loads mock responses from classpath resources.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public class ResourceMockDataSource implements MockDataSource {

	private static final Logger logger = LoggerFactory.getLogger(ResourceMockDataSource.class);

	private static final int ORDER = 20;

	private static final String MOCK_CLASSPATH_PREFIX = "classpath:mock/";

	private final ResourceLoader resourceLoader;

	private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

	public ResourceMockDataSource(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public Optional<String> findByKey(String key) {
		String path = MOCK_CLASSPATH_PREFIX + key.replace('.', '/') + ".json";
		return loadFromPath(path);
	}

	/**
	 * Load mock response content from the given file path.
	 * @param filePath a classpath-relative path or a {@code classpath:} URL
	 * @return the JSON content, if present
	 */
	public Optional<String> loadByFilePath(String filePath) {
		String fullPath = filePath.startsWith("classpath:") ? filePath : "classpath:" + filePath;
		return loadFromPath(fullPath);
	}

	private Optional<String> loadFromPath(String path) {
		Optional<String> cached = this.cache.get(path);
		if (cached != null) {
			return cached;
		}
		try {
			Resource resource = this.resourceLoader.getResource(path);
			if (!resource.exists()) {
				logger.debug("Mock resource not found: {}", path);
				return Optional.empty();
			}
			String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
			logger.debug("Loaded mock resource: {}", path);
			Optional<String> result = Optional.of(content);
			this.cache.put(path, result);
			return result;
		}
		catch (IOException ex) {
			logger.warn("Failed to read mock resource {}: {}", path, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Clear the in-memory resource cache.
	 */
	public void clearCache() {
		this.cache.clear();
		logger.info("Cleared Feign mock resource cache");
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

}
