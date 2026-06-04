package io.github.devoracode.feignmock.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Feign mock support.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = FeignMockProperties.PREFIX)
public class FeignMockProperties {

	public static final String PREFIX = "feign.mock";

	/**
	 * Whether Feign mock support is enabled.
	 */
	private boolean enabled = false;

	/**
	 * Mock response definitions keyed by mock data key.
	 */
	private Map<String, String> responses = new HashMap<>();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Map<String, String> getResponses() {
		return this.responses;
	}

	public void setResponses(Map<String, String> responses) {
		this.responses = responses;
	}

	public Optional<String> getResponse(String key) {
		return Optional.ofNullable(this.responses.get(key));
	}

}
