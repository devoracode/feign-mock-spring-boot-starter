package io.github.devoracode.feignmock.mock;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.devoracode.feignmock.exception.MockDataException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.Ordered;

/**
 * Loads and deserializes mock responses from registered {@link MockDataSource} instances.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public class MockDataLoader {

	private static final Logger logger = LoggerFactory.getLogger(MockDataLoader.class);

	private final ObjectMapper objectMapper;

	private final List<MockDataSource> dataSources;

	private final ResourceMockDataSource resourceSource;

	public MockDataLoader(ObjectMapper objectMapper, List<MockDataSource> dataSources) {
		this.objectMapper = objectMapper;
		List<MockDataSource> sorted = new ArrayList<>(dataSources);
		sorted.sort(Comparator.comparingInt(Ordered::getOrder));
		this.dataSources = sorted;
		this.resourceSource = sorted.stream()
				.filter(ResourceMockDataSource.class::isInstance)
				.map(ResourceMockDataSource.class::cast)
				.findFirst()
				.orElse(null);
	}

	public <T> T loadByKey(String key, Type returnType) {
		String json = findJsonByKey(key)
				.orElseThrow(() -> new MockDataException(buildNotFoundMessage(key)));
		return deserialize(json, returnType);
	}

	public <T> T loadByFile(String filePath, Type returnType) {
		if (this.resourceSource == null) {
			throw new MockDataException(
					"ResourceMockDataSource is not available, cannot load file: " + filePath);
		}
		String json = this.resourceSource.loadByFilePath(filePath)
				.orElseThrow(() -> new MockDataException("Mock file not found: classpath:" + filePath));
		return deserialize(json, returnType);
	}

	public Optional<String> findJsonByKey(String key) {
		for (MockDataSource source : this.dataSources) {
			Optional<String> result = source.findByKey(key);
			if (result.isPresent()) {
				logger.debug("Mock key '{}' resolved by {}", key, source.getClass().getSimpleName());
				return result;
			}
		}
		return Optional.empty();
	}

	private <T> T deserialize(String json, Type returnType) {
		try {
			JavaType javaType = this.objectMapper.constructType(returnType);
			return this.objectMapper.readValue(json, javaType);
		}
		catch (Exception ex) {
			throw new MockDataException(
					"Failed to deserialize mock data, please check JSON format and return type. Cause: "
							+ ex.getMessage(),
					ex);
		}
	}

	private String buildNotFoundMessage(String key) {
		return String.format(
				"Mock data not found, key=[%s]%n  config: feign.mock.responses.%s%n  resource: classpath:mock/%s.json",
				key, key, key.replace('.', '/'));
	}

}
