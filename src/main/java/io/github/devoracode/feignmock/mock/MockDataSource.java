package io.github.devoracode.feignmock.mock;

import java.util.Optional;

import org.springframework.core.Ordered;

/**
 * Strategy for locating mock response JSON by key.
 *
 * @author Wenjie Liu
 * @since 1.0.0
 */
public interface MockDataSource extends Ordered {

	/**
	 * Find mock response JSON for the given key.
	 * @param key the mock data key
	 * @return the JSON content, if present
	 */
	Optional<String> findByKey(String key);

}
