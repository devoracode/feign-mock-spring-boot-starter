package io.github.devoracode.feignmock.mock;

import java.util.Optional;

import io.github.devoracode.feignmock.autoconfigure.FeignMockProperties;

/**
 * {@link MockDataSource} backed by {@link FeignMockProperties#getResponses()}.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public class PropertiesMockDataSource implements MockDataSource {

	private static final int ORDER = 10;

	private final FeignMockProperties properties;

	public PropertiesMockDataSource(FeignMockProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<String> findByKey(String key) {
		return this.properties.getResponse(key);
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

}
